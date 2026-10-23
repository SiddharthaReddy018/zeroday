package liveness.android;

import liveness.engine.LandmarkLayout;

import java.util.Arrays;

/**
 * Combines two detectors behind the {@link FaceDetector} contract so each supplies only what it is
 * trustworthy for.
 *
 * <ul>
 *   <li>{@code counter} (YuNet): face count, bounding box and the five SFace alignment points
 *       (the points the 0.48 same-face threshold was calibrated with).</li>
 *   <li>{@code contours} (ML Kit): eye/mouth contours (landmark indices 0..31) and head pose.</li>
 * </ul>
 *
 * <p>Fail-closed rules. Any frame that cannot be fully explained by both detectors is returned
 * with the pose set to {@link #UNAVAILABLE_POSE_DEGREES} and no contour block, so the quality gate
 * rejects it:
 * <ul>
 *   <li>counter sees no face: {@link Observation#NONE}.</li>
 *   <li>counter sees more than one face: its observation is returned and the contour detector
 *       is not run (the face count alone rejects the frame).</li>
 *   <li>contour detector sees no face, has no contour block, or reports a face that does not
 *       coincide with the counter's face (each box centre must lie inside the other box).</li>
 *   <li>counter's alignment points are missing or not finite.</li>
 * </ul>
 * The reported face count is the larger of the two detectors' counts. Exceptions from either
 * detector propagate; they are never converted into a passing observation.
 *
 * <p>Alignment points are never taken from the contour detector, so this class does not depend on
 * that vendor's left/right labelling for SFace alignment.
 */
public final class CompositeFaceDetector implements FaceDetector, AutoCloseable {

    public static final double UNAVAILABLE_POSE_DEGREES = Double.MAX_VALUE;

    private final FaceDetector counter;
    private final FaceDetector contours;

    public CompositeFaceDetector(FaceDetector counter, FaceDetector contours) {
        if (counter == null || contours == null) {
            throw new IllegalArgumentException("both detectors are required");
        }
        if (counter == contours) {
            throw new IllegalArgumentException("counter and contour detectors must be different instances");
        }
        this.counter = counter;
        this.contours = contours;
    }

    @Override
    public Observation detect(byte[] rgb, int width, int height) {
        Observation c = counter.detect(rgb, width, height);
        if (c == null || c.faceCount <= 0) return Observation.NONE;
        if (c.faceCount > 1) return degraded(c, c.faceCount);
        if (!LandmarkLayout.hasAlign5(c.landmarks) || c.bboxW <= 0 || c.bboxH <= 0) {
            return degraded(c, c.faceCount);
        }

        Observation k = contours.detect(rgb, width, height);
        if (k == null || k.faceCount <= 0) return degraded(c, c.faceCount);
        int count = Math.max(c.faceCount, k.faceCount);
        if (count > 1) return degraded(c, count);
        if (k.bboxW <= 0 || k.bboxH <= 0 || !LandmarkLayout.hasBase(k.landmarks)) {
            return degraded(c, count);
        }
        if (!centreInside(c, k) || !centreInside(k, c)) return degraded(c, count);
        if (!Double.isFinite(k.yawDeg) || !Double.isFinite(k.pitchDeg) || !Double.isFinite(k.rollDeg)) {
            return degraded(c, count);
        }

        double[] merged = new double[LandmarkLayout.FULL_LENGTH];
        Arrays.fill(merged, Double.NaN);
        System.arraycopy(k.landmarks, 0, merged, 0, LandmarkLayout.BASE_LENGTH);
        System.arraycopy(c.landmarks, LandmarkLayout.ALIGN5, merged, LandmarkLayout.ALIGN5, 10);
        return new Observation(count, c.bboxX, c.bboxY, c.bboxW, c.bboxH,
                k.yawDeg, k.pitchDeg, k.rollDeg, merged);
    }

    private static Observation degraded(Observation c, int count) {
        return new Observation(count, c.bboxX, c.bboxY, c.bboxW, c.bboxH,
                UNAVAILABLE_POSE_DEGREES, UNAVAILABLE_POSE_DEGREES, UNAVAILABLE_POSE_DEGREES, null);
    }

    /** True when the centre of {@code inner}'s box lies inside {@code outer}'s box. */
    private static boolean centreInside(Observation inner, Observation outer) {
        double cx = inner.bboxX + inner.bboxW / 2.0;
        double cy = inner.bboxY + inner.bboxH / 2.0;
        return cx >= outer.bboxX && cx <= outer.bboxX + outer.bboxW
                && cy >= outer.bboxY && cy <= outer.bboxY + outer.bboxH;
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (FaceDetector d : new FaceDetector[]{counter, contours}) {
            if (d instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) d).close();
                } catch (Exception e) {
                    if (first == null) first = e; else first.addSuppressed(e);
                }
            }
        }
        if (first != null) throw first;
    }
}
