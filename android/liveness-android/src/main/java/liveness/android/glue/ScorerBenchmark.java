package liveness.android.glue;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import liveness.android.LatencyBenchmark;
import liveness.engine.MiniFasNetScorer;

/**
 * Measures MiniFASNetV2-SE INT8 inference on THIS device against the p95 <= 200 ms target.
 * Run on the reference low-end phone, release-like build, screen on, not charging, device cool:
 * <pre>
 *   String json = ScorerBenchmark.run(context, "MiniFASNetV2-SE-int8.onnx", 30, 300);   // logs tag LivenessBench
 * </pre>
 * Measures scorer.score() only (the ONNX Runtime call on a [1,3,128,128] float input). End-to-end per-frame latency
 * also includes YUV->RGB, face detection and preprocessing, which this does not cover.
 * Synthetic input is used: latency of a fixed-shape model does not depend on pixel content.
 */
public final class ScorerBenchmark {
    private ScorerBenchmark() {}

    public static String run(Context ctx, String assetName, int warmup, int iterations) throws Exception {
        File model = new File(ctx.getCacheDir(), assetName);
        try (InputStream in = ctx.getAssets().open(assetName); OutputStream out = new FileOutputStream(model)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        try (final MiniFasNetScorer scorer = new MiniFasNetScorer(model.toPath())) {
            if (scorer.isStubMode()) throw new IllegalStateException("model failed to load; refusing to benchmark the stub");
            final float[] input = new float[3 * 128 * 128];
            Random r = new Random(7);
            for (int i = 0; i < input.length; i++) input[i] = r.nextFloat();
            LatencyBenchmark.Stats s = LatencyBenchmark.measure(() -> scorer.score(input), warmup, iterations);
            String json = "{\"device\":\"" + android.os.Build.MODEL + "\",\"sdk\":" + android.os.Build.VERSION.SDK_INT
                    + ",\"model\":\"" + assetName + "\",\"target_p95_ms\":200,\"met\":" + s.meetsP95(200.0)
                    + ",\"stats\":" + s.toJson() + "}";
            Log.i("LivenessBench", json);
            return json;
        }
    }
}
