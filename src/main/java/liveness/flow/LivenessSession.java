package liveness.flow;

import liveness.audit.AuditChain;
import liveness.audit.AuditEvent;
import liveness.audit.AuditLog;
import liveness.capture.CaptureIntegrityChecker;
import liveness.capture.FrameFingerprint;
import liveness.challenge.BoundChallenge;
import liveness.challenge.ChallengeGenerator;
import liveness.challenge.ChallengeResponseValidator;
import liveness.core.FaceFrame;
import liveness.crypto.Attestation;
import liveness.crypto.EcdsaSignerStub;
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
import liveness.util.Sha256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Session orchestrator for face liveness detection (§6).
 * <p>
 * Drives the {@link LivenessStateMachine}. <strong>No boolean flags.</strong>
 * All routing decisions derive from {@code machine.current()}.
 * <p>
 * Attestation is produced ONLY from {@code VERIFYING_FINAL → SUCCESS}.
 * Liveness-failed captures are NEVER saved.
 */
public final class LivenessSession {

    // --- Identity ---
    private final String subjectId;
    private final String sessionId;

    // --- Components (§6 fields) ---
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
    private final EcdsaSignerStub signer;

    // --- Session state (not boolean flags — data accumulators) ---
    private final String machineUserId;
    private final List<BoundChallenge> issuedChallenges;
    private final List<Attestation.ChallengeEcho> challengeEchoes;
    private final List<float[]> livenessEmbeddings;
    private int challengeCount;
    private int retryCount;
    private BoundChallenge currentChallenge;
    private Attestation attestation;

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
     * @param signer           ECDSA signer for attestation
     * @param supervisor       supervisor auth (nullable)
     * @param machineUserId    composite key for rate limiting
     */
    public LivenessSession(String subjectId,
                           DeviceAdapter device,
                           LivenessConfig config,
                           MiniFasNetScorer padScorer,
                           SameFaceChecker sameFaceChecker,
                           ChallengeGenerator challengeGen,
                           RetryRateLimiter retryLimiter,
                           CaptureIntegrityChecker captureIntegrity,
                           EcdsaSignerStub signer,
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
                100.0,  // blurMinVariance seed
                40.0,   // brightnessMin
                250.0,  // brightnessMax
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

        this.issuedChallenges = new ArrayList<>();
        this.challengeEchoes = new ArrayList<>();
        this.livenessEmbeddings = new ArrayList<>();
        this.challengeCount = 0;
        this.retryCount = 0;
        this.currentChallenge = null;
        this.attestation = null;
    }

    // =========================================================================
    // Public API — all routing from machine.current(), no boolean flags
    // =========================================================================

    /** Starts the session: IDLE → DEVICE_CHECK. */
    public LivenessState start() {
        LivenessState next = machine.fire(LivenessEvent.START_CAPTURE);
        audit.logTransition(LivenessState.IDLE, next, "START_CAPTURE");
        return next;
    }

    /**
     * Opens the device: DEVICE_CHECK → POSITIONING (or DEVICE_ERROR).
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
     * Pipeline order per §3:
     * 1. Capture Integrity (IAD)
     * 2. Quality Gate (raw frame, before crop — R3)
     * 3. Spatial PAD (per-frame)
     * 4. Decision Engine (hard gates + fusion)
     *
     * @param frame the captured face frame
     * @return the resulting state
     */
    public LivenessState processFrame(FaceFrame frame) {
        LivenessState current = machine.current();

        // --- 1. Capture Integrity Check ---
        FrameFingerprint fp = new FrameFingerprint(
                0L,  // pHash computed from pixel data in production
                frame.timestampMs(),
                frame.sequenceNum(),
                frame.widthPx());

        Optional<String> integrityViolation = captureIntegrity.checkFrame(
                fp,
                String.valueOf(frame.deviceId()),
                frame.signature());

        if (integrityViolation.isPresent()) {
            audit.logScore("INTEGRITY", 0, integrityViolation.get());
            LivenessState next = machine.fire(LivenessEvent.IAD_FAILURE);
            audit.logTransition(current, next, "IAD_FAILURE: " + integrityViolation.get());
            return next;
        }

        // --- 2. Quality Gate (raw frame, before crop — R3, §6.2) ---
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
                frame.heightPx());

        QualityGate.QualityResult qr = qualityGate.evaluate(rawData);

        if (!qr.passed()) {
            // Quality failure → POSITIONING (guidance, not attack)
            if (current == LivenessState.PASSIVE) {
                LivenessState next = machine.fire(LivenessEvent.QUALITY_DROPPED);
                audit.logTransition(current, next, "QUALITY_DROPPED: " + qr.failureReason());
                return next;
            }
            // Already in POSITIONING — stay there
            return current;
        }

        // If in POSITIONING and quality is OK, transition to PASSIVE
        if (current == LivenessState.POSITIONING) {
            LivenessState next = machine.fire(LivenessEvent.QUALITY_OK);
            audit.logTransition(current, next, "QUALITY_OK");
            current = next;
        }

        // --- 3. Spatial PAD scoring ---
        if (current == LivenessState.PASSIVE || current == LivenessState.CHALLENGE_WAIT) {
            int padScore = padScorer.score(new float[0]); // stub: uses configured score
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
                DecisionEngine.DecisionResult result = decisionEngine.evaluate(
                        score, false, false, false);
                audit.logBand(result.band().name(), score, result.reason());

                LivenessPolicy.Band band = result.band();
                switch (band) {
                    case CLEAR_BONA_FIDE:
                        return fireBandEvent(current, LivenessEvent.CLEAR_BONA_FIDE);
                    case UNCERTAIN:
                        if (config.activeEnabled()) {
                            return fireBandEvent(current, LivenessEvent.UNCERTAIN);
                        }
                        // Active disabled — treat as bona fide pass
                        return fireBandEvent(current, LivenessEvent.CLEAR_BONA_FIDE);
                    case CLEAR_ATTACK:
                        if (decisionEngine.isAttackPersisted()) {
                            return fireBandEvent(current, LivenessEvent.CLEAR_ATTACK);
                        }
                        // Not yet persisted — continue accumulating
                        return current;
                    default:
                        return current;
                }
            }
        }

        return current;
    }

    /**
     * Issues a challenge prompt: CHALLENGE_PROMPT → CHALLENGE_WAIT.
     */
    public LivenessState issueChallenge() {
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
     * Reports a challenge timeout: CHALLENGE_WAIT → RETRY_AVAILABLE.
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
     * Processes RCAPTURE result: FINAL_CAPTURE → VERIFYING_FINAL.
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
        // Row 16: FINAL_CAPTURE → VERIFYING_FINAL
        LivenessState next = machine.fire(LivenessEvent.RCAPTURE_RETURNED);
        audit.logTransition(LivenessState.FINAL_CAPTURE, next, "RCAPTURE_RETURNED");

        // Verify binding: transaction ID + freshness
        boolean bindingOk = captureIntegrity.verifyRCapture(
                returnedTransactionId, rcaptureTimestamp, requestTimestamp);

        if (!bindingOk) {
            // Row 21: VERIFYING_FINAL → DEVICE_ERROR (INTEGRITY_FAILURE)
            next = machine.fire(LivenessEvent.BINDING_FAILURE);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "BINDING_FAILURE");
            return next;
        }

        // Same-face check (§6.4)
        boolean sameFaceOk = sameFaceChecker.isSameFace(new float[0], new float[0]);
        if (!sameFaceOk) {
            // Row 19: VERIFYING_FINAL → RETRY_AVAILABLE
            next = machine.fire(LivenessEvent.SAME_FACE_FAIL);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "SAME_FACE_FAIL");
            return next;
        }

        // Final PAD check on RCAPTURE image
        int finalPadScore = padScorer.score(new float[0]);
        LivenessPolicy.Band finalBand = policy.route(finalPadScore);
        audit.logScore("FINAL_PAD", finalPadScore, finalBand.name());

        if (finalBand == LivenessPolicy.Band.CLEAR_ATTACK) {
            // Row 20: VERIFYING_FINAL → BLOCKED_ATTACK
            next = machine.fire(LivenessEvent.FINAL_PAD_ATTACK);
            audit.logTransition(LivenessState.VERIFYING_FINAL, next, "FINAL_PAD_ATTACK");
            retryLimiter.recordBlockedAttack(sessionId, machineUserId);
            return next;
        }

        // All checks pass — commit
        // Row 18: VERIFYING_FINAL → SUCCESS (only path that produces attestation)
        String imageSha256 = Sha256.hex(rcaptureImageBytes);
        String transcriptSha256 = audit.chain().latestHash();

        List<String> mechanisms = new ArrayList<>();
        mechanisms.add("MiniFASNet");

        Attestation.PadResult padResult = new Attestation.PadResult(
                "NO_ATTACK",
                100 - finalPadScore,  // padDecisionScore = 100 - livenessScore (§6.5)
                mechanisms,
                Collections.unmodifiableList(challengeEchoes));

        attestation = new Attestation(
                subjectId, sessionId,
                imageSha256, transcriptSha256,
                "T1", rcaptureTimestamp,
                padScorer.modelVersion(),
                config.configVersion(),
                "STREAM",
                padResult);
        attestation.sign(signer);

        next = machine.fire(LivenessEvent.COMMIT_CHECKS_PASS);
        audit.logTransition(LivenessState.VERIFYING_FINAL, next, "COMMIT_CHECKS_PASS");
        audit.seal();

        return next;
    }

    /**
     * Reports RCAPTURE failure: FINAL_CAPTURE → DEVICE_ERROR.
     */
    public LivenessState rcaptureFailure() {
        LivenessState next = machine.fire(LivenessEvent.RCAPTURE_FAILURE);
        audit.logTransition(LivenessState.FINAL_CAPTURE, next, "RCAPTURE_FAILURE");
        return next;
    }

    /**
     * Attempts a retry: RETRY_AVAILABLE → PASSIVE or FAILED_MAX_RETRIES.
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
     * Reports a positioning timeout: POSITIONING → RETRY_AVAILABLE.
     */
    public LivenessState positioningTimeout() {
        LivenessState next = machine.fire(LivenessEvent.POSITIONING_TIMEOUT);
        audit.logTransition(LivenessState.POSITIONING, next, "POSITIONING_TIMEOUT");
        return next;
    }

    // =========================================================================
    // Getters — session state
    // =========================================================================

    public String subjectId()            { return subjectId; }
    public String sessionId()            { return sessionId; }
    public LivenessState currentState()  { return machine.current(); }
    public Attestation attestation()     { return attestation; }
    public AuditLog audit()              { return audit; }
    public int challengeCount()          { return challengeCount; }
    public int retryCount()              { return retryCount; }
    public BoundChallenge currentChallenge() { return currentChallenge; }

    public List<BoundChallenge> issuedChallenges() {
        return Collections.unmodifiableList(issuedChallenges);
    }

    /** Final verdict derived from machine state. */
    public Verdict verdict() {
        switch (machine.current()) {
            case SUCCESS:          return Verdict.PASS;
            case BLOCKED_ATTACK:   return Verdict.ATTACK;
            case FAILED_MAX_RETRIES: return Verdict.MAX_RETRIES;
            case DEVICE_ERROR:     return Verdict.DEVICE_ERROR;
            default:               return Verdict.PENDING;
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private LivenessState fireBandEvent(LivenessState from, LivenessEvent event) {
        LivenessState next = machine.fire(event);
        audit.logTransition(from, next, event.name());
        return next;
    }

    /**
     * Processes a frame during CHALLENGE_WAIT — runs action detectors
     * and PAD re-scoring simultaneously.
     */
    private LivenessState processFrameDuringChallenge(FaceFrame frame, int padScore) {
        // Check for hard PAD flag during challenge
        LivenessPolicy.Band band = policy.route(padScore);
        if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
            decisionEngine.recordWindow(band);
            if (decisionEngine.isAttackPersisted()) {
                // Row 15: CHALLENGE_WAIT → BLOCKED_ATTACK
                LivenessState next = machine.fire(LivenessEvent.CHALLENGE_PAD_FLAG);
                audit.logTransition(LivenessState.CHALLENGE_WAIT, next,
                        "CHALLENGE_PAD_FLAG: score=" + padScore);
                retryLimiter.recordBlockedAttack(sessionId, machineUserId);
                return next;
            }
        }

        // Check if the challenge action was performed
        if (currentChallenge != null && frame.hasLandmarks()) {
            boolean actionDetected = detectChallengeAction(frame, currentChallenge.action());
            if (actionDetected) {
                long now = System.currentTimeMillis();
                long latencyMs = now - currentChallenge.issuedAtMs();
                challengeEchoes.add(new Attestation.ChallengeEcho(
                        currentChallenge.action(), latencyMs));
                audit.logChallengeResult(currentChallenge.action(), true,
                        "latency=" + latencyMs + "ms");

                currentChallenge = null;

                if (challengeCount < config.minChallenges()) {
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
     * using landmark-based detectors (§20.1-20.2).
     */
    private boolean detectChallengeAction(FaceFrame frame, String action) {
        double[] landmarks = frame.landmarks();
        if (landmarks == null || landmarks.length < 12) {
            return false;
        }

        switch (action) {
            case "BLINK":
                // EAR threshold 0.2 (§20.1, starting value)
                double ear = LandmarkDetector.computeEAR(landmarks);
                return LandmarkDetector.detectBlink(ear, 0.2);

            case "SMILE":
                if (landmarks.length >= 16) {
                    double mar = LandmarkDetector.computeMAR(landmarks);
                    return LandmarkDetector.detectSmile(mar, 0.6);
                }
                return false;

            case "TURN_LEFT":
                return LandmarkDetector.detectTurn(frame.yawDeg(), 15.0, "LEFT");

            case "TURN_RIGHT":
                return LandmarkDetector.detectTurn(frame.yawDeg(), 15.0, "RIGHT");

            case "LOOK_LEFT":
                return LandmarkDetector.detectTurn(frame.yawDeg(), 10.0, "LEFT");

            case "LOOK_RIGHT":
                return LandmarkDetector.detectTurn(frame.yawDeg(), 10.0, "RIGHT");

            case "LOOK_UP":
                return frame.pitchDeg() < -10.0;

            case "LOOK_DOWN":
                return frame.pitchDeg() > 10.0;

            default:
                return false;
        }
    }
}
