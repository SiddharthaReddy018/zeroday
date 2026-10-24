package liveness.android;

import liveness.crypto.EcdsaSignerStub;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PipelineBenchmarkGateDiagTest {

    private static final Path INT8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");

    @Test
    void sanityFailureNamesTheRejectingGateCheck() {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        FaceEmbedder emb = new FaceEmbedder() {
            @Override public float[] embed(byte[] rgb, int w, int h) { throw new UnsupportedOperationException(); }
        };
        // One face, but only 30x30 px: QualityGate must reject every frame as guide.face.too_far.
        FaceDetector tiny = (rgb, w, h) -> new FaceDetector.Observation(1, 100, 100, 30, 30, 0.0, 0.0, 0.0, null);
        YuvFrame[] pool = new YuvFrame[8];
        for (int i = 0; i < pool.length; i++) pool[i] = BenchInput.syntheticYuv(640, 480, 0, 0L, 500L + i);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PipelineBenchmark.runWith(
                INT8, new EcdsaSignerStub(), emb, PipelineBenchmark.Mode.LOCKSTEP, tiny, "tiny-box", pool, "synthetic",
                640, 480, 0, 30, 0, 10));
        String m = e.getMessage();
        assertTrue(m.contains("gate diagnostic"), m);
        assertTrue(m.contains("guide.face.too_far=10"), m);
        assertTrue(m.contains("bbox=30x30"), m);
        assertFalse(StageTimer.isEnabled(), "timer must be switched off after a failed run");
    }
}
