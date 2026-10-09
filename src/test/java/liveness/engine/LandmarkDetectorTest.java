package liveness.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class LandmarkDetectorTest {

    @Test
    public void testComputeEAR() {
        double[] eye = { 0,0, 1,1, 2,1, 3,0, 2,-1, 1,-1 };
        double ear = LandmarkDetector.computeEAR(eye);
        Assertions.assertTrue(ear >= 0);
    }

    @Test
    public void testComputeMAR() {
        double[] mouth = { 0,0, 1,1, 2,1, 3,0, 2,-1, 1,-1, 4,0, 5,0 };
        double mar = LandmarkDetector.computeMAR(mouth);
        Assertions.assertTrue(mar >= 0);
    }

    @Test
    public void testDetectBlink() {
        Assertions.assertTrue(LandmarkDetector.detectBlink(0.15, 0.20));
        Assertions.assertFalse(LandmarkDetector.detectBlink(0.25, 0.20));
    }

    @Test
    public void testDetectSmile() {
        Assertions.assertTrue(LandmarkDetector.detectSmile(0.60, 0.50));
        Assertions.assertFalse(LandmarkDetector.detectSmile(0.40, 0.50));
    }

    @Test
    public void testDetectTurn() {
        Assertions.assertTrue(LandmarkDetector.detectTurn(25.0, 20.0, "right"));
        Assertions.assertFalse(LandmarkDetector.detectTurn(15.0, 20.0, "right"));
    }
}
