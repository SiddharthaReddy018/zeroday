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
     * Initializes the scorer.
     * <ul>
     *   <li>{@code modelPath == null}: explicit stub mode (tests / UI demos only).</li>
     *   <li>{@code modelPath != null}: the model MUST load. A missing file or a load failure throws
     *       {@link IllegalStateException}; it never silently degrades to a stub that passes everyone.</li>
     * </ul>
     */
    public MiniFasNetScorer(Path modelPath) {
        if (modelPath == null) {
            this.isStubMode = true;
            return;
        }
        if (!Files.exists(modelPath)) {
            throw new IllegalStateException("PAD model file not found: " + modelPath);
        }
        try {
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            this.session = env.createSession(modelPath.toString(), opts);
            this.inputName = session.getInputNames().iterator().next();
        } catch (OrtException | RuntimeException | LinkageError e) {   // LinkageError: native ORT lib missing
            throw new IllegalStateException("PAD model failed to load: " + modelPath, e);
        }
        this.isStubMode = false;
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

        if (preprocessedInput == null || preprocessedInput.length != 3 * 128 * 128) {
            throw new IllegalArgumentException("PAD input must be 3x128x128 floats (49152), got "
                    + (preprocessedInput == null ? "null" : String.valueOf(preprocessedInput.length)));
        }
        try {
            long[] shape = new long[]{1, 3, 128, 128};
            FloatBuffer buffer = FloatBuffer.wrap(preprocessedInput);
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {

                float[][] logits = (float[][]) result.get(0).getValue();
                // ModelQualificationTest (§3.1): Class 0 = live (bona fide), Class 1 = spoof (attack)
                float liveLogit = logits[0][0];
                float spoofLogit = logits[0][1];

                // Softmax probability for live class
                float maxLogit = Math.max(liveLogit, spoofLogit);
                float expLive = (float) Math.exp(liveLogit - maxLogit);
                float expSpoof = (float) Math.exp(spoofLogit - maxLogit);
                float liveProb = expLive / (expLive + expSpoof);

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
