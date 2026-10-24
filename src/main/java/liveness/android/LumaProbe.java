package liveness.android;

/** Public bench/diagnostic access to the production luma metric. Not used by the engine. */
public final class LumaProbe {
    private LumaProbe() {}

    /** Returns {laplacianVariance, brightnessMean}: the same function CameraXDeviceAdapter uses. */
    public static double[] stats(YuvFrame f) {
        return CameraXDeviceAdapter.lumaStats(f);
    }
}
