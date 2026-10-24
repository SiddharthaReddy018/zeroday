package io.oxalpha.liveness.bench;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import liveness.android.BlurCandidates;
import liveness.android.LumaProbe;
import liveness.android.RgbToYuv;
import liveness.android.YuvConverter;
import liveness.android.YuvFrame;

/**
 * Per-call cost of BlurCandidates.fullFrameNorm on a camera-shaped frame (720x720, rotation 270, Y stride 768),
 * next to YuvConverter.toRgb and LumaProbe.stats on the same frame. BENCH ONLY. Microbenchmark: no detector
 * running, no ImageProxy copy, no thermal load. Single thread, single phone. Not accuracy.
 */
final class FullNormLatencyBenchmark {
    static final int W = 720;
    static final int H = 720;
    static final int ROTATION = 270;
    static final int Y_STRIDE = 768;
    static final int WARMUP = 50;
    static final int N = 500;
    static final String FRAME_NAME = "face_frame_720_sensor.png";

    private FullNormLatencyBenchmark() {}

    interface Op { double run(); }

    static String run(Context ctx) throws Exception {
        File ff = new File(ctx.getExternalFilesDir(null), FRAME_NAME);
        byte[] rgb0;
        String source;
        if (ff.isFile()) {
            rgb0 = loadRgb(ff);
            source = FRAME_NAME;
        } else {
            rgb0 = new byte[W * H * 3];
            new Random(7).nextBytes(rgb0);
            source = "synthetic uniform noise (frame file not on phone)";
        }
        YuvFrame base = RgbToYuv.toFrame(rgb0, W, H, 0, 0L);
        YuvFrame f = new YuvFrame(W, H, padY(base.y, W, H, Y_STRIDE), base.u, base.v, Y_STRIDE,
                base.uvRowStride, base.uvPixelStride, ROTATION, 0L);
        final byte[] rgb = YuvConverter.toRgb(f);
        final int ow = YuvConverter.outputWidth(f);
        final int oh = YuvConverter.outputHeight(f);

        Map<String, Object> out = BenchRunner.envelope(ctx, "fullframenorm_latency");
        out.put("before", DeviceInfo.dynamicInfo(ctx));
        out.put("frame_source", source);
        out.put("input_shape", W + "x" + H + " rot" + ROTATION + " yRowStride" + Y_STRIDE
                + " (chroma strides from RgbToYuv, ASSUMED); toRgb output " + ow + "x" + oh);
        out.put("warmup_calls", WARMUP);
        out.put("timed_calls", N);
        out.put("note", "microbenchmark: no detector running, no ImageProxy copy, cool phone assumed (user-stated); not live pipeline");

        final double[] sink = {0};
        out.put("full_frame_norm_ms", time(() -> BlurCandidates.fullFrameNorm(rgb, ow, oh), sink));
        out.put("yuv_to_rgb_ms", time(() -> YuvConverter.toRgb(f).length, sink));
        out.put("luma_probe_stats_ms_reference", time(() -> LumaProbe.stats(f)[0], sink));
        out.put("sink_do_not_optimise_away", sink[0]);
        out.put("norm_value_check", BlurCandidates.fullFrameNorm(rgb, ow, oh));
        out.put("after", DeviceInfo.dynamicInfo(ctx));
        return BenchRunner.save(ctx, "fullnorm_latency", out);
    }

    private static Map<String, Object> time(Op op, double[] sink) {
        for (int i = 0; i < WARMUP; i++) sink[0] += op.run();
        double[] ms = new double[N];
        for (int i = 0; i < N; i++) {
            long t = System.nanoTime();
            double r = op.run();
            ms[i] = (System.nanoTime() - t) / 1e6;
            sink[0] += r;
        }
        Arrays.sort(ms);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", N);
        m.put("min", ms[0]);
        m.put("p50", ms[N / 2]);
        m.put("p95", ms[(int) Math.floor(0.95 * (N - 1) + 0.5)]);
        m.put("max", ms[N - 1]);
        return m;
    }

    private static byte[] padY(byte[] y, int w, int h, int stride) {
        byte[] o = new byte[stride * h];
        for (int r = 0; r < h; r++) System.arraycopy(y, r * w, o, r * stride, w);
        return o;
    }

    private static byte[] loadRgb(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (b == null) throw new IllegalStateException("cannot decode " + file);
        if (b.getWidth() != W || b.getHeight() != H) {
            throw new IllegalStateException(file.getName() + " is " + b.getWidth() + "x" + b.getHeight());
        }
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        b.recycle();
        byte[] rgb = new byte[W * H * 3];
        for (int i = 0, p = 0; i < px.length; i++, p += 3) {
            rgb[p] = (byte) ((px[i] >> 16) & 0xff);
            rgb[p + 1] = (byte) ((px[i] >> 8) & 0xff);
            rgb[p + 2] = (byte) (px[i] & 0xff);
        }
        return rgb;
    }
}
