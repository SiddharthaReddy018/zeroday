package liveness.android;

import liveness.crypto.EcdsaSignerStub;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PipelineBenchmarkBlurOverrideTest {

    private static final Path INT8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");

    private static final FaceEmbedder EMB = new FaceEmbedder() {
        @Override public float[] embed(byte[] rgb, int w, int h) { throw new UnsupportedOperationException(); }
    };

    private static YuvFrame[] pool() {
        YuvFrame[] p = new YuvFrame[8];
        for (int i = 0; i < p.length; i++) p[i] = BenchInput.syntheticYuv(640, 480, 0, 0L, 700L + i);
        return p;
    }

    @Test
    void overrideIsLabelledInResult() throws Exception {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        FaceDetector box = (rgb, w, h) -> new FaceDetector.Observation(1, 100, 100, 160, 120, 0.0, 0.0, 0.0, null);
        Map<String, Object> r = PipelineBenchmark.runWith(INT8, new EcdsaSignerStub(), EMB,
                PipelineBenchmark.Mode.LOCKSTEP, box, "fixed", pool(), "synthetic", 640, 480, 0, 30, 0, 10, 5.0);
        assertEquals(5.0, r.get("blur_gate_override"));
        assertTrue(String.valueOf(r.get("gate_note")).contains("NOT representative"));
        assertNotNull(r.get("laplacian_variance_mean_measured"));
    }

    @Test
    void noOverrideLeavesNoLabel() throws Exception {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        FaceDetector box = (rgb, w, h) -> new FaceDetector.Observation(1, 100, 100, 160, 120, 0.0, 0.0, 0.0, null);
        Map<String, Object> r = PipelineBenchmark.runWith(INT8, new EcdsaSignerStub(), EMB,
                PipelineBenchmark.Mode.LOCKSTEP, box, "fixed", pool(), "synthetic", 640, 480, 0, 30, 0, 10);
        assertFalse(r.containsKey("blur_gate_override"));
        assertFalse(r.containsKey("gate_note"));
    }
}
