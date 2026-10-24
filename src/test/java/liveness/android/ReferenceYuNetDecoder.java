package liveness.android;

import liveness.engine.LandmarkLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Frozen copy of the nested-array YuNet decoder as merged in PR #6 (058d46f). Test oracle only: the
 * production flat decoder must produce bit-identical observations.
 */
final class ReferenceYuNetDecoder {

    private ReferenceYuNetDecoder() {}

    private static final int[] STRIDES = {8, 16, 32};
    private static final int[] CHANNELS = {1, 1, 1, 1, 1, 1, 4, 4, 4, 10, 10, 10};

    static FaceDetector.Observation decode(float[][][][] outputs, int frameWidth, int frameHeight,
                              double scaleX, double scaleY) {
        if (outputs == null || outputs.length != YuNetFaceDetector.OUTPUT_NAMES.length) {
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
            int grid = YuNetFaceDetector.INPUT_SIZE / STRIDES[level];
            int anchors = grid * grid;
            cls[level] = requireTensor(outputs, level, anchors, CHANNELS[level]);
            obj[level] = requireTensor(outputs, level + 3, anchors, CHANNELS[level + 3]);
            bbox[level] = requireTensor(outputs, level + 6, anchors, CHANNELS[level + 6]);
            kps[level] = requireTensor(outputs, level + 9, anchors, CHANNELS[level + 9]);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            int grid = YuNetFaceDetector.INPUT_SIZE / stride;
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
                    if (score < YuNetFaceDetector.SCORE_THRESHOLD) continue;

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

        if (candidates.isEmpty()) return FaceDetector.Observation.NONE;
        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (candidates.size() > YuNetFaceDetector.TOP_K) candidates.subList(YuNetFaceDetector.TOP_K, candidates.size()).clear();

        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean suppressed = false;
            for (Candidate prior : kept) {
                if (intersectionOverUnion(candidate, prior) > YuNetFaceDetector.NMS_THRESHOLD) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) kept.add(candidate);
        }
        if (kept.isEmpty()) return FaceDetector.Observation.NONE;

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
        return new FaceDetector.Observation(kept.size(), left, top, right - left, bottom - top,
                YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, landmarks);
    }

    private static float[][] requireTensor(float[][][][] outputs, int outputIndex, int anchors, int channels) {
        float[][][] tensor = outputs[outputIndex];
        if (tensor == null || tensor.length != 1 || tensor[0] == null
                || tensor[0].length != anchors
                || (anchors > 0 && (tensor[0][0] == null || tensor[0][0].length != channels))) {
            throw new IllegalArgumentException("Malformed YuNet tensor " + YuNetFaceDetector.OUTPUT_NAMES[outputIndex]
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

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
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
