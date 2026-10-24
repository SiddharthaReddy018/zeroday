package liveness.android;

/**
 * Pure-Java RGB to YUV_420_888 snapshot (BT.601 full range, 2x2 chroma average), the inverse of what
 * {@link YuvConverter} decodes. Layout matches BenchInput.syntheticYuv: chroma length w*h/2, row stride w,
 * pixel stride 2. Benchmark input only; it says nothing about accuracy.
 */
public final class RgbToYuv {

    private RgbToYuv() {}

    public static YuvFrame toFrame(byte[] rgb, int w, int h, int rotationDegrees, long timestampMs) {
        if (w <= 0 || h <= 0 || (w & 1) != 0 || (h & 1) != 0) {
            throw new IllegalArgumentException("width and height must be positive and even");
        }
        if (rgb == null || rgb.length < w * h * 3) throw new IllegalArgumentException("rgb too short");
        byte[] y = new byte[w * h];
        byte[] u = new byte[w * (h / 2)];
        byte[] v = new byte[w * (h / 2)];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int o = (row * w + col) * 3;
                double r = rgb[o] & 0xFF, g = rgb[o + 1] & 0xFF, b = rgb[o + 2] & 0xFF;
                y[row * w + col] = (byte) clamp((int) Math.round(0.299 * r + 0.587 * g + 0.114 * b));
            }
        }
        for (int row = 0; row < h; row += 2) {
            for (int col = 0; col < w; col += 2) {
                double r = 0, g = 0, b = 0;
                for (int dr = 0; dr < 2; dr++) {
                    for (int dc = 0; dc < 2; dc++) {
                        int o = ((row + dr) * w + (col + dc)) * 3;
                        r += rgb[o] & 0xFF;
                        g += rgb[o + 1] & 0xFF;
                        b += rgb[o + 2] & 0xFF;
                    }
                }
                r /= 4; g /= 4; b /= 4;
                int idx = (row >> 1) * w + (col >> 1) * 2;
                u[idx] = (byte) clamp((int) Math.round(128 - 0.168736 * r - 0.331264 * g + 0.5 * b));
                v[idx] = (byte) clamp((int) Math.round(128 + 0.5 * r - 0.418688 * g - 0.081312 * b));
            }
        }
        return new YuvFrame(w, h, y, u, v, w, w, 2, rotationDegrees, timestampMs);
    }

    /**
     * Copy of a rotation-0 frame whose content digest (32x32 sampled luma, see FrameFingerprint) differs from
     * every copy with another index modulo 1024: the Y LSB at sample point (index mod 1024) is flipped.
     * Changes detection and blur statistics negligibly. pHash stays near-identical.
     */
    public static YuvFrame withDigestMark(YuvFrame base, int index, long timestampMs) {
        if (base.rotationDegrees != 0) throw new IllegalArgumentException("rotation 0 only");
        if (base.width < 32 || base.height < 32) throw new IllegalArgumentException("frame too small");
        int k = Math.floorMod(index, 1024);
        int gy = k / 32, gx = k % 32;
        int row = Math.min(base.height - 1, (int) (gy * ((double) base.height / 32.0)));
        int col = Math.min(base.width - 1, (int) (gx * ((double) base.width / 32.0)));
        byte[] y = base.y.clone();
        y[row * base.yRowStride + col] ^= 1;
        return new YuvFrame(base.width, base.height, y, base.u, base.v, base.yRowStride, base.uvRowStride,
                base.uvPixelStride, base.rotationDegrees, timestampMs);
    }

    private static int clamp(int x) { return x < 0 ? 0 : (x > 255 ? 255 : x); }
}
