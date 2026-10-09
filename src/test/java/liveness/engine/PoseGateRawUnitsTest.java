package liveness.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class PoseGateRawUnitsTest {

    @Test
    public void testValidFramePasses() {
        QualityGate gate = new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
        QualityGate.RawFrameData frame = new QualityGate.RawFrameData(150.0, 120.0, 150, 150, 25.0, 10.0, 0.0, 1, 640, 480);
        QualityGate.QualityResult result = gate.evaluate(frame);
        Assertions.assertTrue(result.passed());
    }

    @Test
    public void testYawExceeded() {
        QualityGate gate = new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
        QualityGate.RawFrameData frame = new QualityGate.RawFrameData(150.0, 120.0, 150, 150, 35.0, 10.0, 0.0, 1, 640, 480);
        QualityGate.QualityResult result = gate.evaluate(frame);
        Assertions.assertFalse(result.passed());
        Assertions.assertEquals("guide.pose.yaw_exceeded", result.failureReason());
    }

    @Test
    public void testLowBlurVariance() {
        QualityGate gate = new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
        QualityGate.RawFrameData frame = new QualityGate.RawFrameData(20.0, 120.0, 150, 150, 0.0, 0.0, 0.0, 1, 640, 480);
        QualityGate.QualityResult result = gate.evaluate(frame);
        Assertions.assertFalse(result.passed());
    }

    @Test
    public void testFaceCountNotOne() {
        QualityGate gate = new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
        QualityGate.RawFrameData frame = new QualityGate.RawFrameData(150.0, 120.0, 150, 150, 0.0, 0.0, 0.0, 2, 640, 480);
        QualityGate.QualityResult result = gate.evaluate(frame);
        Assertions.assertFalse(result.passed());
    }
}
