package liveness.android;

/**
 * Deterministic, platform-independent synthetic inputs for benchmarks (P4/P5).
 * <p>
 * Uses xorshift64* (integer ops only; the float conversion takes 24 bits, so it is exact), so the SAME seed
 * yields bit-identical data on a desktop JVM and on Android ART. That is what lets a phone's PAD logits be
 * compared with the desktop's for the same file. It is NOT java.util.Random on purpose: nothing here may depend
 * on a platform RNG.
 * <p>
 * Synthetic data is fine for LATENCY of a fixed-shape model (cost does not depend on pixel content). It says
 * nothing about accuracy and must never be used as evidence of one.
 */
public final class BenchInput {

    private BenchInput() {}

    /** xorshift64* generator. */
    public static final class Rng {
        private long state;

        public Rng(long seed) { this.state = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }

        public long nextLong() {
            long x = state;
            x ^= x >>> 12;
            x ^= x << 25;
            x ^= x >>> 27;
            state = x;
            return x * 0x2545F4914F6CDD1DL;
        }

        /** Uniform float in [0,1), exact (24 random bits). */
        public float nextFloat() { return (nextLong() >>> 40) / 16777216f; }
    }

    /** PAD model input: 3x128x128 floats in [0,1) (CHW), deterministic for a seed. */
    public static float[] padInput(long seed) {
        Rng r = new Rng(seed);
        float[] in = new float[3 * 128 * 128];
        for (int i = 0; i < in.length; i++) in[i] = r.nextFloat();
        return in;
    }

    /**
     * Synthetic YUV_420_888 frame shaped like a real CameraX buffer: Y plane with row stride == width, chroma
     * planes interleaved with pixel stride 2 and row stride == width (constant 128 = neutral). The luma is noise in
     * [64,191] (mean ~128), which is bright enough and sharp enough to pass the quality gate; each seed gives a
     * different frame so the freeze/loop monitors see live-like input.
     *
     * @param rotationDegrees 270 is what a portrait front camera typically reports; rotation changes the memory
     *                        access pattern of the YUV-to-RGB conversion, so benchmark with the realistic value.
     */
    public static YuvFrame syntheticYuv(int width, int height, int rotationDegrees, long timestampMs, long seed) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("width and height must be positive and even");
        }
        byte[] y = new byte[width * height];
        Rng r = new Rng(seed);
        int i = 0;
        while (i + 8 <= y.length) {
            long x = r.nextLong();
            for (int j = 0; j < 8; j++) y[i + j] = (byte) (64 + ((x >>> (8 * j)) & 0x7F));
            i += 8;
        }
        if (i < y.length) {
            long x = r.nextLong();
            for (int j = 0; i < y.length; i++, j++) y[i] = (byte) (64 + ((x >>> (8 * j)) & 0x7F));
        }
        int chromaLen = width * (height / 2);
        byte[] u = new byte[chromaLen];
        byte[] v = new byte[chromaLen];
        java.util.Arrays.fill(u, (byte) 128);
        java.util.Arrays.fill(v, (byte) 128);
        return new YuvFrame(width, height, y, u, v, width, width, 2, rotationDegrees, timestampMs);
    }
}
