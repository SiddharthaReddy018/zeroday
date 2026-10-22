package liveness.capture;

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
        Optional<FrameSequenceMonitor.IntegrityViolation> v = monitor.addFrame(new FrameFingerprint(100L, 1090L, 4, 640));
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
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(10, 30);
        int w = 64, h = 64;

        byte[] frameA = new byte[w * h];
        byte[] frameB = new byte[w * h];
        byte[] frameC = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            frameA[i] = (byte) ((i * 7) % 256);
            frameB[i] = (byte) ((i * 19 + 50) % 256);
            frameC[i] = (byte) ((i * 31 + 100) % 256);
        }

        // Frame A with slight sensor noise
        byte[] frameANoisy = java.util.Arrays.copyOf(frameA, frameA.length);
        for (int i = 0; i < frameANoisy.length; i += 20) {
            frameANoisy[i] = (byte) Math.min(255, (frameANoisy[i] & 0xFF) + 3);
        }

        long hashA = FrameFingerprint.computePHash(frameA, w, h);
        long hashB = FrameFingerprint.computePHash(frameB, w, h);
        long hashC = FrameFingerprint.computePHash(frameC, w, h);
        long hashANoisy = FrameFingerprint.computePHash(frameANoisy, w, h);

        monitor.addFrame(new FrameFingerprint(hashA, 1000L, 1, w));
        monitor.addFrame(new FrameFingerprint(hashB, 1030L, 2, w));
        monitor.addFrame(new FrameFingerprint(hashC, 1060L, 3, w));
        Optional<FrameSequenceMonitor.IntegrityViolation> v =
                monitor.addFrame(new FrameFingerprint(hashANoisy, 1090L, 4, w));

        Assertions.assertTrue(v.isPresent(), "Noisy loop must be detected as integrity violation");
        Assertions.assertEquals(FrameSequenceMonitor.IntegrityViolation.LOOP_REPLAY, v.get());
    }
}
