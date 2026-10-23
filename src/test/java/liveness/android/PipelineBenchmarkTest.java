package liveness.android;

import liveness.crypto.EcdsaSignerStub;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PipelineBenchmarkTest {

    private static final Path INT8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");

    /** Honest placeholder: not production grade, never called (the benchmark session is fail-closed on same-face). */
    private static final FaceEmbedder EMBEDDER = new FaceEmbedder() {
        @Override public float[] embed(byte[] rgb, int w, int h) {
            throw new UnsupportedOperationException("not measured");
        }
    };

    @Test
    void rejectsBadArgumentsBeforeTouchingAnyModel() {
        Path p = Paths.get("does-not-matter.onnx");
        assertThrows(IllegalArgumentException.class, () -> PipelineBenchmark.run(p, new EcdsaSignerStub(), EMBEDDER,
                PipelineBenchmark.Mode.LOCKSTEP, 640, 480, 270, 30, 0, PipelineBenchmark.BLOCKS - 1));
        assertThrows(IllegalArgumentException.class, () -> PipelineBenchmark.run(p, new EcdsaSignerStub(), EMBEDDER,
                PipelineBenchmark.Mode.PACED, 640, 480, 270, 0, 0, 50));
    }

    @Test
    void missingModelFailsLoudlyNotWithAStub() {
        assertThrows(IllegalStateException.class, () -> PipelineBenchmark.run(
                Paths.get("no-such-model.onnx"), new EcdsaSignerStub(), EMBEDDER,
                PipelineBenchmark.Mode.LOCKSTEP, 640, 480, 270, 30, 0, 10));
    }

    @Test
    void lockstepWithRealModelCoversEveryStage() throws Exception {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        Map<String, Object> r = PipelineBenchmark.run(INT8, new EcdsaSignerStub(), EMBEDDER,
                PipelineBenchmark.Mode.LOCKSTEP, 640, 480, 270, 30, 5, 30);
        assertCommon(r, 30);
        assertFalse(StageTimer.isEnabled(), "timer must be switched off after a run");
    }

    @Test
    void pacedWithRealModelReportsGapsDropsAndEndToEnd() throws Exception {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        Map<String, Object> r = PipelineBenchmark.run(INT8, new EcdsaSignerStub(), EMBEDDER,
                PipelineBenchmark.Mode.PACED, 640, 480, 270, 30, 5, 30);
        assertCommon(r, 30);
        assertTrue(r.containsKey("e2e_arrival_to_processed"));
        assertEquals(30, r.get("producer_fps"));
    }

    @SuppressWarnings("unchecked")
    private static void assertCommon(Map<String, Object> r, int frames) {
        assertEquals(frames, r.get("pad_infer_calls"));
        Map<String, Object> stages = (Map<String, Object>) r.get("stages");
        for (String s : new String[]{"YUV_TO_RGB", "LUMA_STATS", "DETECT", "PHASH", "FACE_FRAME", "CAPTURE_TOTAL",
                "INTEGRITY", "QUALITY", "PREPROCESS", "PAD_INFER", "PROCESS_TOTAL"}) {
            Map<String, Object> m = (Map<String, Object>) stages.get(s);
            assertNotNull(m, "missing stage " + s);
            assertEquals(frames, m.get("n"), s);
            assertEquals(0, m.get("overflow"), s);
        }
        assertEquals(PipelineBenchmark.BLOCKS, ((List<Object>) r.get("per_frame_block_p50_ms")).size());
        assertNotNull(r.get("timestamp_gap"));
        assertTrue(((String) r.get("detector")).contains("FIXED BOX"));
        String json = MiniJson.write(r);   // finite numbers only; same serialiser the app uses
        assertEquals(r.get("bench"), MiniJson.parseObject(json).get("bench"));
    }
}
