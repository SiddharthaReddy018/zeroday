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

    public SFaceEmbedder(Path modelPath) {
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

    public SFaceEmbedder(byte[] modelBytes) {
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
    public float[] embed(byte[] rgb, int width, int height) {
        if (rgb == null || width <= 0 || height <= 0 || rgb.length < width * height * 3) {
            throw new IllegalArgumentException("Invalid RGB buffer for SFace embedding");
        }

        // Crop centered square and resize to 112x112 (BGR layout [1, 3, 112, 112] matching OpenCV SFace)
        float[] tensorData = preprocess(rgb, width, height);

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
