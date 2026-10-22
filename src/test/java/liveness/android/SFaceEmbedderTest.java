package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SFaceEmbedderTest {

    @Test
    public void sfaceProducesNormalized128DEmbedding() {
        Path modelPath = Paths.get("datasets/models/face_recognition_sface.onnx");
        assumeTrue(Files.exists(modelPath), "SFace model not downloaded: " + modelPath);

        try (SFaceEmbedder embedder = new SFaceEmbedder(modelPath)) {
            int w = 112, h = 112;
            byte[] rgb = new byte[w * h * 3];
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = (byte) ((i * 17) % 256);
            }

            float[] embedding = embedder.embed(rgb, w, h);
            assertNotNull(embedding);
            assertEquals(128, embedding.length);

            // Verify L2 norm is ~1.0
            double normSq = 0.0;
            for (float v : embedding) {
                normSq += v * v;
            }
            assertEquals(1.0, Math.sqrt(normSq), 1e-4);
        }
    }

    @Test
    public void calibratedThresholdIsStatedConsistently() {
        assertEquals(0.48, SFaceEmbedder.CALIBRATED_THRESHOLD, 1e-6);
    }
}
