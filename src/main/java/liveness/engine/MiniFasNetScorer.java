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
 * - Output: [1, 2] logits [live, spoof] (index 0 = live; ADR-011)
 */
public final class MiniFasNetScorer implements AutoCloseable {

    /**
     * What the session options set in the constructor actually are. Benchmark reports quote this string so a latency
     * number is always tied to the configuration that produced it (no NNAPI/XNNPACK, no thread tuning here).
     * Keep it in sync with the SessionOptions below.
     */
    public static final String RUNTIME_DESCRIPTION = "ORT CPU execution provider, BASIC_OPT, default intra-op threads";

    private final boolean isStubMode;
    private int stubScore = 80;
    private final int[] inputShape = new int[]{1, 3, 128, 128};

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private String modelSha;

    /**
     * Initializes the scorer.
     * <ul>
     *   <li>{@code modelPath == null}: explicit stub mode (tests / UI demos only).</li>
     *   <li>{@code modelPath != null}: the model MUST load. A missing file or a load failure throws
     *       {@link IllegalStateException}; it never silently degrades to a stub that passes everyone.</li>
     * </ul>
     */
    public MiniFasNetScorer(Path modelPath) {
        this(modelPath, null);
    }

    public MiniFasNetScorer(Path modelPath, String expectedSha256) {
        if (modelPath == null) {
            this.isStubMode = true;
            return;
        }
        if (!Files.exists(modelPath)) {
            throw new IllegalStateException("PAD model file not found: " + modelPath);
        }
        try {
            this.modelSha = liveness.util.Sha256.hex(liveness.util.Sha256.digest(Files.readAllBytes(modelPath)));
            if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                if (!this.modelSha.equalsIgnoreCase(expectedSha256.trim())) {
                    throw new SecurityException("PAD model SHA-256 mismatch: expected "
                            + expectedSha256 + " but computed " + this.modelSha);
                }
            }
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            this.session = env.createSession(modelPath.toString(), opts);
            this.inputName = session.getInputNames().iterator().next();
            requireInputShape(session, inputName);
        } catch (SecurityException e) {
            throw e;
        } catch (OrtException | java.io.IOException | RuntimeException | LinkageError e) {   // LinkageError: native ORT lib missing
            throw new IllegalStateException("PAD model failed to load: " + modelPath, e);
        }
        this.isStubMode = false;
    }

    /**
     * Loads the model from in-memory bytes (Android assets, Flutter bundles). Same fail-closed contract as the Path
     * constructors, and stricter on pinning: because the bytes arrive from outside this class, the SHA-256 pin is
     * mandatory here. There is no stub mode and no unpinned path.
     *
     * @throws IllegalArgumentException null/empty bytes, or a missing/blank pin
     * @throws SecurityException        the bytes do not hash to {@code expectedSha256}
     * @throws IllegalStateException    the runtime could not load the model or its input shape is wrong
     */
    public MiniFasNetScorer(byte[] modelBytes, String expectedSha256) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("PAD model bytes empty");
        }
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            throw new IllegalArgumentException("PAD model SHA-256 pin is required for in-memory model bytes");
        }
        this.modelSha = liveness.util.Sha256.hex(liveness.util.Sha256.digest(modelBytes));
        if (!this.modelSha.equalsIgnoreCase(expectedSha256.trim())) {
            throw new SecurityException("PAD model SHA-256 mismatch: expected "
                    + expectedSha256 + " but computed " + this.modelSha);
        }
        try {
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            this.session = env.createSession(modelBytes, opts);
            this.inputName = session.getInputNames().iterator().next();
            requireInputShape(session, inputName);
        } catch (OrtException | RuntimeException | LinkageError e) {   // LinkageError: native ORT lib missing
            throw new IllegalStateException("PAD model failed to load from bytes (sha256 " + this.modelSha + ")", e);
        }
        this.isStubMode = false;
    }

    /** R1: the input contract comes from the model file, not from this class. */
    private static void requireInputShape(OrtSession session, String inputName) throws OrtException {
        ai.onnxruntime.NodeInfo ni = session.getInputInfo().get(inputName);
        long[] shp = ((ai.onnxruntime.TensorInfo) ni.getInfo()).getShape();
        if (shp.length != 4 || shp[1] != 3 || shp[2] != 128 || shp[3] != 128) {
            throw new IllegalStateException("Unsupported PAD input shape " + java.util.Arrays.toString(shp)
                    + "; expected [N,3,128,128]. Update PadPreprocessor with the model.");
        }
    }

    public MiniFasNetScorer(ModelRegistry registry, String modelName) {
        this(java.util.Objects.requireNonNull(registry, "registry").getModelPath(modelName),
                registry.getMetadata(modelName) != null ? registry.getMetadata(modelName).sha256() : null);
    }

    public void setStubScore(int score) {
        liveness.policy.StubPolicy.check(true, "MiniFasNetScorer.setStubScore");
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

        float[] logits = rawLogits(preprocessedInput);
        // ModelQualificationTest (§3.1): Class 0 = live (bona fide), Class 1 = spoof (attack)
        float liveLogit = logits[0];
        float spoofLogit = logits[1];

        // Softmax probability for live class
        float maxLogit = Math.max(liveLogit, spoofLogit);
        float expLive = (float) Math.exp(liveLogit - maxLogit);
        float expSpoof = (float) Math.exp(spoofLogit - maxLogit);
        float liveProb = expLive / (expLive + expSpoof);

        return Math.round(liveProb * 100.0f);
    }

    /**
     * Raw model output {liveLogit, spoofLogit} for one preprocessed crop (same input contract as
     * {@link #score(float[])}). Exists so a device's output can be compared with the desktop's for the same model
     * file and the same input (AUDIT_TODO X2); the product decision path still goes through {@link #score}.
     *
     * @throws IllegalStateException in stub mode (a stub has no model output)
     */
    public float[] rawLogits(float[] preprocessedInput) {
        if (isStubMode) {
            throw new IllegalStateException("stub scorer has no logits");
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
                return new float[]{logits[0][0], logits[0][1]};
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
        return isStubMode ? "STUB-v0.0.0" : "MiniFASNetV2-SE-sha256:" + modelSha.substring(0, 16);
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
