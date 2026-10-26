package liveness.flow;

import liveness.android.BlurWindowGate;
import liveness.android.FaceEmbedder;
import liveness.audit.AuditChain;
import liveness.audit.AuditEvent;
import liveness.audit.AuditLog;
import liveness.audit.AuditSeal;
import liveness.capture.CaptureIntegrityChecker;
import liveness.capture.FrameFingerprint;
import liveness.challenge.BoundChallenge;
import liveness.challenge.ChallengeGenerator;
import liveness.challenge.ChallengeResponseValidator;
import liveness.core.FaceFrame;
import liveness.crypto.Attestation;
import liveness.crypto.Signer;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;
import liveness.engine.DecisionEngine;
import liveness.engine.LandmarkDetector;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.QualityGate;
import liveness.engine.SameFaceChecker;
import liveness.engine.SpatialPadScorer;
import liveness.policy.LivenessConfig;
import liveness.policy.LivenessPolicy;
import liveness.policy.StubPolicy;
import liveness.util.Sha256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Session orchestrator for face liveness detection (Section 6).
 * <p>
 * Drives the {@link LivenessStateMachine}. <strong>No boolean flags.</strong>
 * All routing decisions derive from {@code machine.current()}.
 * <p>
 * Attestation is produced ONLY from {@code VERIFYING_FINAL -> SUCCESS}.
 * Liveness-failed captures are NEVER saved.
 */
public final class LivenessSession {

    // --- Identity ---
    private final String subjectId;
    private final String sessionId;

    // --- Components (Section 6 fields) ---
    private final DeviceAdapter device;
    private final LivenessPolicy policy;
    private final LivenessConfig config;
    private final SpatialPadScorer spatialPad;
    private final DecisionEngine decisionEngine;
    private final QualityGate qualityGate;
    private final ChallengeGenerator challengeGen;
    private final ChallengeResponseValidator challengeValidator;
    private final AuditLog audit;
    private final RetryRateLimiter retryLimiter;
    private final SupervisorAuth supervisor;  // nullable; null disables exemptions
    private final CaptureIntegrityChecker captureIntegrity;
    private final LivenessStateMachine machine;
    private final MiniFasNetScorer padScorer;
    private final SameFaceChecker sameFaceChecker;
    private final Signer signer;

    // --- Session state (not boolean flags -- data accumulators) ---
    private final String machineUserId;
    private final List<BoundChallenge> issuedChallenges;
    private final List<Attestation.ChallengeEcho> challengeEchoes;
    private final List<float[]> livenessEmbeddings;
    private int challengeCount;
    /** Challenges actually answered; unlike challengeCount (issued) it is not advanced by a timeout. */
    private int confirmedChallenges;
    /** Set when a LOOP_REPLAY signal was seen and loopReplayHardFail is off: bona fide must then pass an active challenge. */
    private boolean loopSuspected;
    private int retryCount;
    private BoundChallenge currentChallenge;
    /** Candidate value, not tuned: yaw limit for the quality gate while a TURN_* challenge is active. */
    static final double TURN_YAW_LIMIT_DEG = 85.0;
    private Attestation attestation;
    private FaceEmbedder faceEmbedder;      // optional; without it same-face fails closed (unless test-stubbed)
    private BlurWindowGate relativeBlurGate;   // null when relativeBlurEnabled is false; one per session, not thread-safe
    private FaceFrame lastHostFrame;        // last quality-passing host frame carrying pixels

    /**
     * Creates a new liveness session.
     *
     * @param subjectId        the subject being verified
     * @param device           the frame source adapter
     * @param config           liveness configuration
     * @param padScorer        PAD model scorer (may be in stub mode)
     * @param sameFaceChecker  same-face verification
     * @param challengeGen     challenge generator
     * @param retryLimiter     retry rate limiter (persisted)
     * @param captureIntegrity capture integrity checker
     * @param signer           cryptographic signer for attestation
     * @param supervisor       supervisor auth (nullable)
     * @param machineUserId    composite key for rate limiting
     */
    private String captureMode = "STREAM";

    /** Recorded in the attestation; defaults to "STREAM" (MDS). Android sets host-camera + rCapture. */
    public void setCaptureMode(String mode) { if (mode != null && !mode.isEmpty()) this.captureMode = mode; }

    public LivenessSession(String subjectId,
                           DeviceAdapter device,
                           LivenessConfig config,
                           MiniFasNetScorer padScorer,
                           SameFaceChecker sameFaceChecker,
                           ChallengeGenerator challengeGen,
                           RetryRateLimiter retryLimiter,
                           CaptureIntegrityChecker captureIntegrity,
                           Signer signer,
                           SupervisorAuth supervisor,
                           String machineUserId) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.sessionId = UUID.randomUUID().toString();
        this.device = Objects.requireNonNull(device, "device");
        this.config = Objects.requireNonNull(config, "config");
        this.policy = new LivenessPolicy(config);
        this.spatialPad = new SpatialPadScorer(config.minQualifyingFrames());
        this.decisionEngine = new DecisionEngine(policy, config.attackPersistenceWindows());
        this.qualityGate = new QualityGate(
                config.blurMinVariance(),
                config.brightnessMin(),
                config.brightnessMax(),
                config.faceSizeMinPx(),
                config.poseMaxYawDeg(),
                config.poseMaxPitchDeg());
        this.padScorer = Objects.requireNonNull(padScorer, "padScorer");
        this.sameFaceChecker = Objects.requireNonNull(sameFaceChecker, "sameFaceChecker");
        this.challengeGen = Objects.requireNonNull(challengeGen, "challengeGen");
        this.challengeValidator = new ChallengeResponseValidator();
        this.audit = new AuditLog(new AuditChain(), sessionId);
        this.retryLimiter = Objects.requireNonNull(retryLimiter, "retryLimiter");
        this.supervisor = supervisor;  // nullable
        this.captureIntegrity = Objects.requireNonNull(captureIntegrity, "captureIntegrity");
        this.machine = new LivenessStateMachine();
        this.signer = Objects.requireNonNull(signer, "signer");
        this.machineUserId = Objects.requireNonNull(machineUserId, "machineUserId");
        this.relativeBlurGate = config.relativeBlurEnabled()
                ? new BlurWindowGate(config.relativeBlurWindow(), config.relativeBlurDropFactor(),
                        config.relativeBlurYShiftFactor())
                : null;
        StubPolicy.check(padScorer.isStubMode(), "PAD scorer");
        StubPolicy.check(sameFaceChecker.isStubbed(), "SameFaceChecker");

        this.issuedChallenges = new ArrayList<>();
        this.challengeEchoes = new ArrayList<>();
        this.livenessEmbeddings = new ArrayList<>();
        this.challengeCount = 0;
        this.confirmedChallenges = 0;
        this.retryCount = 0;
        this.currentChallenge = null;
        this.attestation = null;
    }

    // =========================================================================
    // Public API -- all routing from machine.current(), no boolean flags
    // =========================================================================

    /**
     * Supplies the face embedder used for the host-stream vs rCapture same-face check (S6.4).
     * Without one, {@link #processRCapture(FaceFrame, String, long, long)} cannot compare faces and fails closed.
     */
    public void setFaceEmbedder(FaceEmbedder embedder) { this.faceEmbedder = embedder; }

    /** Starts the session: IDLE -> DEVICE_CHECK. */
    public LivenessState start() {
        LivenessState next = machine.fire(LivenessEvent.START_CAPTURE);
        audit.logTransition(LivenessState.IDLE, next, "START_CAPTURE");
        return next;
    }

    /**
     * Opens the device: DEVICE_CHECK -> POSITIONING (or DEVICE_ERROR).
     */
    public LivenessState openDevice() {
        try {
            device.open();
            LivenessState next = machine.fire(LivenessEvent.DEVICE_READY);
            audit.logTransition(LivenessState.DEVICE_CHECK, next, "DEVICE_READY");
            return next;
        } catch (DeviceException e) {
            LivenessState next = machine.fire(LivenessEvent.DEVICE_UNAVAILABLE);
            audit.logTransition(LivenessState.DEVICE_CHECK, next,
                    "DEVICE_UNAVAILABLE: " + e.getMessage());
            return next;
        }
    }

    /**
     * Processes a single frame through the pipeline.
     * <p>
     * Pipeline order per Section 3:
     * 1. Capture Integrity (IAD)
     * 2. Quality Gate (raw frame, before crop -- R3)
     * 3. Spatial PAD (per-frame)
     * 4. Decision Engine (hard gates + fusion)
     *
     * @param frame the captured face frame
     * @return the resulting state
     */
    public LivenessState processFrame(FaceFrame frame) {
        final long tProcess = liveness.android.StageTimer.start();
        try {
            return processFrameImpl(frame);
        } finally {
            liveness.android.StageTimer.stop(liveness.android.StageTimer.Stage.PROCESS_TOTAL, tProcess);
        }
    }

    private LivenessState processFrameImpl(FaceFrame frame) {
        LivenessState current = machine.current();

        // --- 1. Capture Integrity Check ---
        final long tIntegrity = liveness.android.StageTimer.start();
        FrameFingerprint fp = new FrameFingerprint(
                frame.pHash(),  // computed by the DeviceAdapter from raw pixels (0 if not available)
                frame.timestampMs(),
                frame.sequenceNum(),
                frame.widthPx(),
                frame.contentDigest());


        Optional<String> integrityViolation = captureIntegrity.checkFrame(
                fp,
                String.valueOf(frame.deviceId()),
                frame.signature());
        liveness.android.StageTimer.stop(liveness.android.StageTimer.Stage.INTEGRITY, tIntegrity);

        if (integrityViolation.isPresent()
                && "LOOP_REPLAY".equals(integrityViolation.get())
                && !config.loopReplayHardFail()) {
            // Soft policy: loop detection is unvalidated on real cameras (Part 2 X13), so it is a
            // signal, not a verdict. Log it and require a completed active challenge to pass.
            audit.logScore("INTEGRITY_SOFT", 0, "LOOP_REPLAY suspected: active challenge required");
            loopSuspected = true;
            integrityViolation = Optional.empty();
        }

        if (integrityViolation.isPresent()) {
            audit.logScore("INTEGRITY", 0, integrityViolation.get());
            LivenessState next = machine.fire(LivenessEvent.IAD_FAILURE);
            audit.logTransition(current, next, "IAD_FAILURE: " + integrityViolation.get());
            retryLimiter.recordDeviceError(sessionId, machineUserId, RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
            return next;
        }

        // --- 2. Quality Gate (raw frame, before crop -- R3, Section 6.2) ---
        QualityGate.RawFrameData rawData = new QualityGate.RawFrameData(
                frame.laplacianVariance(),
                frame.brightnessMean(),
                frame.faceBboxWidth(),
                frame.faceBboxHeight(),
                frame.yawDeg(),
                frame.pitchDeg(),
                frame.rollDeg(),
                frame.faceCount(),
                frame.widthPx(),
                frame.heightPx(),
                frame.faceBboxX(),
                frame.faceBboxY());

        final long tQuality = liveness.android.StageTimer.start();
        final boolean challengeActive = current == LivenessState.CHALLENGE_WAIT && currentChallenge != null;
        QualityGate.QualityResult qr = challengeActive
                ? qualityGate.evaluate(rawData, Math.max(config.poseMaxYawDeg(), TURN_YAW_LIMIT_DEG))
                : qualityGate.evaluate(rawData);
        liveness.android.StageTimer.stop(liveness.android.StageTimer.Stage.QUALITY, tQuality);

        if (!qr.passed()) {
            // Quality failure -> POSITIONING (guidance, not attack)
            if (current == LivenessState.PASSIVE) {
                LivenessState next = machine.fire(LivenessEvent.QUALITY_DROPPED);
                audit.logTransition(current, next, "QUALITY_DROPPED: " + qr.failureReason());
                return next;
            }
            if (current == LivenessState.CHALLENGE_WAIT && currentChallenge != null && frame.faceCount() == 1) {
                // In challenge wait, subject is actively moving. Do not discard frames with a valid
                // single face due to normal motion blur, box margin shift, or head angle during turn.
            } else {
                // Already in POSITIONING or other non-challenge state -- stay there
                return current;
            }
        }

        // --- 2b. Relative blur gate (additive, default off). Sessions with no valid full-frame sample skip it. ---
        if (relativeBlurGate != null) {
            BlurWindowGate.State bs = relativeBlurGate.update(frame.fullFrameNorm(), frame.brightnessMean());
            if (relativeBlurGate.hasSamples() && bs != BlurWindowGate.State.OK) {
                String why = bs == BlurWindowGate.State.LOST ? "guide.quality.light_unsteady"
                        : bs == BlurWindowGate.State.BLUR ? "guide.quality.blur_relative"
                        : "relative_blur_warmup";
                if (current == LivenessState.PASSIVE) {
                    LivenessState next = machine.fire(LivenessEvent.QUALITY_DROPPED);
                    audit.logTransition(current, next, "QUALITY_DROPPED: " + why);
                    return next;
                }
                return current;
            }
        }

        if (frame.hasPixelData()) {
            lastHostFrame = frame;
        }

        // If in POSITIONING and quality is OK, transition to PASSIVE
        if (current == LivenessState.POSITIONING) {
            LivenessState next = machine.fire(LivenessEvent.QUALITY_OK);
            audit.logTransition(current, next, "QUALITY_OK");
            current = next;
        }

        // --- 3. Spatial PAD scoring ---
        if (current == LivenessState.PASSIVE || current == LivenessState.CHALLENGE_WAIT) {
            int padScore = scorePad(frame);
            spatialPad.addFrame(padScore, qr.qualityWeight());
            audit.logScore("PAD", padScore, "weight=" + qr.qualityWeight());

            // During challenge wait, also check action detectors
            if (current == LivenessState.CHALLENGE_WAIT && currentChallenge != null) {
                return processFrameDuringChallenge(frame, padScore);
            }

            // --- 4. Decision Engine ---
            Optional<Integer> composite = spatialPad.composite();
            if (composite.isPresent()) {
                int score = composite.get();
                DecisionEngine.DecisionResult result = decisionEngine.evaluate(score);
                audit.logBand(result.band().name(), score, result.reason());

                LivenessPolicy.Band band = result.band();
                switch (band) {
                    case CLEAR_BONA_FIDE:
                        // Route to active challenge when required challenges are not yet confirmed.
                        // Covers: (a) loop suspected with no challenge issued,
                        //         (b) forceChallenge mode,
                        //         (c) production: timeout->retry leaves confirmedChallenges < min
                        //             so a passive-only retry cannot bypass the challenge requirement.
                        boolean needsChallenge = (loopSuspected && challengeCount == 0)
                                || (config.activeEnabled() && confirmedChallenges < config.minChallenges());
                        if (needsChallenge) {
                            if (config.activeEnabled()) {
                                audit.logScore("CHALLENGE_REQUIRED", score,
                                        "Routing to active challenge: confirmed=" + confirmedChallenges
                                                + " min=" + config.minChallenges()
                                                + " loop=" + loopSuspected
                                                + " force=" + config.forceChallenge());
                                return fireBandEvent(current, LivenessEvent.UNCERTAIN);
                            }
                            audit.logScore("PASSIVE_ONLY_REJECT", score, "Active disabled: challenges required but unavailable");
                            return fireBandEvent(current, LivenessEvent.ENGINE_ERROR);
                        }
                        return fireBandEvent(current, LivenessEvent.CLEAR_BONA_FIDE);
                    case UNCERTAIN:
                        if (result.reason() != null && result.reason().startsWith("Attack pending persistence")) {
                            // Still accumulating windows for attack persistence -- continue accumulating
                            return current;
                        }
                        if (config.activeEnabled()) {
                            return fireBandEvent(current, LivenessEvent.UNCERTAIN);
                        }
                        // Active disabled -- UNCERTAIN must never pass without a completed challenge.
                        audit.logScore("PASSIVE_UNCERTAIN_REJECT", score, "Active disabled: UNCERTAIN cannot pass");
                        return fireBandEvent(current, LivenessEvent.ENGINE_ERROR);
                    case CLEAR_ATTACK:
                        if (decisionEngine.isAttackPersisted()) {
                            retryLimiter.recordBlockedAttack(sessionId, machineUserId);
                            return fireBandEvent(current, LivenessEvent.CLEAR_ATTACK);
                        }
                        // Not yet persisted -- continue accumulating
                        return current;
                    default:
                        return current;
                }
            }
        }

        return current;
    }

    /**
     * Issues a challenge prompt: CHALLENGE_PROMPT -> CHALLENGE_WAIT.
     */
    public LivenessState issueChallenge() {
        actionHitFrames = 0;
        currentChallenge = challengeGen.next(
                sessionId, challengeCount, config.challengeTimeoutMs());
        issuedChallenges.add(currentChallenge);
        challengeCount++;

        audit.logChallengeIssued(currentChallenge.action(), challengeCount);

        LivenessState next = machine.fire(LivenessEvent.PROMPT_ISSUED);
        audit.logTransition(LivenessState.CHALLENGE_PROMPT, next, "PROMPT_ISSUED");
        return next;
    }

    /**
     * Reports a challenge timeout: CHALLENGE_WAIT -> RETRY_AVAILABLE.
     */
    public LivenessState challengeTimeout() {
        audit.logChallengeResult(currentChallenge != null ? currentChallenge.action() : "UNKNOWN",
                false, "TIMEOUT");
        LivenessState next = machine.fire(LivenessEvent.CHALLENGE_TIMEOUT);
        audit.logTransition(LivenessState.CHALLENGE_WAIT, next, "CHALLENGE_TIMEOUT");
        currentChallenge = null;
        return next;
    }

    /**
     * Processes RCAPTURE result: FINAL_CAPTURE -> VERIFYING_FINAL.
     *
     * @param rcaptureImageBytes the captured image bytes
     * @param returnedTransactionId the transaction ID from the device
     * @param rcaptureTimestamp timestamp from RCAPTURE response
     * @param requestTimestamp when the capture was requested
     */
    public LivenessState processRCapture(byte[] rcaptureImageBytes,
                                         String returnedTransactionId,
                                         long rcaptureTimestamp,
                                         long requestTimestamp) {
        return finishRCapture(rcaptureImageBytes, null, returnedTransactionId,
                rcaptureTimestamp, requestTimestamp);
    }

    /**
     * Preferred overload: the rCapture frame carries RGB pixels + dimensions, so the final PAD score and the
     * same-face check run on real image data instead of empty placeholders.
     */
    public LivenessState processRCapture(FaceFrame rcaptureFrame,
                                         String returnedTransactionId,
                                         long rcaptureTimestamp,
                                         long requestTimestamp) {
        Objects.requireNonNull(rcaptureFrame, "rcaptureFrame");
        byte[] imageBytes = rcaptureFrame.hasEncodedBytes() ? rcaptureFrame.encodedBytes() : rcaptureFrame.pixelData();
        return finishRCapture(imageBytes, rcaptureFrame, returnedTransactionId,
                rcaptureTimestamp, requestTimestamp);
    }

    private LivenessState finishRCapture(byte[] rcaptureImageBytes,
                                         FaceFrame rcaptureFrame,
                                         String returnedTransactionId,
                                         long rcaptureTimestamp,
                                         long requestTimestamp) {
        // Row 16: FINAL_CAPTURE -> VERIFYING_FINAL
        LivenessState next = machine.fire(LivenessEvent.RCAPTURE_RETURNED);
        audit.logTransition(LivenessState.FINAL_CAPTURE, next, "RCAPTURE_RETURNED");

        // Verify binding: transaction ID + freshness
        boolean bindingOk = captureIntegrity.verifyRCapture(
                returnedTransactionId, rcaptureTimestamp, requestTimestamp);

        if (!bindingOk) {
            // Row 21: VERIFYING_FINAL -> DEVICE_ERROR (INTEGRITY_FAILURE)
            next = machine.fire(LivenessEvent.BINDING_FAILURE);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "BINDING_FAILURE");
            retryLimiter.recordDeviceError(sessionId, machineUserId, RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
            return next;
        }

        // Same-face check (Section 6.4)
        boolean sameFaceOk = checkSameFace(rcaptureFrame);
        if (!sameFaceOk) {
            // Row 19: VERIFYING_FINAL -> RETRY_AVAILABLE
            next = machine.fire(LivenessEvent.SAME_FACE_FAIL);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "SAME_FACE_FAIL");
            return next;
        }

        // Final PAD check on RCAPTURE image
        int finalPadScore = scorePad(rcaptureFrame);
        LivenessPolicy.Band finalBand = policy.route(finalPadScore);
        audit.logScore("FINAL_PAD", finalPadScore, finalBand.name());

        if (finalBand == LivenessPolicy.Band.CLEAR_ATTACK) {
            // Row 20: VERIFYING_FINAL -> BLOCKED_ATTACK
            next = machine.fire(LivenessEvent.FINAL_PAD_ATTACK);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "FINAL_PAD_ATTACK");
            retryLimiter.recordBlockedAttack(sessionId, machineUserId);
            return next;
        }

        if (finalBand != LivenessPolicy.Band.CLEAR_BONA_FIDE) {
            // Row 19 (final quality/PAD fail): only a CLEAR_BONA_FIDE final image may commit.
            next = machine.fire(LivenessEvent.SAME_FACE_FAIL);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next,
                    "FINAL_PAD_NOT_CLEAR_BONA_FIDE: band=" + finalBand.name());
            return next;
        }

        // All checks pass -- commit
        // Row 18: VERIFYING_FINAL -> SUCCESS (only path that produces attestation)
        String imageSha256 = Sha256.hex(Sha256.digest(rcaptureImageBytes)); // SHA-256 of the image (hex() alone only encodes)
        // The commit entry is logged BEFORE the transcript hash is taken so the signed hash covers it.
        audit.logTransition(LivenessState.VERIFYING_FINAL, LivenessState.SUCCESS, "COMMIT_CHECKS_PASS");
        String transcriptSha256 = audit.chain().latestHash();

        List<String> mechanisms = new ArrayList<>();
        mechanisms.add("MiniFASNet");
        if (sameFaceChecker != null) {
            mechanisms.add("SameFace");
        }
        if (!challengeEchoes.isEmpty()) {
            mechanisms.add("ActiveChallenge");
        }

        String attackDecision = finalPadScore >= config.passThreshold() ? "NO_ATTACK" : "UNCERTAIN_PASSED";
        String deviceTier = (device != null) ? device.deviceTier() : "T1";

        Attestation.PadResult padResult = new Attestation.PadResult(
                attackDecision,
                100 - finalPadScore,  // padDecisionScore = 100 - livenessScore (Section 6.5)
                mechanisms,
                Collections.unmodifiableList(challengeEchoes));

        Attestation built = Attestation.createFromSuccessSession(
                subjectId, sessionId,
                imageSha256, transcriptSha256,
                transcriptSha256,  // auditHeadHash = real chain head (R4)
                deviceTier, rcaptureTimestamp,
                padScorer.modelVersion(),
                config.configVersion(),
                captureMode,
                padResult,
                audit.chain().size(),
                signer);

        // Only after the machine accepts the transition is the attestation published on the session.
        next = machine.fire(LivenessEvent.COMMIT_CHECKS_PASS);
        attestation = built;
        audit.seal();

        return next;
    }

    /**
     * Reports RCAPTURE failure: FINAL_CAPTURE -> DEVICE_ERROR.
     */
    public LivenessState rcaptureFailure() {
        LivenessState next = machine.fire(LivenessEvent.RCAPTURE_FAILURE);
        audit.logTransition(LivenessState.FINAL_CAPTURE, next, "RCAPTURE_FAILURE");
        return next;
    }

    /**
     * Attempts a retry: RETRY_AVAILABLE -> PASSIVE or FAILED_MAX_RETRIES.
     */
    public LivenessState attemptRetry() {
        boolean allowed = retryLimiter.tryRetry(sessionId, machineUserId);
        if (allowed) {
            retryCount++;
            spatialPad.reset();
            decisionEngine.resetPersistence();
            LivenessState next = machine.fire(LivenessEvent.RETRY_GRANTED);
            audit.logTransition(LivenessState.RETRY_AVAILABLE, next,
                    "RETRY_GRANTED (" + retryCount + "/" + config.maxRetries() + ")");
            return next;
        } else {
            LivenessState next = machine.fire(LivenessEvent.BUDGET_EXHAUSTED);
            audit.logTransition(LivenessState.RETRY_AVAILABLE, next, "BUDGET_EXHAUSTED");
            return next;
        }
    }

    /**
     * Reports a device disconnect from any live non-terminal state.
     */
    public LivenessState deviceDisconnect() {
        LivenessState current = machine.current();
        LivenessState next = machine.fire(LivenessEvent.DEVICE_DISCONNECT);
        audit.logTransition(current, next, "DEVICE_DISCONNECT");
        return next;
    }

    /**
     * Reports a positioning timeout: POSITIONING -> RETRY_AVAILABLE.
     */
    public LivenessState positioningTimeout() {
        LivenessState next = machine.fire(LivenessEvent.POSITIONING_TIMEOUT);
        audit.logTransition(LivenessState.POSITIONING, next, "POSITIONING_TIMEOUT");
        return next;
    }

    // =========================================================================
    // Getters -- session state
    // =========================================================================

    public String subjectId()            { return subjectId; }
    public String sessionId()            { return sessionId; }
    public LivenessState currentState()  { return machine.current(); }
    public Attestation attestation()     { return attestation; }
    public AuditLog audit()              { return audit; }
    public int challengeCount()          { return challengeCount; }
    public int retryCount()              { return retryCount; }
    public BoundChallenge currentChallenge() { return currentChallenge; }

    /**
     * Seals the audit chain and signs its head. Valid for ANY outcome (success, blocked, failed, device error),
     * so non-attested sessions also leave an anchored trail (ADR-010).
     */
    public AuditSeal.SignedHead signedAuditHead() {
        audit.seal();
        return AuditSeal.sign(audit.chain(), sessionId, signer);
    }

    /**
     * Exports the sealed audit trail for this session.
     */
    public String exportAuditTrail() {
        if (!audit.chain().isSealed()) {
            audit.seal();
        }
        return audit.exportTrail();
    }

    /** Returns the nonce-bound transaction ID for RCAPTURE requests. */
    public String boundTransactionId() {
        return captureIntegrity.boundTransactionId(sessionId);
    }

    public List<BoundChallenge> issuedChallenges() {
        return Collections.unmodifiableList(issuedChallenges);
    }

    /** Final verdict derived from machine state. */
    public Verdict verdict() {
        switch (machine.current()) {
            case SUCCESS:          return Verdict.PASS;
            case BLOCKED_ATTACK:   return Verdict.ATTACK;
            case FAILED_MAX_RETRIES: return Verdict.MAX_RETRIES;
            case DEVICE_ERROR:     return Verdict.DEVICE_FAILURE;
            default:               return Verdict.PENDING;
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Consecutive frames hit counter for BLINK/SMILE confirmation (configured via config.minActionFrames()). */
    private int actionHitFrames = 0;

    private static final float[] NO_INPUT = new float[0];

    /**
     * PAD liveness score 0-100 for one frame. Stub mode ignores the input (tests/demos). With a real model the
     * frame must carry RGB pixels; otherwise the frame is unscorable and is scored 0 (fail closed), never a pass.
     */
    private int scorePad(FaceFrame frame) {
        if (padScorer.isStubMode()) {
            return padScorer.score(NO_INPUT);
        }
        if (frame == null || !frame.hasPixelData()) {
            audit.logScore("PAD", 0, "UNSCORABLE: no pixel data");
            return 0;
        }
        try {
            long t = liveness.android.StageTimer.start();
            float[] in = liveness.engine.PadPreprocessor.prepare(
                    frame.pixelData(), frame.widthPx(), frame.heightPx(),
                    frame.faceBboxX(), frame.faceBboxY(), frame.faceBboxWidth(), frame.faceBboxHeight());
            liveness.android.StageTimer.stop(liveness.android.StageTimer.Stage.PREPROCESS, t);
            t = liveness.android.StageTimer.start();
            int padScoreTimed = padScorer.score(in);
            liveness.android.StageTimer.stop(liveness.android.StageTimer.Stage.PAD_INFER, t);
            return padScoreTimed;
        } catch (IllegalArgumentException e) {
            audit.logScore("PAD", 0, "UNSCORABLE: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Same-face between the last host frame and the rCapture frame. Real comparison needs an embedder, a host
     * frame and an rCapture frame with pixels; if any is missing the comparison is delegated with empty vectors,
     * which fails closed (distance 1.0) unless a test explicitly stubbed the checker.
     */
    private boolean checkSameFace(FaceFrame rcaptureFrame) {
        StubPolicy.check(sameFaceChecker.isStubbed(), "SameFaceChecker");
        if (faceEmbedder != null && rcaptureFrame != null && rcaptureFrame.hasPixelData()
                && lastHostFrame != null && lastHostFrame.hasPixelData()) {
            try {
                float[] a = faceEmbedder.embed(lastHostFrame.pixelData(),
                        lastHostFrame.widthPx(), lastHostFrame.heightPx());
                float[] b = faceEmbedder.embed(rcaptureFrame.pixelData(),
                        rcaptureFrame.widthPx(), rcaptureFrame.heightPx());
                if (a == null || b == null || a.length == 0 || a.length != b.length) {
                    return false;
                }
                return sameFaceChecker.isSameFace(a, b);
            } catch (RuntimeException e) {
                return false;
            }
        }
        return sameFaceChecker.isSameFace(new float[0], new float[0]);
    }

    private LivenessState fireBandEvent(LivenessState from, LivenessEvent event) {
        LivenessState next = machine.fire(event);
        audit.logTransition(from, next, event.name());
        return next;
    }

    /**
     * Processes a frame during CHALLENGE_WAIT -- runs action detectors
     * and PAD re-scoring simultaneously.
     */
    private LivenessState processFrameDuringChallenge(FaceFrame frame, int padScore) {
        // Check for hard PAD flag during challenge
        LivenessPolicy.Band band = policy.route(padScore);
        if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
            decisionEngine.recordWindow(band);
            if (decisionEngine.isAttackPersisted()) {
                // Row 15: CHALLENGE_WAIT -> BLOCKED_ATTACK
                LivenessState next = machine.fire(LivenessEvent.CHALLENGE_PAD_FLAG);
                audit.logTransition(LivenessState.CHALLENGE_WAIT, next,
                        "CHALLENGE_PAD_FLAG: score=" + padScore);
                retryLimiter.recordBlockedAttack(sessionId, machineUserId);
                return next;
            }
        }

        // Check if the challenge action was performed
        if (currentChallenge != null && frame.hasLandmarks()) {
            String detectedAction = detectAnyAction(frame);
            boolean rawHit = (detectedAction != null && detectedAction.equals(currentChallenge.action()));
            actionHitFrames = rawHit ? actionHitFrames + 1 : 0;
            boolean actionDetected = rawHit && actionHitFrames >= requiredHitFrames(currentChallenge.action());
            if (actionDetected || (detectedAction != null && !rawHit)) {
                long now = System.currentTimeMillis();
                ChallengeResponseValidator.ValidationResult vr =
                        challengeValidator.validate(currentChallenge, detectedAction, now);
                if (!vr.isAccepted()) {
                    // Expired / wrong-action response: not counted; timeout handles expiry
                    audit.logChallengeResult(currentChallenge.action(), false, vr.reason() + (rawHit ? "" : " (detected " + detectedAction + ")"));
                    actionHitFrames = 0;
                    return machine.current();
                }
                if (vr.isSuspiciousLatency()) {
                    audit.logChallengeResult(currentChallenge.action(), true, "SUSPICIOUS_LATENCY");
                }
                actionHitFrames = 0;
                long latencyMs = now - currentChallenge.issuedAtMs();
                challengeEchoes.add(new Attestation.ChallengeEcho(
                        currentChallenge.action(), latencyMs));
                audit.logChallengeResult(currentChallenge.action(), true,
                        "latency=" + latencyMs + "ms");

                currentChallenge = null;

                confirmedChallenges++;
                if (confirmedChallenges < config.minChallenges()) {
                    // Row 12: more challenges needed
                    LivenessState next = machine.fire(LivenessEvent.ACTION_CONFIRMED_MORE);
                    audit.logTransition(LivenessState.CHALLENGE_WAIT, next,
                            "ACTION_CONFIRMED_MORE");
                    return next;
                } else {
                    // Row 13: minimum met
                    LivenessState next = machine.fire(LivenessEvent.ACTION_CONFIRMED_DONE);
                    audit.logTransition(LivenessState.CHALLENGE_WAIT, next,
                            "ACTION_CONFIRMED_DONE");
                    return next;
                }
            }
        }

        return machine.current();
    }

    /**
     * Detects whether the subject performed the requested challenge action
     * using landmark-based detectors (Section 20.1-20.2).
     */
    /** Consecutive qualifying frames needed to confirm an action (HANDOFF-2 s5: blink needs several low-EAR frames). */
    private int requiredHitFrames(String action) {
        return ("BLINK".equals(action) || "SMILE".equals(action)) ? config.minActionFrames() : 1;
    }

    private String detectAnyAction(FaceFrame frame) {
        if (currentChallenge != null && detectChallengeAction(frame, currentChallenge.action())) {
            return currentChallenge.action();
        }
        // Only report directly opposing wrong actions to prevent natural reflexes (such as
        // blinking while turning) from erroneously resetting challenge progress or reporting wrong action.
        if (currentChallenge != null) {
            String exp = currentChallenge.action();
            if ("TURN_LEFT".equals(exp) && detectChallengeAction(frame, "TURN_RIGHT")) {
                return "TURN_RIGHT";
            }
            if ("TURN_RIGHT".equals(exp) && detectChallengeAction(frame, "TURN_LEFT")) {
                return "TURN_LEFT";
            }
        }
        return null;
    }
    private boolean detectChallengeAction(FaceFrame frame, String action) {
        return liveness.engine.ChallengeActionDetector.detect(
                action, frame.landmarks(), frame.yawDeg(), frame.pitchDeg(), config);
    }
}
