package liveness.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * MiniFASNet PAD model scorer (§6, §15.2 R1, S26.11).
 * <p>
 * Supports both real ONNX Runtime inference (FP32 or INT8) and
 * stub mode (when model file is not present, for unit testing).
 * <p>
 * Model contract verified via {@link ModelQualificationTest}:
 * - Input: [1, 3, 128, 128] float32 RGB [0, 1]
 * - Output: [1, 2] logits [spoof_score, live_score]
 */
public final class MiniFasNetScorer implements AutoCloseable {

    private final boolean isStubMode;
    private int stubScore = 80;
    private final int[] inputShape = new int[]{1, 3, 128, 128};

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;

    /**
     * Initializes the scorer. If {@code modelPath} is null or does not exist,
     * falls back to stub mode.
     */
    public MiniFasNetScorer(Path modelPath) {
        boolean stub = true;
        if (modelPath != null && Files.exists(modelPath)) {
            try {
                this.env = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                this.session = env.createSession(modelPath.toString(), opts);
                this.inputName = session.getInputNames().iterator().next();
                stub = false;
            } catch (OrtException e) {
                stub = true;
            }
        }
        this.isStubMode = stub;
    }

    public void setStubScore(int score) {
        this.stubScore = score;
    }

    /**
     * Scores a preprocessed face crop.
     *
     * @param preprocessedInput 3x128x128 (49,152 floats) normalized [0, 1]
     * @return PAD score 0-100 where higher = more live (bona fide)
     */
    public int score(float[] preprocessedInput) {
        if (isStubMode) {
            return stubScore;
        }

        try {
            long[] shape = new long[]{1, 3, 128, 128};
            FloatBuffer buffer = FloatBuffer.wrap(preprocessedInput);
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {

                float[][] logits = (float[][]) result.get(0).getValue();
                float spoofLogit = logits[0][0];
                float liveLogit = logits[0][1];

                // Softmax probability for live class
                float maxLogit = Math.max(spoofLogit, liveLogit);
                float expSpoof = (float) Math.exp(spoofLogit - maxLogit);
                float expLive = (float) Math.exp(liveLogit - maxLogit);
                float liveProb = expLive / (expSpoof + expLive);

                return Math.round(liveProb * 100.0f);
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX inference failed", e);
        }
    }

    public int[] inputShape() {
        return inputShape;
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
        if (session != null) {
            try {
                session.close();
            } catch (OrtException ignored) {}
        }
        if (env != null) {
            env.close();
        }
    }
}
