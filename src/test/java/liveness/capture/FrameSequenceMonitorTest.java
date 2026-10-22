package liveness.capture;

import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.Optional;

public class FrameSequenceMonitorTest {

    @Test
    public void testNormalSequence() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        Optional<FrameSequenceMonitor.IntegrityViolation> v1 = monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        Optional<FrameSequenceMonitor.IntegrityViolation> v2 = monitor.addFrame(new FrameFingerprint(101L, 1030L, 2, 640));
        Assertions.assertFalse(v1.isPresent());
        Assertions.assertFalse(v2.isPresent());
    }

    @Test
    public void testFrozenFrames() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(5, 30);
        for(int i=0; i<4; i++) {
            monitor.addFrame(new FrameFingerprint(100L, 1000L + i*30, i+1, 640));
        }
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(100L, 1120L, 5, 640));
        Assertions.assertTrue(v.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.FROZEN, v.get());
    }

    @Test
    public void testLoopReplay() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        monitor.addFrame(new FrameFingerprint(200L, 1030L, 2, 640));
        monitor.addFrame(new FrameFingerprint(300L, 1060L, 3, 640));
        Optional<FrameSequenceMonitor.IntegrityViolation> vSingle = monitor.addFrame(new FrameFingerprint(100L, 1090L, 4, 640));
        Assertions.assertFalse(vSingle.isPresent(), "A single close frame must not fire loop replay");

        // Now test sequence repetition of K=5 frames with non-adjacent separation.
        // Use hashes with enough inter-frame motion, and an intervening frame
        // that differs from all segment hashes by more than loopHammingThreshold (6).
        FrameSequenceMonitor seqMonitor = new FrameSequenceMonitor(20, 30);
        long[] hashes = {0x00FFL, 0xFF00L, 0x0F0FL, 0xF0F0L, 0x5555L};
        for (int i = 0; i < 5; i++) {
            seqMonitor.addFrame(new FrameFingerprint(hashes[i], 1000L + i * 30L, i + 1, 640));
        }
        // Intervening frame must be far from all segment hashes
        seqMonitor.addFrame(new FrameFingerprint(0xAAAAAAAAAAAAAAAAL, 1150L, 6, 640));
        for (int i = 0; i < 4; i++) {
            seqMonitor.addFrame(new FrameFingerprint(hashes[i], 1180L + i * 30L, 7 + i, 640));
        }
        Optional<FrameSequenceMonitor.IntegrityViolation> v =
                seqMonitor.addFrame(new FrameFingerprint(hashes[4], 1300L, 11, 640));
        Assertions.assertTrue(v.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.LOOP_REPLAY, v.get());
    }



    @Test
    public void testTimestampGap() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(200L, 1100L, 2, 640));
        Assertions.assertTrue(v.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.TIMESTAMP_GAP, v.get());
    }

    @Test
    public void testTimestampNonMonotonic() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(200L, 950L, 2, 640));
        Assertions.assertTrue(v.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.TIMESTAMP_NONMONOTONIC, v.get());
    }

    @Test
    public void testResolutionChange() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(200L, 1030L, 2, 480));
        Assertions.assertTrue(v.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.RESOLUTION_CHANGE, v.get());
    }

    @Test
    public void testReset() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        monitor.addFrame(new FrameFingerprint(100L, 1000L, 1, 640));
        monitor.reset();
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(100L, 1030L, 2, 640));
        Assertions.assertFalse(v.isPresent());
    }

    @Test
    public void movingFaceWithStaticCornerMustNotBeFrozen() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(5, 30);
        int w = 64, h = 64;
        Optional<FrameSequenceMonitor.IntegrityViolation> lastViolation = Optional.empty();

        // Top-left 16x16 corner is static (all zeros), while the face moves across frames
        for (int frameIdx = 0; frameIdx < 6; frameIdx++) {
            byte[] gray = new byte[w * h];
            // Face moves across the frame
            int faceCenterX = 24 + frameIdx * 5;
            int faceCenterY = 24 + frameIdx * 4;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // Static top-left corner
                    if (x < 16 && y < 16) {
                        gray[y * w + x] = 0;
                    } else {
                        // Moving pattern
                        int distSq = (x - faceCenterX) * (x - faceCenterX) + (y - faceCenterY) * (y - faceCenterY);
                        gray[y * w + x] = (byte) Math.max(0, 200 - distSq);
                    }
                }
            }
            long pHash = FrameFingerprint.computePHash(gray, w, h);
            FrameFingerprint fp = new FrameFingerprint(pHash, 1000L + frameIdx * 30L, frameIdx + 1, w);
            lastViolation = monitor.addFrame(fp);
        }

        Assertions.assertFalse(lastViolation.isPresent(), "Moving face with static corner must not be diagnosed as FROZEN");
    }

    @Test
    public void identicalFramesMustBeFrozen() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(5, 30);
        int w = 64, h = 64;
        byte[] gray = new byte[w * h];
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) (i % 128 + 40);

        long pHash = FrameFingerprint.computePHash(gray, w, h);
        Optional<FrameSequenceMonitor.IntegrityViolation> violation = Optional.empty();
        for (int i = 0; i < 5; i++) {
            violation = monitor.addFrame(new FrameFingerprint(pHash, 1000L + i * 30L, i + 1, w));
        }

        Assertions.assertTrue(violation.isPresent());
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.FROZEN, violation.get());
    }

    @Test
    public void noisyLoopMustMatch() {
        // Use a window large enough for loop detection: 2*K+1 = 11, use 20.
        // Use production default config so loopMinSegmentMotionBits is applied.
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(20, 30, cfg);
        int w = 64, h = 64;

        byte[][] frames = new byte[5][w * h];
        for (int f = 0; f < 5; f++) {
            for (int i = 0; i < w * h; i++) {
                frames[f][i] = (byte) ((i * (f * 7 + 11) + f * 40) % 256);
            }
        }

        // Add 5 initial frames
        for (int f = 0; f < 5; f++) {
            long hash = FrameFingerprint.computePHash(frames[f], w, h);
            monitor.addFrame(new FrameFingerprint(hash, 1000L + f * 30L, f + 1, w));
        }

        // Intervening frame: must differ from all segment frames by > loopHammingThreshold
        // Use a fully inverted-pattern image to maximize distance
        byte[] intervening = new byte[w * h];
        for (int i = 0; i < w * h; i++) intervening[i] = (byte) (255 - (frames[0][i] & 0xFF));
        long interHash = FrameFingerprint.computePHash(intervening, w, h);
        monitor.addFrame(new FrameFingerprint(interHash, 1150L, 6, w));

        // Add 5 replayed frames with slight noise
        Optional<FrameSequenceMonitor.IntegrityViolation> v = Optional.empty();
        for (int f = 0; f < 5; f++) {
            byte[] noisy = java.util.Arrays.copyOf(frames[f], frames[f].length);
            for (int i = 0; i < noisy.length; i += 25) {
                noisy[i] = (byte) Math.min(255, (noisy[i] & 0xFF) + 2);
            }
            long hashNoisy = FrameFingerprint.computePHash(noisy, w, h);
            v = monitor.addFrame(new FrameFingerprint(hashNoisy, 1180L + f * 30L, 7 + f, w));
        }

        Assertions.assertTrue(v.isPresent(), "Noisy loop sequence must be detected as integrity violation");
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.LOOP_REPLAY, v.get());
    }


}
