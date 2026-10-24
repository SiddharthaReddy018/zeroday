package liveness.service;

import liveness.capture.FrameFingerprint;
import liveness.challenge.ChallengeGenerator;
import liveness.core.FaceFrame;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.flow.LivenessSession;
import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session-level wiring of the relative blur gate (step D). Synthetic: the device only varies fullFrameNorm.
 * Constants are candidates, not fitted. Same moving clip as LoopReplaySoftPolicyTest (live, never loops).
 */
class RelativeBlurSessionTest {

    private static final int W = 96;
    private static final int H = 72;

    private static final class NormDevice implements DeviceAdapter {
        private final MockDeviceAdapter base = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        private final Random rng = new Random(5);
        private final int sharpFrames;
        private final double sharpNorm;
        private final double laterNorm;
        private int n;

        NormDevice(int sharpFrames, double sharpNorm, double laterNorm) {
            this.sharpFrames = sharpFrames;
            this.sharpNorm = sharpNorm;
            this.laterNorm = laterNorm;
        }

        @Override public void open() throws DeviceException { base.open(); }
        @Override public void close() { base.close(); }
        @Override public boolean isOpen() { return base.isOpen(); }

        @Override
        public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
            return base.captureFinal(boundTransactionId);
        }

        @Override
        public FaceFrame captureFrame() throws DeviceException {
            FaceFrame f = base.captureFrame();
            double norm = n < sharpFrames ? sharpNorm : laterNorm;
            byte[] gray = gray(0.4 * n, 0.3 * n);
            long hash = FrameFingerprint.computePHash(gray, W, H);
            byte[] digest = FrameFingerprint.computeContentDigest(gray, W, H);
            long ts = 1_000_000L + 33L * n;
            n++;
            return new FaceFrame(f.quality(), f.signature(), f.deviceId(),
                    ts, n, f.widthPx(), f.heightPx(), f.pixelData(),
                    f.faceBboxX(), f.faceBboxY(), f.faceBboxWidth(), f.faceBboxHeight(),
                    f.yawDeg(), f.pitchDeg(), f.rollDeg(),
                    f.faceCount(), f.laplacianVariance(), f.brightnessMean(), f.landmarks(),
                    hash, f.encodedBytes(), digest, norm);
        }

        private byte[] gray(double dx, double dy) {
            byte[] g = new byte[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double cx = x - W / 2.0 - dx;
                    double cy = y - H / 2.0 - dy;
                    double v = 120 + 90 * Math.exp(-(cx * cx + cy * cy) / (2 * 18 * 18))
                            + 30 * Math.exp(-((cx - 12) * (cx - 12) + (cy + 6) * (cy + 6)) / (2 * 5 * 5))
                            + rng.nextGaussian() * 2;
                    g[y * W + x] = (byte) Math.max(0, Math.min(255, (int) v));
                }
            }
            return g;
        }
    }

    private LivenessService.LivenessResult run(boolean relative, NormDevice dev) {
        LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(30)
                .activeEnabled(false)
                .maxRetries(3)
                .relativeBlurEnabled(relative)
                .relativeBlurWindow(10)
                .build();
        MiniFasNetScorer scorer = new MiniFasNetScorer(null);
        scorer.setStubScore(85);
        SameFaceChecker same = new SameFaceChecker(0.6);
        same.setStubResult(true);
        LivenessService svc = new LivenessService(cfg, scorer, same,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        return svc.performLiveness("subject-blur", dev, "mu-blur",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
    }

    @Test
    void defaultsAreOffAndCandidateConstants() {
        LivenessConfig c = LivenessConfig.builder().build();
        assertFalse(c.relativeBlurEnabled());
        assertEquals(30, c.relativeBlurWindow());
        assertEquals(2.0, c.relativeBlurDropFactor(), 1e-9);
        assertEquals(1.5, c.relativeBlurYShiftFactor(), 1e-9);
    }

    @Test
    @Timeout(60)
    void flagOffIgnoresBlurredSequence() {
        LivenessService.LivenessResult r = run(false, new NormDevice(15, 0.20, 0.06));
        assertTrue(r.isSuccess(), "flag off must not change behavior: " + r.verdict());
    }

    @Test
    @Timeout(60)
    void flagOnSteadySharpSequencePasses() {
        LivenessService.LivenessResult r = run(true, new NormDevice(1_000_000, 0.20, 0.20));
        assertTrue(r.isSuccess(), "steady sharp must pass after warm-up: " + r.verdict());
    }

    @Test
    @Timeout(60)
    void flagOnBlurAfterBaselineDoesNotPass() {
        LivenessService.LivenessResult r = run(true, new NormDevice(15, 0.20, 0.06));
        assertFalse(r.isSuccess(), "3x drop after a sharp baseline must be rejected");
    }

    @Test
    @Timeout(60)
    void flagOnWithNoValidSamplesBypasses() {
        LivenessService.LivenessResult r = run(true, new NormDevice(1_000_000, -1.0, -1.0));
        assertTrue(r.isSuccess(), "no valid full-frame sample: relative check skipped: " + r.verdict());
    }

    /** Drives processFrame directly so the audit trail (the only place the reason lands) can be read. */
    private String driveToTrail(boolean relative, NormDevice dev, String[] finalState) throws Exception {
        LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(30)
                .activeEnabled(false)
                .maxRetries(3)
                .relativeBlurEnabled(relative)
                .relativeBlurWindow(10)
                .build();
        MiniFasNetScorer scorer = new MiniFasNetScorer(null);
        scorer.setStubScore(85);
        SameFaceChecker same = new SameFaceChecker(0.6);
        same.setStubResult(true);
        LivenessService svc = new LivenessService(cfg, scorer, same,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        LivenessSession s = svc.startSession("subject-blur", dev, "mu-blur",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION);
        s.start();
        s.openDevice();
        for (int i = 0; i < 600; i++) {
            String st = s.currentState().name();
            if (!st.equals("POSITIONING") && !st.equals("PASSIVE") && !st.equals("CHALLENGE_WAIT")) break;
            FaceFrame f = dev.captureFrame();
            if (f != null) s.processFrame(f);
        }
        finalState[0] = s.currentState().name();
        return s.exportAuditTrail();
    }

    @Test
    @Timeout(60)
    void flagOnBlurAfterBaselineIsRejectedForTheBlurReason() throws Exception {
        String[] st = new String[1];
        String trail = driveToTrail(true, new NormDevice(15, 0.20, 0.06), st);
        assertTrue(trail.contains("guide.quality.blur_relative"),
                "audit trail must carry the blur reason; final state " + st[0]);
        assertNotEquals("SUCCESS", st[0]);
    }

    @Test
    @Timeout(60)
    void flagOnSteadySharpHasNoBlurReasonAndLeavesPositioning() throws Exception {
        String[] st = new String[1];
        String trail = driveToTrail(true, new NormDevice(1_000_000, 0.20, 0.20), st);
        assertFalse(trail.contains("blur_relative"), "steady sharp must not log the blur reason");
        assertFalse(trail.contains("light_unsteady"), "steady sharp must not log the light reason");
        assertNotEquals("POSITIONING", st[0], "steady sharp must get past warm-up");
    }
}
