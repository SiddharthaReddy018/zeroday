package liveness.android;

import liveness.engine.LandmarkLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeFaceDetectorTest {

    private static final double UNAVAILABLE = CompositeFaceDetector.UNAVAILABLE_POSE_DEGREES;

    private static double[] layout(boolean base, boolean align, double baseValue, double alignValue) {
        double[] lm = new double[LandmarkLayout.FULL_LENGTH];
        java.util.Arrays.fill(lm, Double.NaN);
        if (base) for (int i = 0; i < LandmarkLayout.BASE_LENGTH; i++) lm[i] = baseValue + i;
        if (align) for (int i = 0; i < 10; i++) lm[LandmarkLayout.ALIGN5 + i] = alignValue + i;
        return lm;
    }

    private static FaceDetector.Observation obs(int count, int x, int y, int w, int h,
                                                double yaw, double[] lm) {
        return new FaceDetector.Observation(count, x, y, w, h, yaw, 2.0, 3.0, lm);
    }

    private static class Fake implements FaceDetector {
        final Observation result;
        final RuntimeException toThrow;
        int calls;
        Fake(Observation result) { this.result = result; this.toThrow = null; }
        Fake(RuntimeException e) { this.result = null; this.toThrow = e; }
        @Override public Observation detect(byte[] rgb, int w, int h) {
            calls++;
            if (toThrow != null) throw toThrow;
            return result;
        }
    }

    private static final byte[] RGB = new byte[640 * 480 * 3];

    private static FaceDetector.Observation yunetOne() {
        return obs(1, 100, 100, 200, 240, Double.MAX_VALUE, layout(false, true, 1000, 500));
    }

    private static FaceDetector.Observation mlkitOne() {
        return obs(1, 110, 90, 190, 250, 7.5, layout(true, true, 10, 9999));
    }

    @Test
    void mergesContoursAndPoseFromContourDetectorButAlignmentFromCounter() {
        CompositeFaceDetector d = new CompositeFaceDetector(new Fake(yunetOne()), new Fake(mlkitOne()));
        FaceDetector.Observation o = d.detect(RGB, 640, 480);
        assertEquals(1, o.faceCount);
        assertEquals(100, o.bboxX);
        assertEquals(240, o.bboxH);
        assertEquals(7.5, o.yawDeg, 1e-9);
        assertEquals(2.0, o.pitchDeg, 1e-9);
        assertEquals(3.0, o.rollDeg, 1e-9);
        assertTrue(LandmarkLayout.hasBase(o.landmarks));
        assertTrue(LandmarkLayout.hasAlign5(o.landmarks));
        assertEquals(10.0, o.landmarks[0], 1e-9, "contour block comes from the contour detector");
        assertEquals(41.0, o.landmarks[31], 1e-9);
        assertEquals(500.0, o.landmarks[LandmarkLayout.ALIGN5], 1e-9,
                "alignment points must come from the counter, never from the contour detector");
        assertEquals(509.0, o.landmarks[LandmarkLayout.ALIGN5 + 9], 1e-9);
    }

    @Test
    void counterSeesNoFaceReturnsNoneWithoutRunningContourDetector() {
        Fake contour = new Fake(mlkitOne());
        CompositeFaceDetector d = new CompositeFaceDetector(new Fake(FaceDetector.Observation.NONE), contour);
        assertEquals(0, d.detect(RGB, 640, 480).faceCount);
        assertEquals(0, contour.calls);
    }

    @Test
    void counterSeesTwoFacesRejectsWithoutRunningContourDetector() {
        Fake contour = new Fake(mlkitOne());
        FaceDetector.Observation two = obs(2, 100, 100, 200, 240, Double.MAX_VALUE,
                layout(false, true, 0, 5));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(two), contour).detect(RGB, 640, 480);
        assertEquals(2, o.faceCount);
        assertEquals(UNAVAILABLE, o.yawDeg);
        assertNull(o.landmarks);
        assertEquals(0, contour.calls);
    }

    @Test
    void contourDetectorReportingTwoFacesRejectsEvenIfCounterSawOne() {
        FaceDetector.Observation k = obs(2, 110, 90, 190, 250, 1.0, layout(true, true, 10, 0));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(yunetOne()), new Fake(k)).detect(RGB, 640, 480);
        assertEquals(2, o.faceCount);
        assertEquals(UNAVAILABLE, o.pitchDeg);
        assertNull(o.landmarks);
    }

    @Test
    void contourDetectorMissingFaceOrContoursFailsClosed() {
        FaceDetector.Observation none = new CompositeFaceDetector(new Fake(yunetOne()),
                new Fake(FaceDetector.Observation.NONE)).detect(RGB, 640, 480);
        assertEquals(1, none.faceCount);
        assertEquals(UNAVAILABLE, none.yawDeg);
        assertNull(none.landmarks);

        FaceDetector.Observation noContours = obs(1, 110, 90, 190, 250, 4.0, null);
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(yunetOne()),
                new Fake(noContours)).detect(RGB, 640, 480);
        assertEquals(UNAVAILABLE, o.yawDeg);
        assertNull(o.landmarks);

        FaceDetector.Observation nanEye = obs(1, 110, 90, 190, 250, 4.0, layout(false, true, 0, 1));
        assertNull(new CompositeFaceDetector(new Fake(yunetOne()), new Fake(nanEye))
                .detect(RGB, 640, 480).landmarks);
    }

    @Test
    void contourFaceThatIsNotTheCountersFaceFailsClosed() {
        FaceDetector.Observation elsewhere = obs(1, 400, 300, 150, 150, 0.0, layout(true, true, 10, 0));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(yunetOne()),
                new Fake(elsewhere)).detect(RGB, 640, 480);
        assertEquals(UNAVAILABLE, o.yawDeg);
        assertNull(o.landmarks);
    }

    @Test
    void containedSmallBoxStillFailsWhenCounterCentreIsOutsideIt() {
        FaceDetector.Observation tiny = obs(1, 105, 95, 20, 20, 0.0, layout(true, true, 10, 0));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(yunetOne()),
                new Fake(tiny)).detect(RGB, 640, 480);
        assertEquals(UNAVAILABLE, o.yawDeg, "each box centre must lie inside the other box");
        assertNull(o.landmarks);
    }

    @Test
    void counterWithoutFiniteAlignmentPointsFailsClosedAndSkipsContourDetector() {
        Fake contour = new Fake(mlkitOne());
        FaceDetector.Observation bad = obs(1, 100, 100, 200, 240, Double.MAX_VALUE, layout(false, false, 0, 0));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(bad), contour).detect(RGB, 640, 480);
        assertEquals(UNAVAILABLE, o.yawDeg);
        assertNull(o.landmarks);
        assertEquals(0, contour.calls);
    }

    @Test
    void nonFinitePoseFromContourDetectorFailsClosed() {
        FaceDetector.Observation k = obs(1, 110, 90, 190, 250, Double.NaN, layout(true, true, 10, 0));
        FaceDetector.Observation o = new CompositeFaceDetector(new Fake(yunetOne()), new Fake(k)).detect(RGB, 640, 480);
        assertEquals(UNAVAILABLE, o.yawDeg);
        assertNull(o.landmarks);
    }

    @Test
    void detectorExceptionsPropagateAndAreNeverSwallowed() {
        CompositeFaceDetector a = new CompositeFaceDetector(
                new Fake(new IllegalStateException("yunet")), new Fake(mlkitOne()));
        assertThrows(IllegalStateException.class, () -> a.detect(RGB, 640, 480));
        CompositeFaceDetector b = new CompositeFaceDetector(
                new Fake(yunetOne()), new Fake(new IllegalStateException("mlkit")));
        assertThrows(IllegalStateException.class, () -> b.detect(RGB, 640, 480));
    }

    @Test
    void constructorRejectsNullAndSameInstance() {
        Fake f = new Fake(yunetOne());
        assertThrows(IllegalArgumentException.class, () -> new CompositeFaceDetector(null, f));
        assertThrows(IllegalArgumentException.class, () -> new CompositeFaceDetector(f, null));
        assertThrows(IllegalArgumentException.class, () -> new CompositeFaceDetector(f, f));
    }

    @Test
    void closeClosesBothEvenWhenFirstFails() {
        final int[] closed = {0};
        class C extends Fake implements AutoCloseable {
            final boolean fail;
            C(boolean fail) { super(yunetOne()); this.fail = fail; }
            @Override public void close() throws Exception { closed[0]++; if (fail) throw new Exception("boom"); }
        }
        CompositeFaceDetector d = new CompositeFaceDetector(new C(true), new C(false));
        assertThrows(Exception.class, d::close);
        assertEquals(2, closed[0]);
    }
}
