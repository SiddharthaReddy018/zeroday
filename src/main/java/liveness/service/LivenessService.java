package liveness.service;

import liveness.capture.CaptureIntegrityChecker;
import liveness.capture.DeviceIdentityValidator;
import liveness.capture.FrameSequenceMonitor;
import liveness.capture.FreshnessChecker;
import liveness.capture.SessionTransactionBinder;
import liveness.challenge.ChallengeGenerator;
import liveness.core.FaceFrame;
import liveness.crypto.Attestation;
import liveness.crypto.Signer;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.LivenessSession;
import liveness.flow.LivenessState;
import liveness.flow.RetryRateLimiter;
import liveness.flow.SupervisorAuth;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;

import java.util.Objects;

/**
 * Service-layer orchestrator for face liveness verification (§22).
 * <p>
 * Liveness enforcement lives HERE, NOT in the UI. A UI bug can never
 * accept a failed liveness result. Inference never runs on the UI thread.
 * <p>
 * Three workflows: RESIDENT_REGISTRATION, OPERATOR_AUTH, SUPERVISOR_AUTH.
 * INFANT variant uses passive-only (no challenges).
 */
public final class LivenessService {

    private final LivenessConfig config;
    private final MiniFasNetScorer padScorer;
    private final SameFaceChecker sameFaceChecker;
    private final ChallengeGenerator challengeGen;
    private final RetryRateLimiter retryLimiter;
    private final Signer signer;
    private volatile liveness.android.FaceEmbedder faceEmbedder; // optional; required for a real same-face check

    /** Plugs the face embedder (e.g. SFace/ArcFace). Without one, same-face fails closed outside test stubs. */
    public void setFaceEmbedder(liveness.android.FaceEmbedder embedder) { this.faceEmbedder = embedder; }

    public LivenessService(LivenessConfig config,
                           MiniFasNetScorer padScorer,
                           SameFaceChecker sameFaceChecker,
                           ChallengeGenerator challengeGen,
                           RetryRateLimiter retryLimiter,
                           Signer signer) {
        this.config = Objects.requireNonNull(config, "config");
        this.padScorer = Objects.requireNonNull(padScorer, "padScorer");
        this.sameFaceChecker = Objects.requireNonNull(sameFaceChecker, "sameFaceChecker");
        this.challengeGen = Objects.requireNonNull(challengeGen, "challengeGen");
        this.retryLimiter = Objects.requireNonNull(retryLimiter, "retryLimiter");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    /**
     * Runs the full liveness pipeline on the calling thread.
     * <p>
     * Call from a background thread — never from the UI thread.
     *
     * @param subjectId     the subject being verified
     * @param device        the frame source (MDS adapter or mock)
     * @param machineUserId composite key for rate limiting
     * @param workflow      which workflow to run
     * @param callback      UI callback (nullable; dispatches events to UI thread)
     * @return the liveness result
     */
    public LivenessSession startSession(String subjectId,
                                        DeviceAdapter device,
                                        String machineUserId,
                                        WorkflowType workflow) {
        if (!liveness.policy.StubPolicy.stubsAllowed()) {
            if (faceEmbedder == null || faceEmbedder.getClass().getSimpleName().contains("GridEmbedder")
                    || faceEmbedder.getClass().getSimpleName().contains("ReferenceFaceEmbedder")) {
                throw new IllegalStateException("LivenessService refusing to start outside dev without a production FaceEmbedder (e.g. SFaceEmbedder)");
            }
        }

        if (retryLimiter.isLocked(machineUserId)) {
            throw new IllegalStateException("LOCKED: machine+user rate limit exceeded for " + machineUserId);
        }

        CaptureIntegrityChecker captureIntegrity = buildCaptureIntegrity(device);
        SupervisorAuth supervisor = (workflow == WorkflowType.SUPERVISOR_AUTH)
                ? new SupervisorAuth(subjectId) : null;

        LivenessSession session = new LivenessSession(
                subjectId, device, config, padScorer, sameFaceChecker,
                challengeGen, retryLimiter, captureIntegrity,
                signer, supervisor, machineUserId);
        session.setCaptureMode(device.captureMode());
        session.setFaceEmbedder(faceEmbedder);
        return session;
    }

    public LivenessResult performLiveness(String subjectId,
                                          DeviceAdapter device,
                                          String machineUserId,
                                          WorkflowType workflow,
                                          LivenessCallback callback) {
        LivenessSession session = startSession(subjectId, device, machineUserId, workflow);

        // --- Drive the state machine ---
        // 1. Start
        LivenessState state = session.start();
        notifyState(callback, state);

        // 2. Open device
        state = session.openDevice();
        notifyState(callback, state);

        if (state.isTerminal()) {
            // Device failed to open (incl. Android integrity failure): the UI must still be told.
            LivenessResult early = buildResult(session);
            if (callback != null) {
                callback.onResult(early);
            }
            return early;
        }

        // 3. Frame processing loop
        int framesProcessed = 0;
        int maxFrames = 300;  // safety limit (~10s at 30fps)

        while (!state.isTerminal() && framesProcessed < maxFrames) {
            state = session.currentState();

            switch (state) {
                case POSITIONING:
                case PASSIVE:
                case CHALLENGE_WAIT:
                    // Capture and process a frame
                    try {
                        FaceFrame frame = device.captureFrame();
                        if (frame != null) {
                            if (callback != null) {
                                callback.onPreviewFrame(frame.pixelData());
                            }
                            state = session.processFrame(frame);
                            framesProcessed++;
                            notifyState(callback, state);
                            if (callback != null) {
                                callback.onProgress(framesProcessed, config.minQualifyingFrames());
                            }
                        }
                    } catch (DeviceException e) {
                        state = session.deviceDisconnect();
                        notifyState(callback, state);
                    }
                    break;

                case CHALLENGE_PROMPT:
                    if (workflow == WorkflowType.INFANT) {
                        // INFANT: passive-only, skip challenges → treat as bona fide
                        // This path shouldn't be reached if config disables active for INFANT
                        break;
                    }
                    state = session.issueChallenge();
                    if (callback != null && session.currentChallenge() != null) {
                        callback.onChallengePrompt(
                                session.currentChallenge().action(),
                                config.challengeTimeoutMs());
                    }
                    notifyState(callback, state);
                    break;

                case FINAL_CAPTURE:
                    // Trigger RCAPTURE
                    try {
                        String boundTx = session.boundTransactionId();  // nonce-bound transaction ID
                        long requestTs = System.currentTimeMillis();     // taken BEFORE the capture request
                        FaceFrame rcaptureFrame = device.captureFinal(boundTx);
                        if (rcaptureFrame != null && rcaptureFrame.hasPixelData()) {
                            String returnedTx = device.lastReturnedTransactionId();
                            state = session.processRCapture(
                                    rcaptureFrame,
                                    returnedTx != null ? returnedTx : boundTx,
                                    rcaptureFrame.timestampMs(),
                                    requestTs);
                        } else {
                            state = session.rcaptureFailure();
                        }
                        notifyState(callback, state);
                    } catch (DeviceException e) {
                        state = session.rcaptureFailure();
                        notifyState(callback, state);
                    }
                    break;

                case RETRY_AVAILABLE:
                    state = session.attemptRetry();
                    notifyState(callback, state);
                    break;

                default:
                    // Terminal or unexpected state
                    break;
            }

            if (state.isTerminal()) {
                break;
            }
        }

        LivenessResult result = buildResult(session);
        if (callback != null) {
            callback.onResult(result);
        }
        return result;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private CaptureIntegrityChecker buildCaptureIntegrity(DeviceAdapter device) {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(
                config.frameFreezePHashWindowSize(), device.expectedFrameIntervalMs());
        DeviceIdentityValidator deviceId = new DeviceIdentityValidator();
        FreshnessChecker freshness = new FreshnessChecker(config.rcaptureFreshnessWindowMs());
        SessionTransactionBinder binder = new SessionTransactionBinder();
        return new CaptureIntegrityChecker(monitor, deviceId, freshness, binder);
    }

    private void notifyState(LivenessCallback callback, LivenessState state) {
        if (callback != null) {
            callback.onStateChanged(state.name(), liveness.ui.UiMessages.get(state.name()));
        }
    }

    private LivenessResult buildResult(LivenessSession session) {
        return new LivenessResult(
                session.verdict(),
                session.attestation(),
                session.sessionId(),
                session.retryCount(),
                session.currentState() != LivenessState.SUCCESS
                        ? session.currentState().name() : null,
                session.signedAuditHead());
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /** Workflow types supported by the liveness service. */
    public enum WorkflowType {
        RESIDENT_REGISTRATION,
        OPERATOR_AUTH,
        SUPERVISOR_AUTH,
        INFANT
    }

    /**
     * Result of a liveness verification session.
     * <p>
     * Liveness-failed captures are NEVER saved (constraint #5).
     */
    public static final class LivenessResult {
        private final Verdict verdict;
        private final Attestation attestation;  // non-null only on SUCCESS
        private final String sessionId;
        private final int retryCount;
        private final String failureReason;     // non-null only on failure
        private final liveness.audit.AuditSeal.SignedHead auditHead; // signed for every outcome (ADR-010)

        public LivenessResult(Verdict verdict, Attestation attestation,
                              String sessionId, int retryCount, String failureReason) {
            this(verdict, attestation, sessionId, retryCount, failureReason, null);
        }

        public LivenessResult(Verdict verdict, Attestation attestation,
                              String sessionId, int retryCount, String failureReason,
                              liveness.audit.AuditSeal.SignedHead auditHead) {
            this.auditHead = auditHead;
            this.verdict = verdict;
            this.attestation = attestation;
            this.sessionId = sessionId;
            this.retryCount = retryCount;
            this.failureReason = failureReason;
        }

        public Verdict verdict()           { return verdict; }
        public Attestation attestation()   { return attestation; }
        public String sessionId()          { return sessionId; }
        public int retryCount()            { return retryCount; }
        public String failureReason()      { return failureReason; }
        public liveness.audit.AuditSeal.SignedHead auditHead() { return auditHead; }
        public String auditHeadHash() { return auditHead != null ? auditHead.headHash() : null; }
        public int auditEntryCount()  { return auditHead != null ? auditHead.size() : 0; }

        /** True only if verdict is PASS and attestation is present. */
        public boolean isSuccess() {
            return verdict == Verdict.PASS && attestation != null;
        }
    }
}
