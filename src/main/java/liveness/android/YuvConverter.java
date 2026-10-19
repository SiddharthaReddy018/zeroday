package liveness.android;

/** YUV_420_888 -> packed RGB (R,G,B bytes, row-major), honouring strides and rotation. BT.601 full range. */
public final class YuvConverter {

    private YuvConverter() {}

    /** Output is width*height*3 bytes after rotation; swapped dimensions for 90/270. */
    public static byte[] toRgb(YuvFrame f) {
        int w = f.width, h = f.height;
        int ow = (f.rotationDegrees == 90 || f.rotationDegrees == 270) ? h : w;
        byte[] out = new byte[w * h * 3];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int yv = f.y[row * f.yRowStride + col] & 0xFF;
                int uvIdx = (row >> 1) * f.uvRowStride + (col >> 1) * f.uvPixelStride;
                int uv = (f.u[uvIdx] & 0xFF) - 128;
                int vv = (f.v[uvIdx] & 0xFF) - 128;
                int r = clamp(yv + (int) (1.402f * vv));
                int g = clamp(yv - (int) (0.344136f * uv + 0.714136f * vv));
                int b = clamp(yv + (int) (1.772f * uv));
                int dr, dc;
                switch (f.rotationDegrees) {
                    case 90:  dr = col;         dc = h - 1 - row; break;
                    case 180: dr = h - 1 - row; dc = w - 1 - col; break;
                    case 270: dr = w - 1 - col; dc = row;         break;
                    default:  dr = row;         dc = col;
                }
                int o = (dr * ow + dc) * 3;
                out[o] = (byte) r;
                out[o + 1] = (byte) g;
                out[o + 2] = (byte) b;
            }
        }
        return out;
    }

    public static int outputWidth(YuvFrame f) {
        return (f.rotationDegrees == 90 || f.rotationDegrees == 270) ? f.height : f.width;
    }

    public static int outputHeight(YuvFrame f) {
        return (f.rotationDegrees == 90 || f.rotationDegrees == 270) ? f.width : f.height;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}
