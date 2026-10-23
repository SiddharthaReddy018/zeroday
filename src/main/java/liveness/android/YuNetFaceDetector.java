package liveness.android;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import liveness.engine.LandmarkLayout;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * YuNet face detection through the project's existing ONNX Runtime dependency.
 *
 * <p>The model contract is pinned to the locally supplied OpenCV Zoo YuNet ONNX file.
 * Input frames are interleaved RGB; the model expects an unnormalised BGR NCHW tensor
 * of shape [1,3,640,640]. Images are resized without distortion and padded on the
 * right/bottom. The decoder follows OpenCV FaceDetectorYN's cls/objectness, bbox,
 * five-keypoint and NMS conventions.
 *
 * <p>IMPORTANT: YuNet supplies only a box and five SFace alignment points. It does not
 * supply eye contours, mouth contours, or 3D head pose. Missing eye/mouth coordinates
 * are NaN, and unavailable pose is represented by a very large finite sentinel so the
 * current quality gate rejects a face rather than silently treating it as frontal.
 * This class is suitable for detector latency and SFace alignment work; it is NOT a
 * complete production liveness challenge detector.
 */
public final class YuNetFaceDetector implements FaceDetector, AutoCloseable {

    public static final int INPUT_SIZE = 640;
    public static final String MODEL_SHA256 =
            "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4";

    // Defaults copied from tools/calibrate_same_face.py's existing FaceDetectorYN.create
    // call. They are reference parameters, not thresholds tuned to these decoder tests.
    static final double SCORE_THRESHOLD = 0.6;
    static final double NMS_THRESHOLD = 0.3;
    static final int TOP_K = 5000;
    static final double UNAVAILABLE_POSE_DEGREES = Double.MAX_VALUE;

    static final String[] OUTPUT_NAMES = {
            "cls_8", "cls_16", "cls_32",
            "obj_8", "obj_16", "obj_32",
            "bbox_8", "bbox_16", "bbox_32",
            "kps_8", "kps_16", "kps_32"
    };
    private static final int[] STRIDES = {8, 16, 32};
    private static final int[] CHANNELS = {1, 1, 1, 1, 1, 1, 4, 4, 4, 10, 10, 10};
    private static final int PLANE_SIZE = INPUT_SIZE * INPUT_SIZE;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String modelSha256;
    private final float[] inputBuffer = new float[3 * PLANE_SIZE];
    private boolean closed;

    /** Opens only the model whose SHA-256 is pinned by {@link #MODEL_SHA256}. */
    public YuNetFaceDetector(Path modelPath) {
        if (modelPath == null || !Files.isRegularFile(modelPath)) {
            throw new IllegalArgumentException("YuNet model file not found: " + modelPath);
        }

        final byte[] modelBytes;
        try {
            modelBytes = Files.readAllBytes(modelPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read YuNet model: " + modelPath, e);
        }
        if (modelBytes.length == 0) {
            throw new IllegalArgumentException("YuNet model bytes empty");
        }
        String actualSha = liveness.util.Sha256.hex(liveness.util.Sha256.digest(modelBytes));
        if (!MODEL_SHA256.equalsIgnoreCase(actualSha)) {
            throw new SecurityException("YuNet model SHA-256 mismatch: expected "
                    + MODEL_SHA256 + " but computed " + actualSha);
        }

        OrtEnvironment localEnv = null;
        OrtSession localSession = null;
        try {
            localEnv = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            try {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                // Use the exact byte array that was hashed above (no path TOCTOU).
                localSession = localEnv.createSession(modelBytes, options);
            } finally {
                options.close();
            }
            validateModelContract(localSession);
        } catch (OrtException | RuntimeException | LinkageError e) {
            closeQuietly(localSession);
            closeEnvironmentQuietly(localEnv);
            throw new IllegalStateException("YuNet ONNX model failed to load or validate", e);
        }
        this.env = localEnv;
        this.session = localSession;
        this.modelSha256 = actualSha;
    }

    public String modelSha256() {
        return modelSha256;
    }

    /** Full cost of input validation, preprocessing, ONNX inference, decoding, and NMS. */
    @Override
    public synchronized Observation detect(byte[] rgb, int width, int height) {
        if (closed) throw new IllegalStateException("YuNet detector is closed");
        if (rgb == null || width <= 0 || height <= 0
                || (long) rgb.length < (long) width * height * 3L) {
            throw new IllegalArgumentException("Invalid RGB buffer for YuNet detection");
        }

        ResizeInfo resize = prepareInput(rgb, width, height);
        try (OnnxTensor input = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(inputBuffer), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result result = session.run(Collections.singletonMap("input", input))) {
            float[][][][] outputs = new float[OUTPUT_NAMES.length][][][];
            for (int i = 0; i < OUTPUT_NAMES.length; i++) {
                String name = OUTPUT_NAMES[i];
                outputs[i] = (float[][][]) result.get(name)
                        .orElseThrow(() -> new IllegalStateException("Missing YuNet output: " + name))
                        .getValue();
            }
            return decode(outputs, width, height, resize.scaleX, resize.scaleY);
        } catch (OrtException e) {
            throw new IllegalStateException("YuNet ONNX inference failed", e);
        }
    }

    private ResizeInfo prepareInput(byte[] rgb, int width, int height) {
        double scale = Math.min(INPUT_SIZE / (double) width, INPUT_SIZE / (double) height);
        int resizedWidth = Math.max(1, Math.min(INPUT_SIZE, (int) Math.round(width * scale)));
        int resizedHeight = Math.max(1, Math.min(INPUT_SIZE, (int) Math.round(height * scale)));
        double scaleX = resizedWidth / (double) width;
        double scaleY = resizedHeight / (double) height;

        // The input buffer is reused across frames; clear padding and stale pixels.
        Arrays.fill(inputBuffer, 0.0f);
        for (int y = 0; y < resizedHeight; y++) {
            double sourceY = clamp((y + 0.5) / scaleY - 0.5, 0.0, height - 1.0);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(height - 1, y0 + 1);
            double fy = sourceY - y0;
            for (int x = 0; x < resizedWidth; x++) {
                double sourceX = clamp((x + 0.5) / scaleX - 0.5, 0.0, width - 1.0);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(width - 1, x0 + 1);
                double fx = sourceX - x0;
                int pixel = y * INPUT_SIZE + x;
                int src00 = (y0 * width + x0) * 3;
                int src10 = (y0 * width + x1) * 3;
                int src01 = (y1 * width + x0) * 3;
                int src11 = (y1 * width + x1) * 3;

                // The API input is RGB; the exported YuNet graph expects BGR.
                inputBuffer[pixel] = (float) interpolate(rgb, src00, src10, src01, src11, 2, fx, fy);
                inputBuffer[PLANE_SIZE + pixel] =
                        (float) interpolate(rgb, src00, src10, src01, src11, 1, fx, fy);
                inputBuffer[2 * PLANE_SIZE + pixel] =
                        (float) interpolate(rgb, src00, src10, src01, src11, 0, fx, fy);
            }
        }
        return new ResizeInfo(scaleX, scaleY);
    }

    private static double interpolate(byte[] rgb, int p00, int p10, int p01, int p11,
                                      int channel, double fx, double fy) {
        double v00 = rgb[p00 + channel] & 0xff;
        double v10 = rgb[p10 + channel] & 0xff;
        double v01 = rgb[p01 + channel] & 0xff;
        double v11 = rgb[p11 + channel] & 0xff;
        double top = v00 * (1.0 - fx) + v10 * fx;
        double bottom = v01 * (1.0 - fx) + v11 * fx;
        return top * (1.0 - fy) + bottom * fy;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * Decoder seam for deterministic JVM tests. Tensor order matches the exported model:
     * cls heads, objectness heads, bbox heads, then keypoint heads for strides 8/16/32.
     */
    static Observation decode(float[][][][] outputs, int frameWidth, int frameHeight,
                              double scaleX, double scaleY) {
        if (outputs == null || outputs.length != OUTPUT_NAMES.length) {
            throw new IllegalArgumentException("YuNet requires exactly 12 output tensors");
        }
        if (frameWidth <= 0 || frameHeight <= 0 || !Double.isFinite(scaleX) || scaleX <= 0.0
                || !Double.isFinite(scaleY) || scaleY <= 0.0) {
            throw new IllegalArgumentException("Invalid frame dimensions or resize scale");
        }

        float[][][] cls = new float[3][][];
        float[][][] obj = new float[3][][];
        float[][][] bbox = new float[3][][];
        float[][][] kps = new float[3][][];
        for (int level = 0; level < STRIDES.length; level++) {
            int grid = INPUT_SIZE / STRIDES[level];
            int anchors = grid * grid;
            cls[level] = requireTensor(outputs, level, anchors, CHANNELS[level]);
            obj[level] = requireTensor(outputs, level + 3, anchors, CHANNELS[level + 3]);
            bbox[level] = requireTensor(outputs, level + 6, anchors, CHANNELS[level + 6]);
            kps[level] = requireTensor(outputs, level + 9, anchors, CHANNELS[level + 9]);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            int grid = INPUT_SIZE / stride;
            for (int row = 0; row < grid; row++) {
                for (int col = 0; col < grid; col++) {
                    int index = row * grid + col;
                    float[] clsRow = row(cls[level], index, 1, "cls");
                    float[] objRow = row(obj[level], index, 1, "obj");
                    float clsScore = clsRow[0];
                    float objScore = objRow[0];
                    if (!Float.isFinite(clsScore) || !Float.isFinite(objScore)) continue;
                    clsScore = Math.max(0.0f, Math.min(1.0f, clsScore));
                    objScore = Math.max(0.0f, Math.min(1.0f, objScore));
                    // Match OpenCV FaceDetectorYN's FP32 decode arithmetic. These are
                    // FP32 model outputs; promoting bbox values to double before exp/box
                    // arithmetic can move an integral edge below the boundary before floor().
                    float score = (float) Math.sqrt(clsScore * objScore);
                    if (score < SCORE_THRESHOLD) continue;

                    float[] box = row(bbox[level], index, 4, "bbox");
                    float[] points = row(kps[level], index, 10, "kps");
                    float cx = (col + box[0]) * stride;
                    float cy = (row + box[1]) * stride;
                    float bw = (float) Math.exp(box[2]) * stride;
                    float bh = (float) Math.exp(box[3]) * stride;
                    if (!Float.isFinite(cx) || !Float.isFinite(cy)
                            || !Float.isFinite(bw) || !Float.isFinite(bh) || bw <= 0.0f || bh <= 0.0f) {
                        continue;
                    }
                    float x = cx - bw / 2.0f;
                    float y = cy - bh / 2.0f;
                    float rawRight = x + bw;
                    float rawBottom = y + bh;
                    if (!Double.isFinite(x) || !Double.isFinite(y)
                            || !Double.isFinite(rawRight) || !Double.isFinite(rawBottom)) continue;

                    x /= scaleX;
                    y /= scaleY;
                    double right = rawRight / scaleX;
                    double bottom = rawBottom / scaleY;
                    double clippedX = clamp(x, 0.0, frameWidth);
                    double clippedY = clamp(y, 0.0, frameHeight);
                    double clippedRight = clamp(right, 0.0, frameWidth);
                    double clippedBottom = clamp(bottom, 0.0, frameHeight);
                    if (clippedRight <= clippedX || clippedBottom <= clippedY) continue;

                    double[] alignment = new double[10];
                    boolean finitePoints = true;
                    for (int p = 0; p < 5; p++) {
                        double px = (points[2 * p] + col) * stride / scaleX;
                        double py = (points[2 * p + 1] + row) * stride / scaleY;
                        if (!Double.isFinite(px) || !Double.isFinite(py)) {
                            finitePoints = false;
                            break;
                        }
                        alignment[2 * p] = px;
                        alignment[2 * p + 1] = py;
                    }
                    if (!finitePoints) continue;
                    candidates.add(new Candidate(clippedX, clippedY,
                            clippedRight - clippedX, clippedBottom - clippedY, score, alignment));
                }
            }
        }

        if (candidates.isEmpty()) return Observation.NONE;
        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (candidates.size() > TOP_K) candidates.subList(TOP_K, candidates.size()).clear();

        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean suppressed = false;
            for (Candidate prior : kept) {
                if (intersectionOverUnion(candidate, prior) > NMS_THRESHOLD) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) kept.add(candidate);
        }
        if (kept.isEmpty()) return Observation.NONE;

        Candidate best = kept.get(0);
        // FaceDetector.Observation stores integer boxes. Round outward so the integer
        // rectangle encloses the decoded FP32 box instead of shrinking it.
        int left = (int) Math.floor(best.x);
        int top = (int) Math.floor(best.y);
        int right = (int) Math.ceil(best.x + best.w);
        int bottom = (int) Math.ceil(best.y + best.h);
        double[] landmarks = new double[LandmarkLayout.FULL_LENGTH];
        Arrays.fill(landmarks, Double.NaN);
        System.arraycopy(best.alignment, 0, landmarks, LandmarkLayout.ALIGN5, best.alignment.length);
        return new Observation(kept.size(), left, top, right - left, bottom - top,
                UNAVAILABLE_POSE_DEGREES, UNAVAILABLE_POSE_DEGREES, UNAVAILABLE_POSE_DEGREES, landmarks);
    }

    private static float[][] requireTensor(float[][][][] outputs, int outputIndex, int anchors, int channels) {
        float[][][] tensor = outputs[outputIndex];
        if (tensor == null || tensor.length != 1 || tensor[0] == null
                || tensor[0].length != anchors
                || (anchors > 0 && (tensor[0][0] == null || tensor[0][0].length != channels))) {
            throw new IllegalArgumentException("Malformed YuNet tensor " + OUTPUT_NAMES[outputIndex]
                    + "; expected [1," + anchors + "," + channels + "]");
        }
        return tensor[0];
    }

    private static float[] row(float[][] tensor, int index, int channels, String name) {
        if (index < 0 || index >= tensor.length || tensor[index] == null || tensor[index].length != channels) {
            throw new IllegalArgumentException("Malformed YuNet " + name + " row at anchor " + index);
        }
        return tensor[index];
    }

    private static double intersectionOverUnion(Candidate a, Candidate b) {
        double left = Math.max(a.x, b.x);
        double top = Math.max(a.y, b.y);
        double right = Math.min(a.x + a.w, b.x + b.w);
        double bottom = Math.min(a.y + a.h, b.y + b.h);
        double intersection = Math.max(0.0, right - left) * Math.max(0.0, bottom - top);
        double union = a.w * a.h + b.w * b.h - intersection;
        return union <= 0.0 ? 0.0 : intersection / union;
    }

    private static void validateModelContract(OrtSession modelSession) throws OrtException {
        Map<String, NodeInfo> inputs = modelSession.getInputInfo();
        if (inputs.size() != 1 || !inputs.containsKey("input")) {
            throw new IllegalStateException("Unsupported YuNet input names: " + inputs.keySet());
        }
        requireModelShape(inputs, "input", new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});

        Map<String, NodeInfo> outputs = modelSession.getOutputInfo();
        if (outputs.size() != OUTPUT_NAMES.length) {
            throw new IllegalStateException("Expected 12 YuNet output tensors, got " + outputs.keySet());
        }
        for (int level = 0; level < STRIDES.length; level++) {
            int anchors = (INPUT_SIZE / STRIDES[level]) * (INPUT_SIZE / STRIDES[level]);
            requireModelShape(outputs, OUTPUT_NAMES[level], new long[]{1, anchors, 1});
            requireModelShape(outputs, OUTPUT_NAMES[level + 3], new long[]{1, anchors, 1});
            requireModelShape(outputs, OUTPUT_NAMES[level + 6], new long[]{1, anchors, 4});
            requireModelShape(outputs, OUTPUT_NAMES[level + 9], new long[]{1, anchors, 10});
        }
    }

    private static void requireModelShape(Map<String, NodeInfo> nodes, String name, long[] expected) {
        NodeInfo node = nodes.get(name);
        if (node == null || !(node.getInfo() instanceof TensorInfo)) {
            throw new IllegalStateException("Missing or non-tensor YuNet value: " + name);
        }
        long[] actual = ((TensorInfo) node.getInfo()).getShape();
        if (!Arrays.equals(actual, expected)) {
            throw new IllegalStateException("Unexpected YuNet shape for " + name + ": "
                    + Arrays.toString(actual) + ", expected " + Arrays.toString(expected));
        }
    }

    private static void closeQuietly(OrtSession s) {
        if (s != null) try { s.close(); } catch (OrtException ignored) {}
    }

    private static void closeEnvironmentQuietly(OrtEnvironment e) {
        if (e != null) e.close();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        closeQuietly(session);
        closeEnvironmentQuietly(env);
    }

    private static final class ResizeInfo {
        final double scaleX;
        final double scaleY;
        ResizeInfo(double scaleX, double scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    private static final class Candidate {
        final double x, y, w, h, score;
        final double[] alignment;
        Candidate(double x, double y, double w, double h, double score, double[] alignment) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.score = score;
            this.alignment = alignment;
        }
    }
}
