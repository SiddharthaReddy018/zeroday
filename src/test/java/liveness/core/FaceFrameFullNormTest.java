package liveness.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FaceFrameFullNormTest {
    @Test
    void defaultsToUnavailable() {
        FaceFrame f = new FaceFrame(FrameQuality.GOOD, "t", 1);
        assertEquals(-1.0, f.fullFrameNorm(), 0.0);
    }

    @Test
    void carriesValueFromNewConstructor() {
        FaceFrame f = new FaceFrame(FrameQuality.GOOD, "t", 1, 1L, 0, 640, 480, null,
                0, 0, 0, 0, 0.0, 0.0, 0.0, 1, 0.0, 0.0, null, 0L, null, null, 0.123);
        assertEquals(0.123, f.fullFrameNorm(), 0.0);
    }

    @Test
    void oldConstructorsKeepUnavailable() {
        FaceFrame f = new FaceFrame(FrameQuality.GOOD, "t", 1, 1L, 0, 640, 480, null,
                0, 0, 0, 0, 0.0, 0.0, 0.0, 1, 0.0, 0.0, null, 7L);
        assertEquals(-1.0, f.fullFrameNorm(), 0.0);
    }
}
