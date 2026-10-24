package io.oxalpha.liveness.bench;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.Map;

import liveness.android.CompositeFaceDetector;
import liveness.android.FaceEmbedder;
import liveness.android.PipelineBenchmark;
import liveness.android.RgbToYuv;
import liveness.android.YuNetFaceDetector;
import liveness.android.YuvFrame;
import liveness.android.glue.MlKitFaceDetector;
import liveness.crypto.EcdsaSignerStub;

/**
 * Per-frame pipeline (PAD, QualityGate, session, camera adapter) with the REAL YuNet + ML Kit composite on one real
 * frame pushed by adb (face_frame.png, 640x480, never committed). Lockstep. Latency only, not accuracy.
 */
final class CompositePipelineBenchmark {
    static final int W = 640;
    static final int H = 480;

    private CompositePipelineBenchmark() {}

    private static final FaceEmbedder INERT_EMBEDDER = new FaceEmbedder() {
        @Override public float[] embed(byte[] rgb, int w, int h) {
            throw new UnsupportedOperationException("SFace is not measured by the bench app");
        }
    };

    static String run(Context ctx, String padAsset) throws Exception {
        File frameFile = new File(ctx.getExternalFilesDir(null), CompositeLatencyBenchmark.FRAME_NAME);
        if (!frameFile.isFile()) {
            throw new IllegalStateException("missing " + frameFile + " : adb push a 640x480 face frame there, then rerun");
        }
        byte[] rgb = loadRgb(frameFile);
        File pad = BenchRunner.copyAsset(ctx, padAsset);
        File yunetModel = BenchRunner.copyAsset(ctx, "face_detection_yunet.onnx");

        YuvFrame base = RgbToYuv.toFrame(rgb, W, H, 0, 0L);
        YuvFrame[] pool = new YuvFrame[PipelineBenchmark.FRAME_POOL];
        for (int i = 0; i < pool.length; i++) pool[i] = RgbToYuv.withDigestMark(base, i, 0L);

        Map<String, Object> out = BenchRunner.envelope(ctx, "pipeline_composite_lockstep:" + padAsset);
        out.put("before", DeviceInfo.dynamicInfo(ctx));
        try (YuNetFaceDetector yunet = new YuNetFaceDetector(yunetModel.toPath());
             MlKitFaceDetector mlkit = new MlKitFaceDetector()) {
            CompositeFaceDetector composite = new CompositeFaceDetector(yunet, mlkit);
            Map<String, Object> result = PipelineBenchmark.runWith(pad.toPath(), new EcdsaSignerStub(), INERT_EMBEDDER,
                    PipelineBenchmark.Mode.LOCKSTEP, composite,
                    "REAL composite: YuNet (ORT CPU) + ML Kit FAST CONTOUR_MODE_ALL; detector cost included",
                    pool,
                    CompositeLatencyBenchmark.FRAME_NAME + " " + W + "x" + H + " real capture, RGB->YUV420 round trip, rotation 0 "
                            + "(camera path assumed 270: YUV->RGB cost not representative), pool " + pool.length
                            + " copies each with one luma LSB flipped to defeat FROZEN; soft LOOP_REPLAY flag may trip (audit only); "
                            + "not committed",
                    W, H, 0, 30, BenchRunner.PIPE_WARMUP, BenchRunner.PIPE_FRAMES, 10.0);
            out.put("result", result);
        }
        out.put("after", DeviceInfo.dynamicInfo(ctx));
        return BenchRunner.save(ctx, "pipeline_composite_lockstep_" + padAsset.replace(".onnx", ""), out);
    }

    private static byte[] loadRgb(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) throw new IllegalStateException("cannot decode " + f);
        if (b.getWidth() != W || b.getHeight() != H) {
            throw new IllegalStateException(f.getName() + " is " + b.getWidth() + "x" + b.getHeight() + ", need " + W + "x" + H);
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
