package liveness.android.glue;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import liveness.android.MiniJson;
import liveness.android.PadBenchmark;

/**
 * Measures the PAD model call (tensor creation + ONNX Runtime run + softmax) on THIS device. Thin Android wrapper:
 * the measurement itself is {@link PadBenchmark}, pure Java, identical to what the desktop test runs.
 * <pre>
 *   String json = ScorerBenchmark.run(context, "MiniFASNetV2-SE-int8.onnx", 50, 500);   // logs tag LivenessBench
 * </pre>
 * Covers the model call only. The per-frame path (YUV conversion, detection, quality gate, preprocessing, integrity
 * monitor) is {@code liveness.android.PipelineBenchmark}. Run on a release-like build, screen on, not charging, cool.
 * The standalone bench app (android/bench-app) is the easier way to run this before the library module builds (X4).
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
        String json = MiniJson.write(PadBenchmark.run(model.toPath(), warmup, iterations));
        Log.i("LivenessBench", json);
        return json;
    }
}
