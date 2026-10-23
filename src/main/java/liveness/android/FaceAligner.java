package liveness.android;

/**
 * 5-point face alignment for SFace: a least-squares similarity transform (scale, rotation,
 * translation; no reflection) mapping the detected landmarks onto the ArcFace 112x112 template,
 * then bilinear resampling with a zero (black) border. This is the same operation as OpenCV
 * {@code FaceRecognizerSF.alignCrop}. Pure Java, no native dependencies.
 *
 * <p>Point order (OpenCV/YuNet): subject-right eye (image left), subject-left eye (image right),
 * nose tip, subject-right mouth corner (image left), subject-left mouth corner (image right).
 */
public final class FaceAligner {
    private FaceAligner() {}

    public static final int OUT = 112;

    /** ArcFace template used by OpenCV alignCrop, x,y pairs. */
    public static final double[] TEMPLATE = {
            38.2946, 51.6963,
            73.5318, 51.5014,
            56.0252, 71.7366,
            41.5493, 92.3655,
            70.7299, 92.2041
    };

    /** Similarity transform src -> dst as dst = s*R*(src - mS) + mD. Returns {s, cos, sin, mSx, mSy, mDx, mDy}. */
    static double[] fit(double[] src) {
        double msx = 0, msy = 0, mdx = 0, mdy = 0;
        for (int i = 0; i < 5; i++) {
            msx += src[2 * i]; msy += src[2 * i + 1];
            mdx += TEMPLATE[2 * i]; mdy += TEMPLATE[2 * i + 1];
        }
        msx /= 5; msy /= 5; mdx /= 5; mdy /= 5;
        double a = 0, b = 0, var = 0;
        for (int i = 0; i < 5; i++) {
            double xs = src[2 * i] - msx, ys = src[2 * i + 1] - msy;
            double xd = TEMPLATE[2 * i] - mdx, yd = TEMPLATE[2 * i + 1] - mdy;
            a += xs * xd + ys * yd;
            b += xs * yd - ys * xd;
            var += xs * xs + ys * ys;
        }
        double norm = Math.hypot(a, b);
        if (var < 1e-9 || norm < 1e-9) {
            throw new IllegalArgumentException("Degenerate landmarks: cannot align");
        }
        return new double[]{norm / var, a / norm, b / norm, msx, msy, mdx, mdy};
    }

    /**
     * @param rgb interleaved RGB, length >= w*h*3
     * @param pts 10 values (5 x,y points) in raw frame pixels
     * @return 112x112 interleaved RGB
     */
    public static byte[] alignCrop(byte[] rgb, int w, int h, double[] pts) {
        if (rgb == null || w <= 0 || h <= 0 || (long) rgb.length < (long) w * h * 3) {
            throw new IllegalArgumentException("Invalid RGB buffer for alignment");
        }
        if (pts == null || pts.length < 10) {
            throw new IllegalArgumentException("Need 5 landmark points (10 values)");
        }
        for (int i = 0; i < 10; i++) {
            if (Double.isNaN(pts[i]) || Double.isInfinite(pts[i])) {
                throw new IllegalArgumentException("Non-finite landmark");
            }
        }
        double[] t = fit(pts);
        double s = t[0], c = t[1], sn = t[2];
        double msx = t[3], msy = t[4], mdx = t[5], mdy = t[6];
        byte[] out = new byte[OUT * OUT * 3];
        for (int oy = 0; oy < OUT; oy++) {
            for (int ox = 0; ox < OUT; ox++) {
                double dx = ox - mdx, dy = oy - mdy;
                // src = R^T * d / s + mS
                double sx = (c * dx + sn * dy) / s + msx;
                double sy = (-sn * dx + c * dy) / s + msy;
                int o = (oy * OUT + ox) * 3;
                int x0 = (int) Math.floor(sx), y0 = (int) Math.floor(sy);
                double fx = sx - x0, fy = sy - y0;
                for (int ch = 0; ch < 3; ch++) {
                    double v00 = px(rgb, w, h, x0, y0, ch), v10 = px(rgb, w, h, x0 + 1, y0, ch);
                    double v01 = px(rgb, w, h, x0, y0 + 1, ch), v11 = px(rgb, w, h, x0 + 1, y0 + 1, ch);
                    double v = (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy;
                    out[o + ch] = (byte) Math.max(0, Math.min(255, (int) Math.round(v)));
                }
            }
        }
        return out;
    }

    private static int px(byte[] rgb, int w, int h, int x, int y, int ch) {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0;
        return rgb[(y * w + x) * 3 + ch] & 0xFF;
    }
}
