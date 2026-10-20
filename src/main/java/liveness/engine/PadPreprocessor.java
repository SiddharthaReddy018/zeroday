package liveness.engine;

/**
 * Converts a raw interleaved RGB frame plus a face box into the MiniFASNetV2-SE input tensor
 * (HANDOFF-2 R1 / S16.8): square crop at 1.5x the face box, reflect-101 padding when the crop leaves the
 * image, resize to 128x128, RGB, CHW, scaled to [0, 1].
 * <p>
 * Resampling: area (box) averaging when shrinking, bilinear when enlarging. The reference export documents
 * INTER_AREA / LANCZOS4; the difference is small but NOT zero, so the pipeline must be re-qualified against
 * the real model (ModelQualificationTest + a parity check against the OpenCV reference) before thresholds are
 * frozen.
 * <p>
 * Input contract: {@code rgb.length >= width*height*3}, byte order R,G,B per pixel. Anything else is rejected
 * with {@link IllegalArgumentException} so the caller can fail closed.
 */
public final class PadPreprocessor {

    public static final int SIZE = 128;
    public static final double CROP_FACTOR = 1.5;

    private PadPreprocessor() {}

    /**
     * If bboxW or bboxH is <= 0 the whole image is treated as the face (centred square).
     */
    public static float[] prepare(byte[] rgb, int width, int height,
                                  int bboxX, int bboxY, int bboxW, int bboxH) {
        if (rgb == null || width <= 0 || height <= 0 || (long) rgb.length < (long) width * height * 3) {
            throw new IllegalArgumentException("invalid RGB buffer for PAD preprocessing");
        }
        double cx, cy, side;
        if (bboxW > 0 && bboxH > 0) {
            cx = bboxX + bboxW / 2.0;
            cy = bboxY + bboxH / 2.0;
            side = CROP_FACTOR * Math.max(bboxW, bboxH);
        } else {
            cx = width / 2.0;
            cy = height / 2.0;
            side = Math.min(width, height);
        }
        double x0 = cx - side / 2.0;
        double y0 = cy - side / 2.0;
        double scale = side / SIZE;           // source pixels per output pixel

        float[] out = new float[3 * SIZE * SIZE];
        final int plane = SIZE * SIZE;
        if (scale > 1.0) {
            areaResample(rgb, width, height, x0, y0, scale, out, plane);
        } else {
            bilinearResample(rgb, width, height, x0, y0, scale, out, plane);
        }
        return out;
    }

    private static void areaResample(byte[] rgb, int w, int h, double x0, double y0, double scale,
                                     float[] out, int plane) {
        for (int oy = 0; oy < SIZE; oy++) {
            double sy0 = y0 + oy * scale, sy1 = sy0 + scale;
            for (int ox = 0; ox < SIZE; ox++) {
                double sx0 = x0 + ox * scale, sx1 = sx0 + scale;
                double r = 0, g = 0, b = 0, wsum = 0;
                for (int iy = (int) Math.floor(sy0); iy < Math.ceil(sy1); iy++) {
                    double wy = Math.min(sy1, iy + 1) - Math.max(sy0, iy);
                    if (wy <= 0) continue;
                    int ry = reflect(iy, h);
                    for (int ix = (int) Math.floor(sx0); ix < Math.ceil(sx1); ix++) {
                        double wx = Math.min(sx1, ix + 1) - Math.max(sx0, ix);
                        if (wx <= 0) continue;
                        int o = (ry * w + reflect(ix, w)) * 3;
                        double wt = wx * wy;
                        r += wt * (rgb[o] & 0xFF);
                        g += wt * (rgb[o + 1] & 0xFF);
                        b += wt * (rgb[o + 2] & 0xFF);
                        wsum += wt;
                    }
                }
                int i = oy * SIZE + ox;
                out[i] = (float) (r / wsum / 255.0);
                out[plane + i] = (float) (g / wsum / 255.0);
                out[2 * plane + i] = (float) (b / wsum / 255.0);
            }
        }
    }

    private static void bilinearResample(byte[] rgb, int w, int h, double x0, double y0, double scale,
                                         float[] out, int plane) {
        for (int oy = 0; oy < SIZE; oy++) {
            double sy = y0 + (oy + 0.5) * scale - 0.5;
            int iy = (int) Math.floor(sy);
            double fy = sy - iy;
            for (int ox = 0; ox < SIZE; ox++) {
                double sx = x0 + (ox + 0.5) * scale - 0.5;
                int ix = (int) Math.floor(sx);
                double fx = sx - ix;
                int i = oy * SIZE + ox;
                for (int c = 0; c < 3; c++) {
                    double p00 = px(rgb, w, h, ix, iy, c), p10 = px(rgb, w, h, ix + 1, iy, c);
                    double p01 = px(rgb, w, h, ix, iy + 1, c), p11 = px(rgb, w, h, ix + 1, iy + 1, c);
                    double v = (p00 * (1 - fx) + p10 * fx) * (1 - fy) + (p01 * (1 - fx) + p11 * fx) * fy;
                    out[c * plane + i] = (float) (v / 255.0);
                }
            }
        }
    }

    private static double px(byte[] rgb, int w, int h, int x, int y, int c) {
        return rgb[(reflect(y, h) * w + reflect(x, w)) * 3 + c] & 0xFF;
    }

    /** BORDER_REFLECT_101: -1 -> 1, n -> n-2 (edge pixel not repeated). */
    static int reflect(int i, int n) {
        if (n == 1) return 0;
        int period = 2 * (n - 1);
        int m = ((i % period) + period) % period;
        return m < n ? m : period - m;
    }
}
