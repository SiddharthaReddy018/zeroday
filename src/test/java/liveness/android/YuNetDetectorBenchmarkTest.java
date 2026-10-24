package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YuNetDetectorBenchmarkTest {

    @Test
    void rejectsInvalidArgumentsBeforeLoadingModel() {
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 640, 480, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 0, 480, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 640, 480, -1, 10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stageStatsReportSharesAndUnattributedTime() {
        String[] names = {"a", "b"};
        double[][] stages = {{1.0, 3.0}, {2.0, 2.0}};
        double[] total = {4.0, 6.0};
        Map<String, Object> m = YuNetDetectorBenchmark.stageStatsMap(names, stages, total);
        assertEquals(2.0, (Double) ((Map<String, Object>) m.get("a")).get("meanMs"), 1e-12);
        assertEquals(0.4, (Double) ((Map<String, Object>) m.get("a")).get("share_of_mean_total"), 1e-12);
        assertEquals(0.4, (Double) ((Map<String, Object>) m.get("b")).get("share_of_mean_total"), 1e-12);
        assertEquals(1.0, (Double) m.get("unattributed_mean_ms"), 1e-12);
    }

    @Test
    void stageStatsRejectMismatchedInputs() {
        assertThrows(IllegalArgumentException.class, () -> YuNetDetectorBenchmark.stageStatsMap(
                new String[]{"a"}, new double[][]{{1.0}, {2.0}}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () -> YuNetDetectorBenchmark.stageStatsMap(
                new String[]{"a"}, new double[][]{{1.0, 2.0}}, new double[]{1.0}));
    }
}
