package liveness.android;

import liveness.engine.SameFaceChecker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReferenceFaceEmbedderTest {

    @Test
    public void embedProducesNormalizedVector() {
        ReferenceFaceEmbedder embedder = new ReferenceFaceEmbedder();
        byte[] rgb = new byte[100 * 100 * 3];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = (byte) (i % 256);
        }

        float[] embedding = embedder.embed(rgb, 100, 100);
        assertNotNull(embedding);
        assertEquals(128, embedding.length);

        // Verify L2 norm is ~1.0
        double normSq = 0.0;
        for (float v : embedding) {
            normSq += v * v;
        }
        assertEquals(1.0, Math.sqrt(normSq), 1e-4);
    }

    @Test
    public void sameImageHasZeroCosineDistance() {
        ReferenceFaceEmbedder embedder = new ReferenceFaceEmbedder();
        byte[] rgb = new byte[80 * 80 * 3];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = (byte) ((i * 13) % 256);
        }

        float[] emb1 = embedder.embed(rgb, 80, 80);
        float[] emb2 = embedder.embed(rgb, 80, 80);

        double dist = SameFaceChecker.cosineDistance(emb1, emb2);
        assertEquals(0.0, dist, 1e-5);
    }

    @Test
    public void differentImagesHaveNonZeroDistance() {
        ReferenceFaceEmbedder embedder = new ReferenceFaceEmbedder();
        byte[] img1 = new byte[80 * 80 * 3];
        byte[] img2 = new byte[80 * 80 * 3];
        for (int i = 0; i < img1.length; i++) {
            img1[i] = (byte) 50;  // flat dark
            img2[i] = (byte) (i % 255); // high frequency texture
        }

        float[] emb1 = embedder.embed(img1, 80, 80);
        float[] emb2 = embedder.embed(img2, 80, 80);

        double dist = SameFaceChecker.cosineDistance(emb1, emb2);
        assertTrue(dist > 0.01, "Different images should have non-zero cosine distance: " + dist);
    }
}
