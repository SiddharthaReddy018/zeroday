package liveness.android;

/**
 * Candidate blur metrics computed on the detector face crop.
 * BENCH / OFFLINE ONLY: not wired into captureFrame or QualityGate.
 * Pure Java, no Android deps.
 *
 * Assumes rgb is interleaved R,G,B bytes, length >= w*h*3 (length is checked; layout of
 * YuvConverter.toRgb was not verified when this was written).
 *
 * Result array indices: LAP_VAR, LAP_VAR_NORM, SHARP_RATIO, MEAN_LUMA. Higher = sharper
 * for the first three.
 */
public final class BlurCandidates {
    public static final int SIDE = 112;
    public static final int MIN_BBOX = 8;
    public static final int BLUR_TAP = 5;

    public static final int LAP_VAR = 0;
    public static final int LAP_VAR_NORM = 1;
    public static final int SHARP_RATIO = 2;
    public static final int MEAN_LUMA = 3;

    private BlurCandidates() {}

    /** Returns null if the input or the clamped bbox is unusable. */
    public static double[] compute(byte[] rgb, int w, int h, int bx, int by, int bw, int bh) {
        if (rgb == null || w <= 0 || h <= 0 || (long) w * h * 3 > rgb.length) return null;
        int x0 = Math.max(0, bx);
        int y0 = Math.max(0, by);
        int x1 = Math.min(w, bx + bw);
        int y1 = Math.min(h, by + bh);
        if (x1 - x0 < MIN_BBOX || y1 - y0 < MIN_BBOX) return null;

        float[] g = cropLuma(rgb, w, x0, y0, x1 - x0, y1 - y0);
        int n = SIDE;

        double sum = 0;
        for (int i = 0; i < g.length; i++) sum += g[i];
        double mean = sum / g.length;

        double lsum = 0, lsq = 0;
        long ln = 0;
        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                int i = y * n + x;
                double lap = 4.0 * g[i] - g[i - 1] - g[i + 1] - g[i - n] - g[i + n];
                lsum += lap;
                lsq += lap * lap;
                ln++;
            }
        }
        double var = 0;
        if (ln > 0) {
            double m = lsum / ln;
            var = Math.max(0.0, lsq / ln - m * m);
        }
        double norm = var / (mean * mean + 1.0);
        return new double[]{var, norm, sharpRatio(g), mean};
    }

    private static float[] cropLuma(byte[] rgb, int w, int x0, int y0, int cw, int ch) {
        int n = SIDE;
        float[] out = new float[n * n];
        for (int oy = 0; oy < n; oy++) {
            int sy0 = y0 + oy * ch / n;
            int sy1 = y0 + (oy + 1) * ch / n;
            if (sy1 <= sy0) sy1 = sy0 + 1;
            for (int ox = 0; ox < n; ox++) {
                int sx0 = x0 + ox * cw / n;
                int sx1 = x0 + (ox + 1) * cw / n;
                if (sx1 <= sx0) sx1 = sx0 + 1;
                long acc = 0;
                int cnt = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int i = (sy * w + sx) * 3;
                        int r = rgb[i] & 0xFF;
                        int gg = rgb[i + 1] & 0xFF;
                        int b = rgb[i + 2] & 0xFF;
                        acc += (77 * r + 150 * gg + 29 * b) >> 8;
                        cnt++;
                    }
                }
                out[oy * n + ox] = cnt == 0 ? 0f : (float) acc / cnt;
            }
        }
        return out;
    }

    /**
     * Crete-style re-blur ratio. Blur the crop (horizontal and vertical box, BLUR_TAP),
     * then measure how much neighbour-difference energy survives. A sharp crop loses
     * a lot when blurred (ratio high); an already blurry crop loses little (ratio low).
     * Returns min over the two directions; 0 for a flat crop.
     */
    private static double sharpRatio(float[] g) {
        int n = SIDE;
        int r = BLUR_TAP / 2;
        float[] bhz = new float[n * n];
        float[] bvt = new float[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float sh = 0f, sv = 0f;
                for (int k = -r; k <= r; k++) {
                    int xx = Math.min(n - 1, Math.max(0, x + k));
                    int yy = Math.min(n - 1, Math.max(0, y + k));
                    sh += g[y * n + xx];
                    sv += g[yy * n + x];
                }
                bhz[y * n + x] = sh / BLUR_TAP;
                bvt[y * n + x] = sv / BLUR_TAP;
            }
        }
        double soH = 0, svH = 0, soV = 0, svV = 0;
        for (int y = 1; y < n; y++) {
            for (int x = 1; x < n; x++) {
                int i = y * n + x;
                double dOh = Math.abs(g[i] - g[i - 1]);
                double dBh = Math.abs(bhz[i] - bhz[i - 1]);
                soH += dOh;
                svH += Math.max(0.0, dOh - dBh);
                double dOv = Math.abs(g[i] - g[i - n]);
                double dBv = Math.abs(bvt[i] - bvt[i - n]);
                soV += dOv;
                svV += Math.max(0.0, dOv - dBv);
            }
        }
        double rh = soH < 1e-6 ? 0.0 : svH / soH;
        double rv = soV < 1e-6 ? 0.0 : svV / soV;
        return Math.min(rh, rv);
    }
}
