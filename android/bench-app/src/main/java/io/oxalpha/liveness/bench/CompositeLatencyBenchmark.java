package io.oxalpha.liveness.bench;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import liveness.android.CompositeFaceDetector;
import liveness.android.FaceDetector;
import liveness.android.YuNetFaceDetector;
import liveness.android.glue.MlKitFaceDetector;

/**
 * Latency of YuNet alone, ML Kit alone and the composite on one real frame pushed by adb (never committed):
 * /sdcard/Android/data/io.oxalpha.liveness.bench/files/face_frame.png, exactly 640x480.
 * Latency only. Face counts are recorded for context, not as an accuracy result.
 */
final class CompositeLatencyBenchmark {
    static final String FRAME_NAME = "face_frame.png";
    static final int W = 640;
    static final int H = 480;
    static final int WARMUP = 60;
    static final int FRAMES = 400;

    private CompositeLatencyBenchmark() {}

    static String run(Context ctx) throws Exception {
        File frameFile = new File(ctx.getExternalFilesDir(null), FRAME_NAME);
        if (!frameFile.isFile()) {
            throw new IllegalStateException("missing " + frameFile
                    + " : adb push a 640x480 face frame there, then rerun");
        }
        byte[] rgb = loadRgb(frameFile);
        File model = BenchRunner.copyAsset(ctx, "face_detection_yunet.onnx");

        Map<String, Object> out = BenchRunner.envelope(ctx, "composite_yunet_mlkit");
        out.put("before", DeviceInfo.dynamicInfo(ctx));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bench", "composite_yunet_mlkit");
        result.put("frame", FRAME_NAME + " " + W + "x" + H + " real capture, not committed");
        result.put("warmup_frames", WARMUP);
        result.put("frames", FRAMES);
        result.put("mlkit", "com.google.mlkit:face-detection 16.1.7, bundled, FAST, CONTOUR_MODE_ALL");
        result.put("not_measured", "camera/ImageProxy copy, UI, SFace, PAD, signing, accuracy");
        result.put("percentiles", "nearest-rank on sorted samples");
        try (YuNetFaceDetector yunet = new YuNetFaceDetector(model.toPath());
             MlKitFaceDetector mlkit = new MlKitFaceDetector()) {
            CompositeFaceDetector composite = new CompositeFaceDetector(yunet, mlkit);
            result.put("yunet", measure(yunet, rgb));
            result.put("mlkit", measure(mlkit, rgb));
            result.put("composite", measure(composite, rgb));
        }
        result.put("cores", Runtime.getRuntime().availableProcessors());
        out.put("result", result);
        out.put("after", DeviceInfo.dynamicInfo(ctx));
        return BenchRunner.save(ctx, "composite_yunet_mlkit", out);
    }

    private static Map<String, Object> measure(FaceDetector d, byte[] rgb) {
        FaceDetector.Observation last = null;
        for (int i = 0; i < WARMUP; i++) last = d.detect(rgb, W, H);
        double[] s = new double[FRAMES];
        int minFaces = Integer.MAX_VALUE;
        int maxFaces = 0;
        for (int i = 0; i < FRAMES; i++) {
            long t = System.nanoTime();
            last = d.detect(rgb, W, H);
            s[i] = (System.nanoTime() - t) / 1e6;
            minFaces = Math.min(minFaces, last.faceCount);
            maxFaces = Math.max(maxFaces, last.faceCount);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        double sum = 0;
        for (double v : s) sum += v;
        double[] sorted = s.clone();
        Arrays.sort(sorted);
        m.put("n", FRAMES);
        m.put("meanMs", sum / FRAMES);
        m.put("p50Ms", rank(sorted, 0.50));
        m.put("p95Ms", rank(sorted, 0.95));
        m.put("p99Ms", rank(sorted, 0.99));
        m.put("maxMs", sorted[FRAMES - 1]);
        m.put("face_count_min", minFaces);
        m.put("face_count_max", maxFaces);
        m.put("last_bbox_x", last.bboxX);
        m.put("last_bbox_y", last.bboxY);
        m.put("last_bbox_w", last.bboxW);
        m.put("last_bbox_h", last.bboxH);
        m.put("last_pose_available", last.yawDeg != Double.MAX_VALUE);
        m.put("last_landmark_values", last.landmarks == null ? 0 : last.landmarks.length);
        return m;
    }

    private static double rank(double[] sorted, double p) {
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static byte[] loadRgb(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) throw new IllegalStateException("cannot decode " + f);
        if (b.getWidth() != W || b.getHeight() != H) {
            throw new IllegalStateException(FRAME_NAME + " is " + b.getWidth() + "x" + b.getHeight()
                    + ", need " + W + "x" + H);
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
