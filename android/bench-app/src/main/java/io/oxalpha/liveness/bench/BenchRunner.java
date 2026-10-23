package io.oxalpha.liveness.bench;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import liveness.android.MiniJson;
import liveness.android.FaceEmbedder;
import liveness.android.PadBenchmark;
import liveness.android.PipelineBenchmark;
import liveness.crypto.EcdsaSignerStub;

/** Runs benchmarks off the UI thread's responsibility (caller supplies the thread), saves JSON, logs to logcat. */
final class BenchRunner {
    static final String TAG = "LivenessBench";
    static final int PAD_WARMUP = 50;
    static final int PAD_ITERATIONS = 500;

    private BenchRunner() {}

    static String runPad(Context ctx, String assetName) throws Exception {
        File model = copyAsset(ctx, assetName);
        Map<String, Object> out = envelope(ctx, "pad_model:" + assetName);
        out.put("before", DeviceInfo.dynamicInfo(ctx));
        out.put("result", PadBenchmark.run(model.toPath(), PAD_WARMUP, PAD_ITERATIONS));
        out.put("after", DeviceInfo.dynamicInfo(ctx));
        return save(ctx, "pad_" + assetName.replace(".onnx", ""), out);
    }

    static final int PIPE_WARMUP = 60;
    static final int PIPE_FRAMES = 400;

    /** Embedder/signer are inert placeholders: neither runs per frame, and the report says SFace/signing are not measured. */
    private static final FaceEmbedder INERT_EMBEDDER = new FaceEmbedder() {
        @Override public float[] embed(byte[] rgb, int w, int h) { return new float[128]; }
        @Override public boolean productionGrade() { return true; }
    };

    static String runPipeline(Context ctx, String assetName, PipelineBenchmark.Mode mode) throws Exception {
        File model = copyAsset(ctx, assetName);
        Map<String, Object> out = envelope(ctx, "pipeline_" + mode + ":" + assetName);
        out.put("before", DeviceInfo.dynamicInfo(ctx));
        out.put("result", PipelineBenchmark.run(model.toPath(), new EcdsaSignerStub(), INERT_EMBEDDER, mode,
                640, 480, 270, 30, PIPE_WARMUP, PIPE_FRAMES));
        out.put("after", DeviceInfo.dynamicInfo(ctx));
        return save(ctx, "pipeline_" + mode.name().toLowerCase() + "_" + assetName.replace(".onnx", ""), out);
    }

    static Map<String, Object> envelope(Context ctx, String what) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("what", what);
        out.put("app", "liveness-bench 1");
        out.put("build_debuggable", (ctx.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        out.put("device", DeviceInfo.staticInfo());
        out.put("wall_clock_ms", System.currentTimeMillis());
        return out;
    }

    static String save(Context ctx, String stem, Map<String, Object> out) throws Exception {
        String json = MiniJson.write(out);
        File dir = new File(ctx.getExternalFilesDir(null), "bench");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        File f = new File(dir, stem + "_" + System.currentTimeMillis() + ".json");
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
        }
        Log.i(TAG, "saved " + f.getAbsolutePath());
        Log.i(TAG, json);
        return f.getAbsolutePath() + "\n\n" + json;
    }

    static File copyAsset(Context ctx, String assetName) throws Exception {
        File model = new File(ctx.getCacheDir(), assetName);
        try (InputStream in = ctx.getAssets().open(assetName); OutputStream out = new FileOutputStream(model)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return model;
    }

}
