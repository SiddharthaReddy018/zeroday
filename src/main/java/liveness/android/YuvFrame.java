package liveness.android;

/**
 * Plain-Java snapshot of an Android YUV_420_888 image (three planes).
 * The Android glue copies plane bytes out of ImageProxy and closes the proxy
 * immediately, so no camera buffer is ever retained by the engine.
 */
public final class YuvFrame {
    public final int width;
    public final int height;
    public final byte[] y;
    public final byte[] u;
    public final byte[] v;
    public final int yRowStride;
    public final int uvRowStride;
    public final int uvPixelStride;
    public final int rotationDegrees;
    public final long timestampMs;
    /** {@link System#nanoTime()} when this snapshot was created (producer side); used only to measure queue age. */
    public final long arrivalNanos = System.nanoTime();

    public YuvFrame(int width, int height, byte[] y, byte[] u, byte[] v,
                    int yRowStride, int uvRowStride, int uvPixelStride,
                    int rotationDegrees, long timestampMs) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("bad dimensions");
        if (y == null || u == null || v == null) throw new IllegalArgumentException("null plane");
        if (rotationDegrees % 90 != 0) throw new IllegalArgumentException("rotation must be multiple of 90");
        this.width = width;
        this.height = height;
        this.y = y;
        this.u = u;
        this.v = v;
        this.yRowStride = yRowStride;
        this.uvRowStride = uvRowStride;
        this.uvPixelStride = uvPixelStride;
        this.rotationDegrees = ((rotationDegrees % 360) + 360) % 360;
        this.timestampMs = timestampMs;
    }
}
