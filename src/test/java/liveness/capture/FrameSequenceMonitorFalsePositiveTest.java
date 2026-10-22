package liveness.capture;

import liveness.capture.FrameSequenceMonitor.IntegrityViolation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task T1 acceptance test: validates that live still users under realistic camera sensor noise
 * and subpixel drift do not suffer false FROZEN or LOOP_REPLAY attacks.
 */
public class FrameSequenceMonitorFalsePositiveTest {

    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;
    private static final int WINDOW_SIZE = 10;
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

    @Test
    public void liveNoiseSettingsProduceZeroFrozenAndZeroLoopReplay() {
        double[] sigmas = {1.0, 2.0, 4.0};
        double[] drifts = {0.0, 0.5, 1.5};

        for (double sigma : sigmas) {
            for (double drift : drifts) {
                Random rng = new Random(SEED + (long) (sigma * 100 + drift * 10));
                int frozenCount = 0;
                int loopCount = 0;

                for (int trial = 0; trial < TRIALS; trial++) {
                    FrameSequenceMonitor monitor = new FrameSequenceMonitor(WINDOW_SIZE, 33);
                    for (int frameIdx = 0; frameIdx < WINDOW_SIZE; frameIdx++) {
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
                        String.format("Live noise setting (sigma=%.1f, drift=%.1f) must yield 0 FROZEN violations in %d trials",
                                sigma, drift, TRIALS));
                assertEquals(0, loopCount,
                        String.format("Live noise setting (sigma=%.1f, drift=%.1f) must yield 0 LOOP_REPLAY violations in %d trials",
                                sigma, drift, TRIALS));
            }
        }
    }

    @Test
    public void byteIdenticalRepeatedFramesTriggerFrozen() {
        Random rng = new Random(SEED);
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(WINDOW_SIZE, 33);
        // sigma = 0, drift = 0 generates byte-identical frames
        Optional<IntegrityViolation> lastViolation = Optional.empty();
        for (int frameIdx = 0; frameIdx < WINDOW_SIZE; frameIdx++) {
            FrameFingerprint fp = generateSyntheticFrame(frameIdx, 0.0, 0.0, rng);
            Optional<IntegrityViolation> v = monitor.addFrame(fp);
            if (v.isPresent()) {
                lastViolation = v;
            }
        }

        assertTrue(lastViolation.isPresent(), "Byte-identical repeated frames must be detected as FROZEN");
        assertEquals(IntegrityViolation.FROZEN, lastViolation.get());
    }

    @Test
    public void repeatedKFrameSequenceTriggersLoopReplay() {
        Random rng = new Random(SEED);
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(20, 33);

        // Pre-generate 5 distinct frames
        FrameFingerprint[] clip = new FrameFingerprint[5];
        for (int i = 0; i < 5; i++) {
            clip[i] = generateSyntheticFrame(i, 0.0, (i + 1) * 2.0, rng);
        }

        // Feed first 5 frames
        for (int i = 0; i < 5; i++) {
            monitor.addFrame(new FrameFingerprint(clip[i].getPHash(), 1000L + i * 33L, i + 1, WIDTH, clip[i].getContentDigest()));
        }

        // Intervening frame ensuring segments are non-adjacent
        FrameFingerprint intervening = generateSyntheticFrame(99, 0.0, 99.0, rng);
        monitor.addFrame(new FrameFingerprint(intervening.getPHash(), 1165L, 6, WIDTH, intervening.getContentDigest()));

        // Replay the 5 frames
        Optional<IntegrityViolation> lastViolation = Optional.empty();
        for (int i = 0; i < 5; i++) {
            Optional<IntegrityViolation> v = monitor.addFrame(
                    new FrameFingerprint(clip[i].getPHash(), 1198L + i * 33L, 7 + i, WIDTH, clip[i].getContentDigest()));
            if (v.isPresent()) {
                lastViolation = v;
            }
        }

        assertTrue(lastViolation.isPresent(), "Identical repeated 5-frame sequence must trigger LOOP_REPLAY");
        assertEquals(IntegrityViolation.LOOP_REPLAY, lastViolation.get());
    }


    @Test
    public void freshNoiseRepeatedClipDocumentedAsNotDetectableByHashAlone() {
        // Documented honest limitation (Section 0 / Part 2 X13):
        // When a video loop replay is filmed through a physical camera, each repeat incurs fresh independent
        // sensor noise (sigma >= 1). Under DCT pHash, fresh noise on a 32x32 frame shifts frequency bins such that
        // the pairwise Hamming distance between repeats exceeds the loop threshold (dist >= 18).
        // Therefore, a loop replay with fresh noise per repeat is NOT detectable by pHash alone.
        Random rng = new Random(SEED);
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(WINDOW_SIZE, 33);

        int detectedCount = 0;
        for (int frameIdx = 0; frameIdx < 10; frameIdx++) {
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
