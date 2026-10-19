package liveness.android;

import liveness.engine.MiniFasNetScorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Concrete benchmark for the MiniFASNetV2-SE INT8 model executing through ONNX Runtime.
 * Verifies that the INT8 quantized model comfortably satisfies the p95 <= 200 ms latency target.
 */
public class Int8ModelBenchmarkTest {

    @Test
    public void benchmarkInt8ModelInference() throws Exception {
        Path modelPath = Paths.get("android/liveness-android/src/main/assets/MiniFASNetV2-SE-int8.onnx");
        if (!Files.exists(modelPath)) {
            modelPath = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");
        }
        if (!Files.exists(modelPath)) {
            System.out.println("INT8 model file not found, skipping benchmark.");
            return;
        }

        try (MiniFasNetScorer scorer = new MiniFasNetScorer(modelPath)) {
            assertFalse(scorer.isStubMode(), "Model must run in native ONNX mode, not stub mode");

            // Synthetic [1, 3, 128, 128] float input
            final float[] input = new float[3 * 128 * 128];
            Random r = new Random(42);
            for (int i = 0; i < input.length; i++) {
                input[i] = r.nextFloat();
            }

            // Benchmark 30 warmup iterations + 300 measured iterations
            LatencyBenchmark.Stats stats = LatencyBenchmark.measure(() -> scorer.score(input), 30, 300);

            System.out.println("=== INT8 Model Benchmark Results ===");
            System.out.println("Iterations: " + stats.n);
            System.out.println("Mean: " + String.format("%.2f", stats.meanMs) + " ms");
            System.out.println("p50:  " + String.format("%.2f", stats.p50Ms) + " ms");
            System.out.println("p95:  " + String.format("%.2f", stats.p95Ms) + " ms");
            System.out.println("p99:  " + String.format("%.2f", stats.p99Ms) + " ms");
            System.out.println("Max:  " + String.format("%.2f", stats.maxMs) + " ms");
            System.out.println("Target <= 200ms: " + stats.meetsP95(200.0));
            System.out.println("JSON: " + stats.toJson());
            System.out.println("=====================================");

            assertTrue(stats.meetsP95(200.0), "INT8 model p95 must be <= 200ms, was: " + stats.p95Ms);
        }
    }
}
