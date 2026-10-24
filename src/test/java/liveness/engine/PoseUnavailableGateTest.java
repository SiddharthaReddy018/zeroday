package liveness.engine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PoseUnavailableGateTest {

    private static QualityGate gate() {
        return new QualityGate(50.0, 40.0, 220.0, 100, 30.0, 20.0);
    }

    private static QualityGate.RawFrameData frame(double yaw, double pitch) {
        return new QualityGate.RawFrameData(150.0, 120.0, 150, 150, yaw, pitch, 0.0, 1, 640, 480);
    }

    @Test
    public void maxValueYawIsUnavailableNotExceeded() {
        QualityGate.QualityResult r = gate().evaluate(frame(Double.MAX_VALUE, 0.0));
        Assertions.assertFalse(r.passed());
        Assertions.assertEquals("guide.pose.unavailable", r.failureReason());
    }

    @Test
    public void maxValuePitchIsUnavailableNotExceeded() {
        QualityGate.QualityResult r = gate().evaluate(frame(0.0, Double.MAX_VALUE));
        Assertions.assertFalse(r.passed());
        Assertions.assertEquals("guide.pose.unavailable", r.failureReason());
    }

    @Test
    public void nanYawFailsClosed() {
        QualityGate.QualityResult r = gate().evaluate(frame(Double.NaN, 0.0));
        Assertions.assertFalse(r.passed());
        Assertions.assertEquals("guide.pose.unavailable", r.failureReason());
    }

    @Test
    public void nanPitchFailsClosed() {
        QualityGate.QualityResult r = gate().evaluate(frame(0.0, Double.NaN));
        Assertions.assertFalse(r.passed());
        Assertions.assertEquals("guide.pose.unavailable", r.failureReason());
    }

    @Test
    public void realExceededYawKeepsItsReason() {
        QualityGate.QualityResult r = gate().evaluate(frame(35.0, 10.0));
        Assertions.assertFalse(r.passed());
        Assertions.assertEquals("guide.pose.yaw_exceeded", r.failureReason());
    }

    @Test
    public void inRangePoseStillPasses() {
        Assertions.assertTrue(gate().evaluate(frame(25.0, 10.0)).passed());
    }
}
