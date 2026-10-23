package liveness.capture;

import liveness.capture.FrameSequenceMonitor.IntegrityViolation;
import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2/R3 acceptance test: validates loop-attack detection and freeze-attack detection
 * using the PRODUCTION DEFAULT configuration.
 *
 * All tests are deterministic (seeded), synthetic, and do not require a real camera.
 * Probe: synthetic 96x72 scene, 200 trials per setting.
 */
public class FrameSequenceMonitorLoopDetectionTest {

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
     * Generates a frame with a moving pattern (several px displacement per frame)
     * to produce real motion between consecutive frames.
     */
    private static FrameFingerprint generateMovingFrame(
            int frameIdx, double sigma, double dispPx, Random rng) {
        byte[] gray = new byte[WIDTH * HEIGHT];
        double effX = dispPx * frameIdx;
        double effY = dispPx * 0.75 * frameIdx;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double base = 128.0 + 50.0 * Math.sin((x + effX) / 8.0) + 50.0 * Math.cos((y + effY) / 8.0);
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
     * R2 acceptance: looped attack with real motion, 6-frame clip with fresh noise per repeat.
     * The clip consists of 6 frames with displacement of several px per frame.
     * When looped, each repeat receives fresh sensor noise (different pixel digest and noise).
     *
     * Detected in at least 95 percent of 200 trials at production default config.
     */
    @Test
    public void loopedAttackWithMotionDetectedAtProductionDefault() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        int K = cfg.loopSequenceLengthK();
        assertTrue(window >= 2 * K + 1,
                "Production default window must support loop detection");

        Random rng = new Random(SEED);
        int detected = 0;
        int clipLen = 6;

        for (int trial = 0; trial < TRIALS; trial++) {
            FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
            boolean trialDetected = false;

            // Feed 12 frames: 6-frame clip repeated once with fresh noise per repeat
            for (int f = 0; f < 12; f++) {
                int clipIdx = f % clipLen;
                // Fresh noise sigma=1.0 per frame, real motion displacement=5.0 px per frame
                FrameFingerprint fp = generateMovingFrame(clipIdx, 1.0, 5.0, rng);
                Optional<IntegrityViolation> v = monitor.addFrame(
                        new FrameFingerprint(fp.getPHash(), 1000L + f * 33L, f + 1, WIDTH, fp.getContentDigest()));
                if (v.isPresent() && v.get() == IntegrityViolation.LOOP_REPLAY) {
                    trialDetected = true;
                    break;
                }
            }
            if (trialDetected) {
                detected++;
            }
        }

        double detectionRate = (double) detected / TRIALS;
        System.out.println("[R2 acceptance] Looped attack detection rate at production default: "
                + detected + "/" + TRIALS + " = " + String.format("%.4f", detectionRate));
        assertTrue(detectionRate >= 0.95,
                "Looped attack with real motion must be detected in >= 95% of trials. Actual: "
                        + detected + "/" + TRIALS + " = " + String.format("%.4f", detectionRate));
    }

    /**
     * R2 acceptance: byte-identical repeated frames produce FROZEN.
     */
    @Test
    public void byteIdenticalRepeatedFramesTriggerFrozen() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);

        // All frames have the same content
        byte[] gray = new byte[WIDTH * HEIGHT];
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) (i % 128 + 40);
        long pHash = FrameFingerprint.computePHash(gray, WIDTH, HEIGHT);
        byte[] digest = FrameFingerprint.computeContentDigest(gray, WIDTH, HEIGHT);

        Optional<IntegrityViolation> lastViolation = Optional.empty();
        for (int i = 0; i < window; i++) {
            Optional<IntegrityViolation> v = monitor.addFrame(
                    new FrameFingerprint(pHash, 1000L + i * 33L, i + 1, WIDTH, digest));
            if (v.isPresent()) {
                lastViolation = v;
            }
        }

        assertTrue(lastViolation.isPresent(), "Byte-identical repeated frames must be detected as FROZEN");
        assertEquals(IntegrityViolation.FROZEN, lastViolation.get());
    }

    /**
     * R2 acceptance: static scene (all frames near-identical pHash, varying noise)
     * must NEVER be flagged as LOOP_REPLAY, even when the pHash sequence repeats.
     * The motion check ensures static scenes are not loops.
     */
    @Test
    public void staticSceneNeverFlaggedAsLoop() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();

        double[] sigmas = {1.0, 2.0, 4.0};
        double[] drifts = {0.0, 1.5};

        for (double sigma : sigmas) {
            for (double drift : drifts) {
                int loopFlags = 0;
                for (int trial = 0; trial < TRIALS; trial++) {
                    Random rng = new Random(SEED + trial + (long) (sigma * 1000 + drift * 100));
                    FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);
                    for (int frameIdx = 0; frameIdx < window; frameIdx++) {
                        FrameFingerprint fp = generateSyntheticFrame(frameIdx, sigma, drift, rng);
                        Optional<IntegrityViolation> v = monitor.addFrame(fp);
                        if (v.isPresent() && v.get() == IntegrityViolation.LOOP_REPLAY) {
                            loopFlags++;
                        }
                    }
                }
                assertEquals(0, loopFlags,
                        String.format("Static scene (sigma=%.1f, drift=%.1f) must never be LOOP_REPLAY in %d trials",
                                sigma, drift, TRIALS));
            }
        }
    }

    /**
     * R2: If contentDigest is null, freeze detection must NOT fall back to pHash equality.
     */
    @Test
    public void nullContentDigestDoesNotCauseFrozen() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);

        long samePHash = 0x123456789ABCDEF0L;
        for (int i = 0; i < window; i++) {
            // null contentDigest: freeze detection should not fire
            FrameFingerprint fp = new FrameFingerprint(samePHash, 1000L + i * 33L, i + 1, WIDTH, null);
            Optional<IntegrityViolation> v = monitor.addFrame(fp);
            if (v.isPresent()) {
                assertNotEquals(IntegrityViolation.FROZEN, v.get(),
                        "Null contentDigest must not produce FROZEN (not decidable)");
            }
        }
    }

    /**
     * Validates that changing the default config in LivenessConfig changes what the tests exercise.
     */
    @Test
    public void configDrivenWindowIsUsed() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        int window = cfg.frameFreezePHashWindowSize();
        int K = cfg.loopSequenceLengthK();
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(window, 33, cfg);

        assertEquals(window, monitor.getWindowSize(), "Monitor window must match config");
        assertEquals(K, monitor.getLoopK(), "Monitor loopK must match config");
        assertEquals(cfg.freezeConsecutiveFrames(), monitor.getFreezeThreshold(),
                "Monitor freezeThreshold must match config");
        assertEquals(cfg.loopMinSegmentMotionBits(), monitor.getLoopMinSegmentMotionBits(),
                "Monitor loopMinSegmentMotionBits must match config");
    }
}
