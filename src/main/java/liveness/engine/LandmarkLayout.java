package liveness.engine;

/**
 * Single contract for the flattened landmark array carried by {@code FaceFrame.landmarks()}
 * and {@code FaceDetector.Observation.landmarks}. Values are x,y pairs in raw frame pixels.
 *
 * <pre>
 *  index  0..11  left eye  : p1..p6   (p1 outer corner, p2 upper-outer, p3 upper-inner,
 *                                      p4 inner corner, p5 lower-inner, p6 lower-outer)
 *  index 12..23  right eye : p1..p6   (same convention)
 *  index 24..31  mouth     : left corner, upper-lip centre, right corner, lower-lip centre
 *  index 32..41  OPTIONAL alignment points for SFace (OpenCV/YuNet order):
 *                subject-right eye centre (image left), subject-left eye centre (image right),
 *                nose tip, subject-right mouth corner (image left), subject-left mouth corner (image right)
 * </pre>
 * "Left/right eye" in the first block are only names; EAR is averaged over both.
 * Arrays shorter than {@link #BASE_LENGTH} carry no usable layout (except the legacy
 * 12-value single-eye form, accepted for BLINK only).
 */
public final class LandmarkLayout {
    private LandmarkLayout() {}

    public static final int LEFT_EYE = 0;
    public static final int RIGHT_EYE = 12;
    public static final int MOUTH = 24;
    public static final int ALIGN5 = 32;
    public static final int BASE_LENGTH = 32;
    public static final int FULL_LENGTH = 42;
    /** Legacy form: one eye, 6 points. */
    public static final int LEGACY_SINGLE_EYE_LENGTH = 12;

    public static boolean hasBase(double[] lm) {
        return lm != null && lm.length >= BASE_LENGTH && allFinite(lm, 0, BASE_LENGTH);
    }

    public static boolean hasAlign5(double[] lm) {
        return lm != null && lm.length >= FULL_LENGTH && allFinite(lm, ALIGN5, 10);
    }

    /** The 10 alignment values (5 points) or null when absent. */
    public static double[] align5(double[] lm) {
        if (!hasAlign5(lm)) return null;
        double[] out = new double[10];
        System.arraycopy(lm, ALIGN5, out, 0, 10);
        return out;
    }

    /**
     * Mean eye-aspect ratio over both eyes; NaN when the layout is missing or an eye is degenerate
     * (zero width). Legacy 12-value arrays are treated as one eye.
     */
    public static double meanEar(double[] lm) {
        if (lm == null) return Double.NaN;
        if (lm.length == LEGACY_SINGLE_EYE_LENGTH) {
            return earOf(lm, 0);
        }
        if (!hasBase(lm)) return Double.NaN;
        double l = earOf(lm, LEFT_EYE);
        double r = earOf(lm, RIGHT_EYE);
        if (Double.isNaN(l) || Double.isNaN(r)) return Double.NaN;
        return (l + r) / 2.0;
    }

    /**
     * Larger of the two eyes' EAR (both eyes must be closed for this to be low). NaN when undecidable.
     * Legacy 12-value arrays are treated as one eye.
     */
    public static double maxEar(double[] lm) {
        if (lm == null) return Double.NaN;
        if (lm.length == LEGACY_SINGLE_EYE_LENGTH) return earOf(lm, 0);
        if (!hasBase(lm)) return Double.NaN;
        double l = earOf(lm, LEFT_EYE);
        double r = earOf(lm, RIGHT_EYE);
        if (Double.isNaN(l) || Double.isNaN(r)) return Double.NaN;
        return Math.max(l, r);
    }

    /** Mouth aspect ratio from the 4 mouth points; NaN when the base layout is missing. */
    public static double mar(double[] lm) {
        if (!hasBase(lm)) return Double.NaN;
        double[] m = new double[8];
        System.arraycopy(lm, MOUTH, m, 0, 8);
        double hDist = Math.hypot(m[4] - m[0], m[5] - m[1]);
        if (hDist < 1e-6) return Double.NaN;
        return LandmarkDetector.computeMAR(m);
    }

    private static double earOf(double[] lm, int off) {
        double[] e = new double[12];
        System.arraycopy(lm, off, e, 0, 12);
        for (double v : e) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return Double.NaN;
        }
        double width = Math.hypot(e[6] - e[0], e[7] - e[1]);
        if (width < 1e-6) return Double.NaN;
        return LandmarkDetector.computeEAR(e);
    }

    private static boolean allFinite(double[] a, int off, int n) {
        for (int i = off; i < off + n; i++) {
            if (Double.isNaN(a[i]) || Double.isInfinite(a[i])) return false;
        }
        return true;
    }
}
