package liveness.android;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Production face recognition embedder using OpenCV Zoo SFace ONNX model (112x112 input, 128-D output).
 * Produces L2-normalized 128-dimensional identity embeddings.
 * Calibrated threshold with SFace: 0.48 cosine distance (§4.2, ADR-012).
 */
public final class SFaceEmbedder implements FaceEmbedder, AutoCloseable {

    public static final double CALIBRATED_THRESHOLD = 0.48;
    public static final int INPUT_SIZE = 112;
    public static final int EMBEDDING_DIM = 128;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    /** When non-null, embed() detects + aligns (production path). When null: legacy central crop. */
    private final FaceDetector detector;

    public SFaceEmbedder(Path modelPath) {
        this(modelPath, (FaceDetector) null);
    }

    /**
     * Unpinned in-memory model. Nothing checks that these bytes are the SFace model you expect.
     *
     * @deprecated use {@link #SFaceEmbedder(byte[], String, FaceDetector)}, which pins the SHA-256
     *             and fails closed on a mismatch.
     */
    @Deprecated
    public SFaceEmbedder(byte[] modelBytes) {
        this(modelBytes, (FaceDetector) null);
    }

    public SFaceEmbedder(Path modelPath, FaceDetector detector) {
        this.detector = detector;
        if (modelPath == null || !Files.exists(modelPath)) {
            throw new IllegalArgumentException("SFace model file not found: " + modelPath);
        }
        try {
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            this.session = env.createSession(modelPath.toString(), opts);
            this.inputName = session.getInputNames().iterator().next();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to initialize SFace ONNX session", e);
        }
    }

    /**
     * Pinned in-memory model (production path). The bytes are hashed and compared with
     * {@code expectedSha256} before any ONNX session is created.
     *
     * @param expectedSha256 mandatory SHA-256 of the SFace model, hex (case-insensitive)
     * @throws IllegalArgumentException null/empty bytes, or a missing/blank pin
     * @throws SecurityException        the bytes do not hash to {@code expectedSha256}
     * @throws IllegalStateException    the bytes match the pin but are not a loadable model
     */
    public SFaceEmbedder(byte[] modelBytes, String expectedSha256, FaceDetector detector) {
        this(verifiedModelBytes(modelBytes, expectedSha256), detector);
    }

    private static byte[] verifiedModelBytes(byte[] modelBytes, String expectedSha256) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("SFace model bytes empty");
        }
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            throw new IllegalArgumentException("SFace model SHA-256 pin is required for in-memory model bytes");
        }
        String actual = liveness.util.Sha256.hex(liveness.util.Sha256.digest(modelBytes));
        if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
            throw new SecurityException("SFace model SHA-256 mismatch: expected "
                    + expectedSha256 + " but computed " + actual);
        }
        return modelBytes;
    }

    /**
     * Unpinned in-memory model. Nothing checks that these bytes are the SFace model you expect.
     *
     * @deprecated use {@link #SFaceEmbedder(byte[], String, FaceDetector)}, which pins the SHA-256
     *             and fails closed on a mismatch.
     */
    @Deprecated
    public SFaceEmbedder(byte[] modelBytes, FaceDetector detector) {
        this.detector = detector;
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("SFace model bytes empty");
        }
        try {
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            this.session = env.createSession(modelBytes, opts);
            this.inputName = session.getInputNames().iterator().next();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to initialize SFace ONNX session", e);
        }
    }

    @Override
    public boolean productionGrade() {
        // Only the aligned path matches how the 0.48 threshold was defined (OpenCV alignCrop).
        return detector != null;
    }

    @Override
    public float[] embed(byte[] rgb, int width, int height) {
        if (rgb == null || width <= 0 || height <= 0 || rgb.length < width * height * 3) {
            throw new IllegalArgumentException("Invalid RGB buffer for SFace embedding");
        }
        if (detector == null) {
            // legacy, unaligned path (tests / dev only; not production grade)
            return run(preprocess(rgb, width, height));
        }
        FaceDetector.Observation o = detector.detect(rgb, width, height);
        if (o == null || o.faceCount != 1) {
            throw new IllegalStateException("SFace alignment needs exactly one face, got "
                    + (o == null ? 0 : o.faceCount));
        }
        double[] pts = liveness.engine.LandmarkLayout.align5(o.landmarks);
        if (pts == null) {
            throw new IllegalStateException("Detector returned no 5-point alignment landmarks");
        }
        return embedAligned(FaceAligner.alignCrop(rgb, width, height, pts));
    }

    /** Embed an already aligned 112x112 interleaved RGB crop. */
    public float[] embedAligned(byte[] rgb112) {
        if (rgb112 == null || rgb112.length != INPUT_SIZE * INPUT_SIZE * 3) {
            throw new IllegalArgumentException("Aligned crop must be 112x112 RGB");
        }
        float[] out = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int plane = INPUT_SIZE * INPUT_SIZE;
        for (int i = 0; i < plane; i++) {
            out[i] = rgb112[i * 3 + 2] & 0xFF;          // B
            out[plane + i] = rgb112[i * 3 + 1] & 0xFF;  // G
            out[2 * plane + i] = rgb112[i * 3] & 0xFF;  // R
        }
        return run(out);
    }

    private float[] run(float[] tensorData) {
        try {
            long[] shape = new long[]{1, 3, INPUT_SIZE, INPUT_SIZE};
            FloatBuffer buffer = FloatBuffer.wrap(tensorData);
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {

                float[][] output = (float[][]) result.get(0).getValue();
                float[] feat = output[0];

                // L2-normalize
                double normSq = 0.0;
                for (float v : feat) {
                    normSq += v * v;
                }
                double norm = Math.sqrt(normSq);
                float[] normalized = new float[EMBEDDING_DIM];
                if (norm > 1e-6) {
                    for (int i = 0; i < EMBEDDING_DIM; i++) {
                        normalized[i] = (float) (feat[i] / norm);
                    }
                }
                return normalized;
            }
        } catch (OrtException e) {
            throw new RuntimeException("SFace embedding inference failed", e);
        }
    }

    private static float[] preprocess(byte[] rgb, int width, int height) {
        float[] out = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int plane = INPUT_SIZE * INPUT_SIZE;
        // Central square crop
        int side = Math.min(width, height);
        int x0 = (width - side) / 2;
        int y0 = (height - side) / 2;
        double scale = (double) side / INPUT_SIZE;

        for (int oy = 0; oy < INPUT_SIZE; oy++) {
            int sy = y0 + (int) Math.min(side - 1, Math.round(oy * scale));
            for (int ox = 0; ox < INPUT_SIZE; ox++) {
                int sx = x0 + (int) Math.min(side - 1, Math.round(ox * scale));
                int idx = (sy * width + sx) * 3;
                float r = rgb[idx] & 0xFF;
                float g = rgb[idx + 1] & 0xFF;
                float b = rgb[idx + 2] & 0xFF;
                int outIdx = oy * INPUT_SIZE + ox;
                // BGR planar order for OpenCV SFace model: channel 0 = B, 1 = G, 2 = R
                out[outIdx] = b;
                out[plane + outIdx] = g;
                out[2 * plane + outIdx] = r;
            }
        }
        return out;
    }

    @Override
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException ignored) {}
        }
    }
}
