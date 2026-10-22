package liveness.engine;

import static org.junit.jupiter.api.Assertions.*;

import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;

class LandmarkLayoutTest {

    /** Eye: width 20, height 6 on both verticals -> EAR = (6+6)/(2*20) = 0.30 when open. */
    private static void eye(double[] a, int off, double x0, double y0, double h) {
        a[off] = x0;       a[off + 1] = y0;           // p1 outer
        a[off + 2] = x0 + 6;  a[off + 3] = y0 - h / 2; // p2 upper
        a[off + 4] = x0 + 14; a[off + 5] = y0 - h / 2; // p3 upper
        a[off + 6] = x0 + 20; a[off + 7] = y0;         // p4 inner
        a[off + 8] = x0 + 14; a[off + 9] = y0 + h / 2; // p5 lower
        a[off + 10] = x0 + 6; a[off + 11] = y0 + h / 2; // p6 lower
    }

    static double[] face(double eyeH, double mouthOpen) {
        double[] a = new double[LandmarkLayout.FULL_LENGTH];
        eye(a, LandmarkLayout.LEFT_EYE, 100, 100, eyeH);
        eye(a, LandmarkLayout.RIGHT_EYE, 160, 100, eyeH);
        int m = LandmarkLayout.MOUTH;
        a[m] = 120;     a[m + 1] = 200;               // left corner
        a[m + 2] = 140; a[m + 3] = 200 - mouthOpen / 2; // top
        a[m + 4] = 160; a[m + 5] = 200;               // right corner
        a[m + 6] = 140; a[m + 7] = 200 + mouthOpen / 2; // bottom
        for (int i = 0; i < 10; i++) a[LandmarkLayout.ALIGN5 + i] = 50 + i;
        return a;
    }

    @Test void openEyesGiveExpectedMeanEar() {
        assertEquals(0.30, LandmarkLayout.meanEar(face(6, 4)), 1e-9);
    }

    @Test void closedEyesGiveLowEar() {
        assertTrue(LandmarkLayout.meanEar(face(0.5, 4)) < 0.05);
    }

    @Test void marMatchesMouthPoints() {
        assertEquals(LandmarkDetector.computeMAR(new double[]{120, 200, 140, 198, 160, 200, 140, 202}),
                LandmarkLayout.mar(face(6, 4)), 1e-9);
    }

    @Test void legacyTwelveValueIsSingleEye() {
        double[] f = face(6, 4);
        double[] one = new double[12];
        System.arraycopy(f, 0, one, 0, 12);
        assertEquals(0.30, LandmarkLayout.meanEar(one), 1e-9);
        assertTrue(Double.isNaN(LandmarkLayout.mar(one)));
    }

    @Test void shortOrNullOrNanIsUndecidable() {
        assertTrue(Double.isNaN(LandmarkLayout.meanEar(null)));
        assertTrue(Double.isNaN(LandmarkLayout.meanEar(new double[20])));
        double[] f = face(6, 4);
        f[13] = Double.NaN;
        assertTrue(Double.isNaN(LandmarkLayout.meanEar(f)));
    }

    @Test void zeroWidthEyeIsUndecidable() {
        double[] f = face(6, 4);
        for (int i = 0; i < 12; i++) f[i] = 5; // collapsed eye
        assertTrue(Double.isNaN(LandmarkLayout.meanEar(f)));
    }

    @Test void align5OnlyWhenFullLength() {
        assertNotNull(LandmarkLayout.align5(face(6, 4)));
        double[] base = new double[LandmarkLayout.BASE_LENGTH];
        System.arraycopy(face(6, 4), 0, base, 0, base.length);
        assertNull(LandmarkLayout.align5(base));
        assertEquals(50, LandmarkLayout.align5(face(6, 4))[0], 0);
    }

    @Test void detectorBlinkNeedsBothEyesClosed() {
        LivenessConfig c = LivenessConfig.defaults();
        assertTrue(ChallengeActionDetector.detect("BLINK", face(0.5, 4), 0, 0, c));
        assertFalse(ChallengeActionDetector.detect("BLINK", face(6, 4), 0, 0, c));
        // one eye closed, one open -> mean above threshold -> no blink
        double[] f = face(6, 4);
        double[] closed = face(0.5, 4);
        System.arraycopy(closed, 0, f, 0, 12);
        assertFalse(ChallengeActionDetector.detect("BLINK", f, 0, 0, c));
    }

    @Test void detectorSmileUsesMouthSlot() {
        LivenessConfig c = LivenessConfig.defaults();
        assertFalse(ChallengeActionDetector.detect("SMILE", face(6, 2), 0, 0, c));
        assertTrue(ChallengeActionDetector.detect("SMILE", face(6, 40), 0, 0, c));
        assertFalse(ChallengeActionDetector.detect("SMILE", new double[12], 0, 0, c));
    }

    @Test void detectorFailsClosed() {
        LivenessConfig c = LivenessConfig.defaults();
        assertFalse(ChallengeActionDetector.detect("BLINK", null, 0, 0, c));
        assertFalse(ChallengeActionDetector.detect("NOPE", face(6, 4), 0, 0, c));
        assertFalse(ChallengeActionDetector.detect(null, face(6, 4), 0, 0, c));
    }

    @Test void poseActionsUseConfigThresholds() {
        LivenessConfig c = LivenessConfig.defaults();
        assertTrue(ChallengeActionDetector.detect("LOOK_DOWN", null, 0, 90, c));
        assertTrue(ChallengeActionDetector.detect("LOOK_UP", null, 0, -90, c));
        assertFalse(ChallengeActionDetector.detect("LOOK_UP", null, 0, 0, c));
    }
}
