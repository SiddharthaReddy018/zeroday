package liveness.android;

/**
 * Host-side face observation provider for CameraX frames (e.g. ML Kit / MediaPipe
 * wrapped in the Android glue). The engine's QualityGate needs bbox, pose and
 * face count in raw physical units; those are not derivable from luma alone.
 * Implementations must be thread-safe and must not retain {@code rgb}.
 */
public interface FaceDetector {

    /** Result of one detection pass. Use {@link #NONE} when no face is found. */
    final class Observation {
        public static final Observation NONE =
                new Observation(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, null);

        public final int faceCount;
        public final int bboxX, bboxY, bboxW, bboxH;
        public final double yawDeg, pitchDeg, rollDeg;
        public final double[] landmarks; // nullable, flattened x,y pairs

        public Observation(int faceCount, int bboxX, int bboxY, int bboxW, int bboxH,
                           double yawDeg, double pitchDeg, double rollDeg, double[] landmarks) {
            this.faceCount = faceCount;
            this.bboxX = bboxX;
            this.bboxY = bboxY;
            this.bboxW = bboxW;
            this.bboxH = bboxH;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.landmarks = landmarks;
        }
    }

    Observation detect(byte[] rgb, int width, int height);
}
