package liveness.android;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
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
    static final float SCORE_THRESHOLD = 0.6f;
    static final float NMS_THRESHOLD = 0.3f;
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

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String modelSha256;
    private final YuNetInputPreprocessor preprocessor = new YuNetInputPreprocessor();
    private final float[][] flatOutputs = allocateFlatOutputs();
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

    /** Stage names for {@link #detectTimed}, in execution order. */
    static final String[] STAGE_NAMES = {
            "prepare_input", "tensor_create", "ort_run", "output_extract", "decode_nms"
    };

    /** Full cost of input validation, preprocessing, ONNX inference, decoding, and NMS. */
    @Override
    public synchronized Observation detect(byte[] rgb, int width, int height) {
        return detectInternal(rgb, width, height, null);
    }

    /**
     * Same work and same result as {@link #detect}, additionally writing the nanoseconds spent in each
     * stage of {@link #STAGE_NAMES} into {@code stageNanos}. Benchmark use only. Closing the tensor and
     * result is not attributed to any stage.
     */
    synchronized Observation detectTimed(byte[] rgb, int width, int height, long[] stageNanos) {
        if (stageNanos == null || stageNanos.length != STAGE_NAMES.length) {
            throw new IllegalArgumentException("stageNanos must have length " + STAGE_NAMES.length);
        }
        return detectInternal(rgb, width, height, stageNanos);
    }

    private static long mark(long[] stageNanos) {
        return stageNanos == null ? 0L : System.nanoTime();
    }

    private Observation detectInternal(byte[] rgb, int width, int height, long[] stageNanos) {
        if (closed) throw new IllegalStateException("YuNet detector is closed");
        if (rgb == null || width <= 0 || height <= 0
                || (long) rgb.length < (long) width * height * 3L) {
            throw new IllegalArgumentException("Invalid RGB buffer for YuNet detection");
        }

        long t0 = mark(stageNanos);
        YuNetInputPreprocessor.Resize resize = preprocessor.prepare(rgb, width, height);
        long t1 = mark(stageNanos);
        try (OnnxTensor input = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(preprocessor.buffer()), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE})) {
            long t2 = mark(stageNanos);
            try (OrtSession.Result result = session.run(Collections.singletonMap("input", input))) {
                long t3 = mark(stageNanos);
                extractOutputs(result);
                long t4 = mark(stageNanos);
                Observation observation = decodeFlat(flatOutputs, width, height, resize.scaleX, resize.scaleY);
                if (stageNanos != null) {
                    long t5 = System.nanoTime();
                    stageNanos[0] = t1 - t0;
                    stageNanos[1] = t2 - t1;
                    stageNanos[2] = t3 - t2;
                    stageNanos[3] = t4 - t3;
                    stageNanos[4] = t5 - t4;
                }
                return observation;
            }
        } catch (OrtException e) {
            throw new IllegalStateException("YuNet ONNX inference failed", e);
        }
    }

    /** Copies each output tensor into its preallocated flat array (bulk copy, no per-row allocation). */
    private void extractOutputs(OrtSession.Result result) throws OrtException {
        for (int i = 0; i < OUTPUT_NAMES.length; i++) {
            String name = OUTPUT_NAMES[i];
            OnnxValue value = result.get(name)
                    .orElseThrow(() -> new IllegalStateException("Missing YuNet output: " + name));
            if (!(value instanceof OnnxTensor)) {
                throw new IllegalStateException("YuNet output is not a tensor: " + name);
            }
            FloatBuffer data = ((OnnxTensor) value).getFloatBuffer();
            float[] dest = flatOutputs[i];
            if (data == null || data.remaining() != dest.length) {
                throw new IllegalStateException("Unexpected YuNet output size for " + name);
            }
            data.get(dest);
        }
    }

    private static float[][] allocateFlatOutputs() {
        float[][] flat = new float[OUTPUT_NAMES.length][];
        for (int i = 0; i < OUTPUT_NAMES.length; i++) flat[i] = new float[flatLength(i)];
        return flat;
    }

    private static int flatLength(int outputIndex) {
        int grid = INPUT_SIZE / STRIDES[outputIndex % 3];
        return grid * grid * CHANNELS[outputIndex];
    }

    /** Last extracted flat outputs, copied. Test hook for comparing extraction against ORT's getValue(). */
    synchronized float[][] flatOutputsSnapshot() {
        float[][] copy = new float[flatOutputs.length][];
        for (int i = 0; i < copy.length; i++) copy[i] = flatOutputs[i].clone();
        return copy;
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
        float[][] flat = new float[OUTPUT_NAMES.length][];
        for (int i = 0; i < OUTPUT_NAMES.length; i++) {
            int grid = INPUT_SIZE / STRIDES[i % 3];
            int anchors = grid * grid;
            float[][] tensor = requireTensor(outputs, i, anchors, CHANNELS[i]);
            float[] values = new float[anchors * CHANNELS[i]];
            for (int a = 0; a < anchors; a++) {
                if (tensor[a] == null || tensor[a].length != CHANNELS[i]) {
                    throw new IllegalArgumentException("Malformed YuNet " + OUTPUT_NAMES[i]
                            + " row at anchor " + a);
                }
                System.arraycopy(tensor[a], 0, values, a * CHANNELS[i], CHANNELS[i]);
            }
            flat[i] = values;
        }
        return decodeFlat(flat, frameWidth, frameHeight, scaleX, scaleY);
    }

    /** Decodes flat row-major [anchors x channels] tensors, one array per output in {@link #OUTPUT_NAMES} order. */
    static Observation decodeFlat(float[][] flat, int frameWidth, int frameHeight,
                                  double scaleX, double scaleY) {
        if (flat == null || flat.length != OUTPUT_NAMES.length) {
            throw new IllegalArgumentException("YuNet requires exactly 12 output tensors");
        }
        if (frameWidth <= 0 || frameHeight <= 0 || !Double.isFinite(scaleX) || scaleX <= 0.0
                || !Double.isFinite(scaleY) || scaleY <= 0.0) {
            throw new IllegalArgumentException("Invalid frame dimensions or resize scale");
        }
        for (int i = 0; i < OUTPUT_NAMES.length; i++) {
            if (flat[i] == null || flat[i].length != flatLength(i)) {
                throw new IllegalArgumentException("Malformed YuNet tensor " + OUTPUT_NAMES[i]
                        + "; expected " + flatLength(i) + " values");
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            int grid = INPUT_SIZE / stride;
            float[] cls = flat[level];
            float[] obj = flat[level + 3];
            float[] bbox = flat[level + 6];
            float[] kps = flat[level + 9];
            for (int row = 0; row < grid; row++) {
                for (int col = 0; col < grid; col++) {
                    int index = row * grid + col;
                    float clsScore = cls[index];
                    float objScore = obj[index];
                    if (!Float.isFinite(clsScore) || !Float.isFinite(objScore)) continue;
                    clsScore = Math.max(0.0f, Math.min(1.0f, clsScore));
                    objScore = Math.max(0.0f, Math.min(1.0f, objScore));
                    // Match OpenCV FaceDetectorYN's FP32 decode arithmetic. These are
                    // FP32 model outputs; promoting bbox values to double before exp/box
                    // arithmetic can move an integral edge below the boundary before floor().
                    float score = (float) Math.sqrt(clsScore * objScore);
                    if (score < SCORE_THRESHOLD) continue;

                    int b = index * 4;
                    int k = index * 10;
                    float cx = (col + bbox[b]) * stride;
                    float cy = (row + bbox[b + 1]) * stride;
                    float bw = (float) Math.exp(bbox[b + 2]) * stride;
                    float bh = (float) Math.exp(bbox[b + 3]) * stride;
                    if (!Float.isFinite(cx) || !Float.isFinite(cy)
                            || !Float.isFinite(bw) || !Float.isFinite(bh) || bw <= 0.0f || bh <= 0.0f) {
                        continue;
                    }
                    float x1 = cx - bw / 2.0f;
                    float y1 = cy - bh / 2.0f;
                    float x2 = x1 + bw;
                    float y2 = y1 + bh;
                    if (!Float.isFinite(x1) || !Float.isFinite(y1)
                            || !Float.isFinite(x2) || !Float.isFinite(y2)) continue;

                    // Keep reference FP32 arithmetic in model coordinates, then apply the
                    // inverse resize in double precision. This avoids a second FP32 rounding
                    // step when mapping coordinates back to the original frame.
                    double x = x1 / scaleX;
                    double y = y1 / scaleY;
                    double right = x2 / scaleX;
                    double bottom = y2 / scaleY;
                    double clippedX = clamp(x, 0.0, frameWidth);
                    double clippedY = clamp(y, 0.0, frameHeight);
                    double clippedRight = clamp(right, 0.0, frameWidth);
                    double clippedBottom = clamp(bottom, 0.0, frameHeight);
                    if (clippedRight <= clippedX || clippedBottom <= clippedY) continue;

                    double[] alignment = new double[10];
                    boolean finitePoints = true;
                    for (int p = 0; p < 5; p++) {
                        double px = (kps[k + 2 * p] + col) * stride / scaleX;
                        double py = (kps[k + 2 * p + 1] + row) * stride / scaleY;
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
