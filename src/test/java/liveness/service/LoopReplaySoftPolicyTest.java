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
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session-level policy for the LOOP_REPLAY signal (Part 1 patch, ADR-016).
 * <p>
 * Loop detection is unvalidated on real cameras (Part 2 X13). Default policy is therefore SOFT:
 * a loop signal never produces an attack verdict by itself; a loop-suspected session can only
 * pass after a completed active challenge, and never when active challenges are disabled.
 * With loopReplayHardFail=true the old behaviour (integrity failure) is kept.
 * <p>
 * The device here replays a 6-frame clip with real motion at 33 ms timestamps (synthetic).
 */
class LoopReplaySoftPolicyTest {

    private static final int W = 96;
    private static final int H = 72;
    private static final int LOOP_LEN = 6;

    /** Wraps a BONA_FIDE mock but replaces timestamp, pHash and content digest with a looped clip. */
    private static final class LoopingDevice implements DeviceAdapter {
        private final MockDeviceAdapter base = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        private final Random rng = new Random(5);
        private final boolean loop;
        private int n;

        LoopingDevice(boolean loop) { this.loop = loop; }

        @Override public void open() throws DeviceException { base.open(); }
        @Override public void close() { base.close(); }
        @Override public boolean isOpen() { return base.isOpen(); }

        @Override
        public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
            return base.captureFinal(boundTransactionId);   // final capture keeps the mock's nonce echo
        }

        @Override
        public FaceFrame captureFrame() throws DeviceException {
            FaceFrame f = base.captureFrame();
            double dx;
            double dy;
            if (loop) {                               // attack: 6-frame clip with real motion, repeated
                int k = n % LOOP_LEN;
                dx = 5 * Math.sin(k * 1.7);
                dy = 5 * Math.cos(k * 1.1);
            } else {                                  // live: steady drift, never revisits a position
                dx = 0.4 * n;
                dy = 0.3 * n;
            }
            byte[] gray = gray(dx, dy);
            long hash = FrameFingerprint.computePHash(gray, W, H);
            byte[] digest = FrameFingerprint.computeContentDigest(gray, W, H);
            long ts = 1_000_000L + 33L * n;
            n++;
            return new FaceFrame(f.quality(), f.signature(), f.deviceId(),
                    ts, n, f.widthPx(), f.heightPx(), f.pixelData(),
                    f.faceBboxX(), f.faceBboxY(), f.faceBboxWidth(), f.faceBboxHeight(),
                    f.yawDeg(), f.pitchDeg(), f.rollDeg(),
                    f.faceCount(), f.laplacianVariance(), f.brightnessMean(), f.landmarks(),
                    hash, f.encodedBytes(), digest);
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

    private LivenessService.LivenessResult run(boolean loopDevice, boolean hardFail, boolean active) {
        LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(30)               // decide only after loop detection can fire
                .activeEnabled(active)
                .loopReplayHardFail(hardFail)
                .maxRetries(3)
                .build();
        MiniFasNetScorer scorer = new MiniFasNetScorer(null);
        scorer.setStubScore(85);
        SameFaceChecker same = new SameFaceChecker(0.6);
        same.setStubResult(true);
        LivenessService svc = new LivenessService(cfg, scorer, same,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        return svc.performLiveness("subject-loop", new LoopingDevice(loopDevice), "mu-loop",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
    }

    @Test
    void liveMovingClipStillPassesUnderSoftPolicy() {
        LivenessService.LivenessResult r = run(false, false, false);
        assertTrue(r.isSuccess(), "ever-changing live motion must pass: " + r.verdict());
    }

    @Test
    void loopedClipNeverPassesWhenActiveDisabledUnderSoftPolicy() {
        LivenessService.LivenessResult r = run(true, false, false);
        assertFalse(r.isSuccess(), "loop-suspected session must not pass without a challenge");
    }

    @Test
    void loopedClipDoesNotGetAttackVerdictUnderSoftPolicy() {
        LivenessService.LivenessResult r = run(true, false, true);
        assertNotEquals(Verdict.ATTACK, r.verdict(), "loop signal alone is not an attack verdict");
        assertFalse(r.isSuccess(), "mock device cannot complete the challenge, so no pass");
    }

    @Test
    void loopedClipFailsIntegrityUnderHardPolicy() {
        LivenessService.LivenessResult r = run(true, true, false);
        assertFalse(r.isSuccess());
    }

    @Test
    void softAndHardPoliciesBothDefaultCorrectly() {
        assertFalse(LivenessConfig.defaultConfig().loopReplayHardFail(), "default must be soft");
        assertTrue(LivenessConfig.builder().loopReplayHardFail(true).build().loopReplayHardFail());
    }
}
