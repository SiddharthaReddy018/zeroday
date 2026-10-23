package liveness.android;

import liveness.engine.MiniFasNetScorer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Latency of the PAD model call alone ({@link MiniFasNetScorer#score}: tensor creation + ONNX Runtime run + softmax)
 * on a fixed synthetic input (P4, AUDIT_TODO X6).
 * <p>
 * Pure Java on purpose: the identical code runs on the desktop JVM (see PadBenchmarkTest) and on the phone (the
 * bench app only copies the asset to a file and calls this), so the two numbers are comparable.
 * <p>
 * What this does NOT cover: YUV-to-RGB, face detection, quality gate, preprocessing (crop/resize), integrity
 * monitor, SFace. {@link PipelineBenchmark} covers the per-frame path. Passing p95 here is necessary, not sufficient.
 */
public final class PadBenchmark {

    /** Input seed shared by desktop and device so their logits are comparable for the same model file. */
    public static final long INPUT_SEED = 7L;
    /** Consecutive slices of the run whose medians are reported, to expose slowdown during the run. */
    public static final int BLOCKS = 5;
    /** HANDOFF-2 M4 gate wording: p95 <= 200 ms. For the model call alone this is a weak bar; see class doc. */
    public static final double TARGET_P95_MS = 200.0;

    private PadBenchmark() {}

    /**
     * @param model      ONNX file on disk (FP32 or INT8)
     * @param warmup     untimed calls first (ORT lazily allocates and optimises on early runs)
     * @param iterations timed calls (>= {@link #BLOCKS})
     * @return report as a plain map; serialise with {@link MiniJson#write(Object)}
     */
    public static Map<String, Object> run(Path model, int warmup, int iterations) throws Exception {
        if (iterations < BLOCKS) throw new IllegalArgumentException("iterations must be >= " + BLOCKS);
        if (warmup < 0) throw new IllegalArgumentException("warmup");
        try (final MiniFasNetScorer scorer = new MiniFasNetScorer(model)) {
            if (scorer.isStubMode()) {
                throw new IllegalStateException("model failed to load; refusing to benchmark the stub");
            }
            final float[] input = BenchInput.padInput(INPUT_SEED);
            float[] logits = scorer.rawLogits(input);
            int score = scorer.score(input);

            double[] ms = LatencyBenchmark.sampleMs(() -> scorer.score(input), warmup, iterations);
            LatencyBenchmark.Stats s = LatencyBenchmark.summarize(ms);
            double[] blocks = LatencyBenchmark.blockP50(ms, BLOCKS);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bench", "pad_model");
            out.put("model_file", model.getFileName().toString());
            out.put("model_version", scorer.modelVersion());
            out.put("runtime", MiniFasNetScorer.RUNTIME_DESCRIPTION);
            out.put("input", "xorshift64* seed " + INPUT_SEED + ", 3x128x128 floats in [0,1), synthetic");
            out.put("warmup", warmup);
            out.put("iterations", iterations);
            out.put("cores", Runtime.getRuntime().availableProcessors());
            out.put("stats", statsMap(s));
            List<Object> bl = new ArrayList<>();
            for (double b : blocks) bl.add(b);
            out.put("block_p50_ms", bl);
            out.put("block_drift_last_over_first", blocks[0] > 0 ? (Object) (blocks[blocks.length - 1] / blocks[0]) : null);
            out.put("p95_le_200ms", s.meetsP95(TARGET_P95_MS));
            List<Object> lg = new ArrayList<>();
            for (float f : logits) lg.add(shortest(f));
            out.put("logits_live_spoof", lg);
            out.put("score", score);
            return out;
        }
    }

    static Map<String, Object> statsMap(LatencyBenchmark.Stats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", s.n);
        m.put("meanMs", s.meanMs);
        m.put("p50Ms", s.p50Ms);
        m.put("p95Ms", s.p95Ms);
        m.put("p99Ms", s.p99Ms);
        m.put("maxMs", s.maxMs);
        return m;
    }

    /** Shortest decimal that round-trips the float, as a double (stable text for cross-device comparison). */
    static double shortest(float f) {
        return Double.parseDouble(Float.toString(f));
    }
}
