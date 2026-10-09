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
}
