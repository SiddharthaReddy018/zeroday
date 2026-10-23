package liveness.capture;

import liveness.capture.FrameSequenceMonitor.IntegrityViolation;
import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2/R3 acceptance test: validates that live still users under realistic camera sensor noise
 * and subpixel drift do not suffer false FROZEN or LOOP_REPLAY flags
 * when using the PRODUCTION DEFAULT configuration.
 *
 * Also validates freeze detection on byte-identical frames and the honest
 * limitation of pHash-only loop detection under fresh sensor noise.
 */
public class FrameSequenceMonitorFalsePositiveTest {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 72;
    private static final int TRIALS = 200;
    private static final long SEED = 42L;

    private static FrameFingerprint generateSyntheticFrame(
            int frameIdx, double sigma, double driftPx, Random rng) {
        byte[] gray = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            double effY = y + driftPx * frameIdx;
            for (int x = 0; x < WIDTH; x++) {
                double effX = x + driftPx * frameIdx;
                // Smooth static scene (sinusoidal background luminance pattern)
                double base = 128.0 + 35.0 * Math.sin(effX / 8.0) + 35.0 * Math.cos(effY / 8.0);
                if (sigma > 0) {
                    base += rng.nextGaussian() * sigma;
                }
                int clamped = Math.max(0, Math.min(255, (int) Math.round(base)));
                gray[y * WIDTH + x] = (byte) clamped;
            }
        }

        long pHash = FrameFingerprint.computePHash(gray, WIDTH, HEIGHT);
        byte[] digest = FrameFingerprint.computeContentDigest(gray, WIDTH, HEIGHT);
        return new FrameFingerprint(pHash, 1000L + frameIdx * 33L, frameIdx + 1, WIDTH, digest);
    }

    /**
     * R2 acceptance: live still user at production default config and window.
     * Sigma in {1,2,4}, drift in {0,1.5}, 200 trials each: 0 FROZEN, 0 LOOP_REPLAY.
     */
    @Test
    public void liveNoiseSettingsProduceZeroFrozenAndZeroLoopReplay() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        int K = cfg.loopSequenceLengthK();
        // Assert the production default window is large enough for the loop branch to be reachable
        assertTrue(window >= 2 * K + 1,
                "Production default window (" + window + ") must be >= 2*K+1 (" + (2 * K + 1) + ")");

        double[] sigmas = {1.0, 2.0, 4.0};
        double[] drifts = {0.0, 1.5};

        for (double sigma : sigmas) {
            for (double drift : drifts) {
                Random rng = new Random(SEED + (long) (sigma * 100 + drift * 10));
                int frozenCount = 0;
                int loopCount = 0;

                for (int trial = 0; trial < TRIALS; trial++) {
                    FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
                    for (int frameIdx = 0; frameIdx < window; frameIdx++) {
                        FrameFingerprint fp = generateSyntheticFrame(frameIdx, sigma, drift, rng);
                        Optional<IntegrityViolation> violation = monitor.addFrame(fp);
                        if (violation.isPresent()) {
                            if (violation.get() == IntegrityViolation.FROZEN) {
                                frozenCount++;
                            } else if (violation.get() == IntegrityViolation.LOOP_REPLAY) {
                                loopCount++;
                            }
                        }
                    }
                }

                assertEquals(0, frozenCount,
                        String.format("Live noise setting (sigma=%.1f, drift=%.1f) must yield 0 FROZEN violations in %d trials at production default window %d",
                                sigma, drift, TRIALS, window));
                assertEquals(0, loopCount,
                        String.format("Live noise setting (sigma=%.1f, drift=%.1f) must yield 0 LOOP_REPLAY violations in %d trials at production default window %d",
                                sigma, drift, TRIALS, window));
            }
        }
    }

    /**
     * R2 acceptance: live still user at window 15 and window 30 with K=5: 0 flags.
     */
    @Test
    public void liveNoiseAtWindow15And30ProduceZeroFlags() {
        int[] windows = {15, 30};
        double[] sigmas = {1.0, 2.0, 4.0};
        double[] drifts = {0.0, 1.5};

        for (int window : windows) {
            LivenessConfig cfg = LivenessConfig.builder()
                    .frameFreezePHashWindowSize(window)
                    .loopSequenceLengthK(5)
                    .build();
            for (double sigma : sigmas) {
                for (double drift : drifts) {
                    Random rng = new Random(SEED + window * 1000 + (long) (sigma * 100 + drift * 10));
                    int frozenCount = 0;
                    int loopCount = 0;

                    for (int trial = 0; trial < TRIALS; trial++) {
                        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
                        for (int frameIdx = 0; frameIdx < window; frameIdx++) {
                            FrameFingerprint fp = generateSyntheticFrame(frameIdx, sigma, drift, rng);
                            Optional<IntegrityViolation> violation = monitor.addFrame(fp);
                            if (violation.isPresent()) {
                                if (violation.get() == IntegrityViolation.FROZEN) frozenCount++;
                                else if (violation.get() == IntegrityViolation.LOOP_REPLAY) loopCount++;
                            }
                        }
                    }

                    assertEquals(0, frozenCount,
                            String.format("Window %d: sigma=%.1f, drift=%.1f must yield 0 FROZEN in %d trials",
                                    window, sigma, drift, TRIALS));
                    assertEquals(0, loopCount,
                            String.format("Window %d: sigma=%.1f, drift=%.1f must yield 0 LOOP_REPLAY in %d trials",
                                    window, sigma, drift, TRIALS));
                }
            }
        }
    }

    @Test
    public void byteIdenticalRepeatedFramesTriggerFrozen() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
        // sigma = 0, drift = 0 generates byte-identical frames (same content digest)
        Random rng = new Random(SEED);
        Optional<IntegrityViolation> lastViolation = Optional.empty();
        for (int frameIdx = 0; frameIdx < window; frameIdx++) {
            FrameFingerprint fp = generateSyntheticFrame(frameIdx, 0.0, 0.0, rng);
            Optional<IntegrityViolation> v = monitor.addFrame(fp);
            if (v.isPresent()) {
                lastViolation = v;
            }
        }

        assertTrue(lastViolation.isPresent(), "Byte-identical repeated frames must be detected as FROZEN");
        assertEquals(IntegrityViolation.FROZEN, lastViolation.get());
    }

    /**
     * R2 acceptance: noisy live frames must never be FROZEN.
     */
    @Test
    public void noisyLiveFramesNeverFrozen() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        double[] sigmas = {1.0, 2.0, 4.0};

        for (double sigma : sigmas) {
            Random rng = new Random(SEED + (long) (sigma * 100));
            int frozenCount = 0;
            for (int trial = 0; trial < TRIALS; trial++) {
                FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
                for (int frameIdx = 0; frameIdx < window; frameIdx++) {
                    FrameFingerprint fp = generateSyntheticFrame(frameIdx, sigma, 0.0, rng);
                    Optional<IntegrityViolation> v = monitor.addFrame(fp);
                    if (v.isPresent() && v.get() == IntegrityViolation.FROZEN) {
                        frozenCount++;
                    }
                }
            }
            assertEquals(0, frozenCount,
                    String.format("sigma=%.1f: noisy live frames must never be FROZEN in %d trials", sigma, TRIALS));
        }
    }

    @Test
    public void freshNoiseRepeatedClipDocumentedAsNotDetectableByHashAlone() {
        // Documented honest limitation (Part 2 X13):
        // When a video loop replay is filmed through a physical camera, each repeat incurs fresh
        // sensor noise. Under DCT pHash, fresh noise shifts frequency bins such that the pairwise
        // Hamming distance between repeats exceeds the loop threshold.
        // Therefore, a loop replay with fresh noise per repeat is NOT detectable by pHash alone.
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        Random rng = new Random(SEED);
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);

        int detectedCount = 0;
        for (int frameIdx = 0; frameIdx < window; frameIdx++) {
            // Clip of period 5 with fresh noise sigma=2.0 on each frame
            int baseIdx = frameIdx % 5;
            FrameFingerprint fp = generateSyntheticFrame(baseIdx, 2.0, baseIdx * 1.5, rng);
            Optional<IntegrityViolation> v = monitor.addFrame(fp);
            if (v.isPresent() && v.get() == IntegrityViolation.LOOP_REPLAY) {
                detectedCount++;
            }
        }

        // Honest statement: hash alone does not catch loop replay with fresh sensor noise per repeat.
        assertEquals(0, detectedCount, "Repeated clip with fresh sensor noise per repeat is not detectable by hash alone");
    }
}
