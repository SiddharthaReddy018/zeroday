package liveness.engine;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MiniFasNetScorer implements AutoCloseable {
    
    private final boolean isStubMode;
    private int stubScore = 80;
    private final int[] expectedShape = new int[]{1, 3, 80, 80};

    public MiniFasNetScorer(Path modelPath) {
        if (modelPath == null || !Files.exists(modelPath)) {
            this.isStubMode = true;
        } else {
            this.isStubMode = false;
        }
    }

    public void setStubScore(int score) {
        this.stubScore = score;
    }

    public int score(float[] preprocessedInput) {
        if (isStubMode) {
            return stubScore;
        }
        throw new IllegalStateException("ONNX inference not implemented yet");
    }

    public int[] inputShape() {
        return expectedShape;
    }

    /** Model version string for attestation (§6.7). */
    public String modelVersion() {
        return isStubMode ? "STUB-v0.0.0" : "MiniFASNetV2-SE-v1.0";
    }

    /** Whether running in stub mode (no model file). */
    public boolean isStubMode() {
        return isStubMode;
    }

    @Override
    public void close() {
    }
}
