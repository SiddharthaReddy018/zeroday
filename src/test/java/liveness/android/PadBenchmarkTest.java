package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PadBenchmarkTest {

    private static final Path INT8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");
    private static final Path FP32 = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");

    @Test
    void rejectsTooFewIterationsBeforeTouchingAnyModel() {
        assertThrows(IllegalArgumentException.class,
                () -> PadBenchmark.run(Paths.get("does-not-matter.onnx"), 0, PadBenchmark.BLOCKS - 1));
        assertThrows(IllegalArgumentException.class,
                () -> PadBenchmark.run(Paths.get("does-not-matter.onnx"), -1, 50));
    }

    @Test
    void shortestFloatTextRoundTrips() {
        assertEquals(0.1d, PadBenchmark.shortest(0.1f), 0.0);
        assertEquals(-3.5d, PadBenchmark.shortest(-3.5f), 0.0);
    }

    @Test
    void int8ReportIsCompleteFiniteAndSerialisable() throws Exception {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        assertReport(PadBenchmark.run(INT8, 3, 25), "MiniFASNetV2-SE-int8.onnx");
    }

    @Test
    void fp32ReportIsCompleteFiniteAndSerialisable() throws Exception {
        assumeTrue(Files.exists(FP32), "FP32 model not present; SKIPPED (not passed): " + FP32);
        assertReport(PadBenchmark.run(FP32, 3, 25), "MiniFASNetV2-SE.onnx");
    }

    @SuppressWarnings("unchecked")
    private static void assertReport(Map<String, Object> r, String file) {
        assertEquals("pad_model", r.get("bench"));
        assertEquals(file, r.get("model_file"));
        assertEquals(25, ((Map<String, Object>) r.get("stats")).get("n"));
        assertEquals(PadBenchmark.BLOCKS, ((List<Object>) r.get("block_p50_ms")).size());
        List<Object> logits = (List<Object>) r.get("logits_live_spoof");
        assertEquals(2, logits.size());
        for (Object o : logits) assertTrue(Double.isFinite(((Number) o).doubleValue()));
        // Must survive the same serialiser the benchmark app uses (it rejects NaN/Infinity).
        String json = MiniJson.write(r);
        assertEquals(file, MiniJson.parseObject(json).get("model_file"));
        // Printed so a human can diff desktop vs phone for the SAME model file (AUDIT_TODO X2). Not an assertion:
        // INT8 kernels may differ by a few LSB across CPUs, FP32 should agree closely. Judge, do not assume 1e-3.
        System.out.println("PARITY " + file + " logits=" + logits + " score=" + r.get("score"));
    }
}
