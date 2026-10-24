package liveness.android;

import java.util.Arrays;

/**
 * YuNet input preprocessing: aspect-preserving bilinear resize into the top-left of a 640x640 canvas,
 * interleaved RGB to planar BGR, no normalisation. Pure Java so it can be tested without ONNX Runtime.
 *
 * <p>Output is bit-identical to the original per-pixel implementation. Differences are performance only:
 * per-column sample tables are computed once per frame instead of once per pixel, the buffer is zeroed
 * only when the resized layout changes (the padding is otherwise already zero and every content pixel is
 * overwritten), and pixels whose sample point is exactly on the source grid (both fractions zero, as in the
 * common 1:1 case) are copied directly, which equals the interpolation result exactly.
 */
final class YuNetInputPreprocessor {

    static final int SIZE = 640;
    private static final int PLANE_SIZE = SIZE * SIZE;

    private final float[] buffer = new float[3 * PLANE_SIZE];
    private final int[] colOffset0 = new int[SIZE];
    private final int[] colOffset1 = new int[SIZE];
    private final double[] colFraction = new double[SIZE];
    private int layoutWidth;
    private int layoutHeight;

    static final class Resize {
        final double scaleX;
        final double scaleY;

        Resize(double scaleX, double scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    /** Planar BGR, length 3 * 640 * 640. Reused across calls; valid until the next {@link #prepare}. */
    float[] buffer() {
        return buffer;
    }

    /** Caller validates {@code rgb.length >= width * height * 3} and positive dimensions. */
    Resize prepare(byte[] rgb, int width, int height) {
        double scale = Math.min(SIZE / (double) width, SIZE / (double) height);
        int resizedWidth = Math.max(1, Math.min(SIZE, (int) Math.round(width * scale)));
        int resizedHeight = Math.max(1, Math.min(SIZE, (int) Math.round(height * scale)));
        double scaleX = resizedWidth / (double) width;
        double scaleY = resizedHeight / (double) height;

        if (resizedWidth != layoutWidth || resizedHeight != layoutHeight) {
            Arrays.fill(buffer, 0.0f);
            layoutWidth = resizedWidth;
            layoutHeight = resizedHeight;
        }

        for (int x = 0; x < resizedWidth; x++) {
            double sourceX = clamp((x + 0.5) / scaleX - 0.5, 0.0, width - 1.0);
            int x0 = (int) Math.floor(sourceX);
            int x1 = Math.min(width - 1, x0 + 1);
            colOffset0[x] = x0 * 3;
            colOffset1[x] = x1 * 3;
            colFraction[x] = sourceX - x0;
        }

        for (int y = 0; y < resizedHeight; y++) {
            double sourceY = clamp((y + 0.5) / scaleY - 0.5, 0.0, height - 1.0);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(height - 1, y0 + 1);
            double fy = sourceY - y0;
            int row0 = y0 * width * 3;
            int row1 = y1 * width * 3;
            int rowBase = y * SIZE;
            for (int x = 0; x < resizedWidth; x++) {
                int p00 = row0 + colOffset0[x];
                int pixel = rowBase + x;
                double fx = colFraction[x];
                if (fx == 0.0 && fy == 0.0) {
                    // Exact: top = v*1.0 + v10*0.0 = v, bottom likewise, result = v*1.0 + bottom*0.0 = v.
                    buffer[pixel] = rgb[p00 + 2] & 0xff;
                    buffer[PLANE_SIZE + pixel] = rgb[p00 + 1] & 0xff;
                    buffer[2 * PLANE_SIZE + pixel] = rgb[p00] & 0xff;
                } else {
                    int p10 = row0 + colOffset1[x];
                    int p01 = row1 + colOffset0[x];
                    int p11 = row1 + colOffset1[x];
                    // The API input is RGB; the exported YuNet graph expects BGR.
                    buffer[pixel] = (float) interpolate(rgb, p00, p10, p01, p11, 2, fx, fy);
                    buffer[PLANE_SIZE + pixel] = (float) interpolate(rgb, p00, p10, p01, p11, 1, fx, fy);
                    buffer[2 * PLANE_SIZE + pixel] = (float) interpolate(rgb, p00, p10, p01, p11, 0, fx, fy);
                }
            }
        }
        return new Resize(scaleX, scaleY);
    }

    private static double interpolate(byte[] rgb, int p00, int p10, int p01, int p11,
                                      int channel, double fx, double fy) {
        double v00 = rgb[p00 + channel] & 0xff;
        double v10 = rgb[p10 + channel] & 0xff;
        double v01 = rgb[p01 + channel] & 0xff;
        double v11 = rgb[p11 + channel] & 0xff;
        double top = v00 * (1.0 - fx) + v10 * fx;
        double bottom = v01 * (1.0 - fx) + v11 * fx;
        return top * (1.0 - fy) + bottom * fy;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
