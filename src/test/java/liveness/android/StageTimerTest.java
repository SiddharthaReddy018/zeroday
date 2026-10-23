package liveness.android;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageTimerTest {

    @AfterEach
    void off() { StageTimer.disable(); }

    @Test
    void disabledRecordsNothingAndStartReturnsZero() {
        StageTimer.enable(4);
        StageTimer.disable();
        assertEquals(0L, StageTimer.start());
        StageTimer.stop(StageTimer.Stage.DETECT, StageTimer.start());
        StageTimer.record(StageTimer.Stage.DETECT, 5_000_000L);
        assertEquals(0, StageTimer.count(StageTimer.Stage.DETECT));
    }

    @Test
    void recordsInOrderAndConvertsToMillis() {
        StageTimer.enable(8);
        StageTimer.record(StageTimer.Stage.PAD_INFER, 2_000_000L);
        StageTimer.record(StageTimer.Stage.PAD_INFER, 3_500_000L);
        assertArrayEquals(new double[]{2.0, 3.5}, StageTimer.samplesMs(StageTimer.Stage.PAD_INFER), 1e-12);
        assertEquals(0, StageTimer.count(StageTimer.Stage.QUALITY));
    }

    @Test
    void startStopMeasuresElapsedTime() throws Exception {
        StageTimer.enable(2);
        long t = StageTimer.start();
        assertNotEquals(0L, t);
        Thread.sleep(5);
        StageTimer.stop(StageTimer.Stage.PREPROCESS, t);
        double ms = StageTimer.samplesMs(StageTimer.Stage.PREPROCESS)[0];
        assertTrue(ms >= 4.0 && ms < 1000.0, "elapsed " + ms);
    }

    @Test
    void overflowIsCountedNotStored() {
        StageTimer.enable(2);
        for (int i = 0; i < 5; i++) StageTimer.record(StageTimer.Stage.INTEGRITY, 1_000_000L);
        assertEquals(2, StageTimer.count(StageTimer.Stage.INTEGRITY));
        assertEquals(3, StageTimer.overflow(StageTimer.Stage.INTEGRITY));
    }

    @Test
    void enableResetsEarlierSamples() {
        StageTimer.enable(4);
        StageTimer.record(StageTimer.Stage.QUALITY, 1_000_000L);
        StageTimer.enable(4);
        assertEquals(0, StageTimer.count(StageTimer.Stage.QUALITY));
        assertEquals(0, StageTimer.overflow(StageTimer.Stage.QUALITY));
    }
}
