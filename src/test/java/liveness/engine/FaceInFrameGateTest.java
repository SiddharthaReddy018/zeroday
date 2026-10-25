package liveness.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceInFrameGateTest {
    private static QualityGate gate() {
        return new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
    }

    private static QualityGate.RawFrameData box(int x, int y, int w, int h) {
        return new QualityGate.RawFrameData(150.0, 120.0, w, h, 0.0, 0.0, 0.0, 1, 640, 480, x, y);
    }

    @Test
    void centeredBoxPasses() {
        assertTrue(gate().evaluate(box(245, 165, 150, 150)).passed());
    }

    @Test
    void leftEdgeCropFails() {
        assertEquals("guide.face.partial", gate().evaluate(box(0, 165, 150, 150)).failureReason());
    }

    @Test
    void rightEdgeCropFails() {
        assertEquals("guide.face.partial", gate().evaluate(box(520, 165, 150, 150)).failureReason());
    }

    @Test
    void topAndBottomCropFail() {
        assertEquals("guide.face.partial", gate().evaluate(box(245, -40, 150, 150)).failureReason());
        assertEquals("guide.face.partial", gate().evaluate(box(245, 400, 150, 150)).failureReason());
    }

    @Test
    void oldConstructorSkipsCheck() {
        QualityGate.RawFrameData f = new QualityGate.RawFrameData(
                150.0, 120.0, 150, 150, 0.0, 0.0, 0.0, 1, 640, 480);
        assertTrue(gate().evaluate(f).passed());
    }
}
