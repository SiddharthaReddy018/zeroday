package liveness.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class SameFaceCheckerTest {

    @Test
    public void testIdenticalEmbeddings() {
        float[] emb1 = { 1.0f, 0.0f, 0.0f };
        float[] emb2 = { 1.0f, 0.0f, 0.0f };
        double dist = SameFaceChecker.cosineDistance(emb1, emb2);
        Assertions.assertEquals(0.0, dist, 1e-5);
    }

    @Test
    public void testOrthogonalEmbeddings() {
        float[] emb1 = { 1.0f, 0.0f, 0.0f };
        float[] emb2 = { 0.0f, 1.0f, 0.0f };
        double dist = SameFaceChecker.cosineDistance(emb1, emb2);
        Assertions.assertEquals(1.0, dist, 1e-5);
    }

    @Test
    public void testIsSameFace() {
        SameFaceChecker checker = new SameFaceChecker(0.5);
        float[] emb1 = { 1.0f, 0.0f, 0.0f };
        float[] emb2 = { 0.9f, 0.1f, 0.0f };
        Assertions.assertTrue(checker.isSameFace(emb1, emb2));
    }

    @Test
    public void testStubMode() {
        SameFaceChecker checker = new SameFaceChecker(0.5);
        checker.setStubResult(true);
        float[] emb1 = { 1.0f, 0.0f, 0.0f };
        float[] emb2 = { 0.0f, 1.0f, 0.0f };
        Assertions.assertTrue(checker.isSameFace(emb1, emb2));
    }
}
