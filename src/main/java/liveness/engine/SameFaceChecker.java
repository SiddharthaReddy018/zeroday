package liveness.engine;

public final class SameFaceChecker {
    
    private final double threshold;
    private Boolean stubResult = null;

    public SameFaceChecker(double threshold) {
        this.threshold = threshold;
    }

    public boolean isSameFace(float[] embedding1, float[] embedding2) {
        if (stubResult != null) {
            return stubResult;
        }
        return cosineDistance(embedding1, embedding2) < threshold;
    }

    public static double cosineDistance(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Embeddings must be same length");
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 1.0;
        return 1.0 - (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public boolean isStubbed() { return stubResult != null; }

    /** TEST/DEMO ONLY: guarded at runtime by {@link liveness.policy.StubPolicy}. */
    public void setStubResult(boolean result) {
        this.stubResult = result;
    }
}
