package liveness.engine;

/**
 * Own lightweight quality gate (§6.2, R3).
 * <p>
 * Runs on the RAW frame BEFORE crop/alignment.
 * Uses raw physical units: degrees, pixels, Laplacian variance.
 * NEVER uses mapped 0-100 scores or a default cut-off of 50.
 * <p>
 * Stream gate and final RCAPTURE image gate are calibrated
 * separately (R3, U26).
 */
public final class QualityGate {

    private final double minBlurVariance;
    private final double minBrightness;
    private final double maxBrightness;
    private final int minFaceSizePx;
    private final double maxYawDeg;
    private final double maxPitchDeg;

    /**
     * @param minBlurVariance  seed 100.0 — Laplacian variance minimum
     * @param minBrightness    seed 40.0 — mean pixel brightness minimum
     * @param maxBrightness    seed 250.0 — mean pixel brightness maximum
     * @param minFaceSizePx    from config, seed 64 [REPO]
     * @param maxYawDeg        from config, seed 30 [REPO] — raw degrees
     * @param maxPitchDeg      from config, seed 30 [REPO] — raw degrees
     */
    public QualityGate(double minBlurVariance, double minBrightness,
                       double maxBrightness, int minFaceSizePx,
                       double maxYawDeg, double maxPitchDeg) {
        this.minBlurVariance = minBlurVariance;
        this.minBrightness = minBrightness;
        this.maxBrightness = maxBrightness;
        this.minFaceSizePx = minFaceSizePx;
        this.maxYawDeg = maxYawDeg;
        this.maxPitchDeg = maxPitchDeg;
    }

    /**
     * Evaluates a raw frame's quality dimensions.
     *
     * @param frameData the frame's raw measurements
     * @return quality result with pass/fail, reason, and fusion weight
     */
    public QualityResult evaluate(RawFrameData frameData) {
        // Single face check
        if (frameData.faceCount() != 1) {
            String reason = frameData.faceCount() == 0
                    ? "guide.face.none" : "guide.face.multiple";
            return QualityResult.fail(reason);
        }

        // Blur check (Laplacian variance — raw units)
        if (frameData.laplacianVariance() < minBlurVariance) {
            return QualityResult.fail("guide.quality.blur");
        }

        // Brightness check (raw mean pixel value)
        if (frameData.brightnessMean() < minBrightness) {
            return QualityResult.fail("guide.quality.too_dark");
        }
        if (frameData.brightnessMean() > maxBrightness) {
            return QualityResult.fail("guide.quality.too_bright");
        }

        // Face size check (raw pixels)
        if (frameData.faceBboxWidthPx() < minFaceSizePx
                || frameData.faceBboxHeightPx() < minFaceSizePx) {
            return QualityResult.fail("guide.face.too_far");
        }

        // Pose unavailable (detector sentinel Double.MAX_VALUE, or non-finite): not a pose violation.
        if (poseUnavailable(frameData.yawDeg()) || poseUnavailable(frameData.pitchDeg())) {
            return QualityResult.fail("guide.pose.unavailable");
        }

        // Pose check (raw degrees — R3)
        if (Math.abs(frameData.yawDeg()) > maxYawDeg) {
            return QualityResult.fail("guide.pose.yaw_exceeded");
        }
        if (Math.abs(frameData.pitchDeg()) > maxPitchDeg) {
            return QualityResult.fail("guide.pose.pitch_exceeded");
        }

        // Compute quality weight for fusion (§5.4)
        // Better quality → higher weight (0.0 to 1.0)
        double sharpnessWeight = Math.min(1.0,
                frameData.laplacianVariance() / (minBlurVariance * 3));
        double poseWeight = 1.0 - (Math.abs(frameData.yawDeg())
                + Math.abs(frameData.pitchDeg())) / (maxYawDeg + maxPitchDeg);
        poseWeight = Math.max(0.1, poseWeight);

        double qualityWeight = (sharpnessWeight + poseWeight) / 2.0;
        return QualityResult.pass(qualityWeight);
    }

    private static boolean poseUnavailable(double deg) {
        return Double.isNaN(deg) || Math.abs(deg) >= Double.MAX_VALUE;
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Raw frame measurements in physical units (R3).
     * All values come from the raw frame before any crop/alignment.
     */
    public static final class RawFrameData {
        private final double laplacianVariance;
        private final double brightnessMean;
        private final int faceBboxWidthPx;
        private final int faceBboxHeightPx;
        private final double yawDeg;
        private final double pitchDeg;
        private final double rollDeg;
        private final int faceCount;
        private final int frameWidthPx;
        private final int frameHeightPx;

        public RawFrameData(double laplacianVariance, double brightnessMean,
                            int faceBboxWidthPx, int faceBboxHeightPx,
                            double yawDeg, double pitchDeg, double rollDeg,
                            int faceCount, int frameWidthPx, int frameHeightPx) {
            this.laplacianVariance = laplacianVariance;
            this.brightnessMean = brightnessMean;
            this.faceBboxWidthPx = faceBboxWidthPx;
            this.faceBboxHeightPx = faceBboxHeightPx;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.faceCount = faceCount;
            this.frameWidthPx = frameWidthPx;
            this.frameHeightPx = frameHeightPx;
        }

        public double laplacianVariance()  { return laplacianVariance; }
        public double brightnessMean()     { return brightnessMean; }
        public int faceBboxWidthPx()       { return faceBboxWidthPx; }
        public int faceBboxHeightPx()      { return faceBboxHeightPx; }
        public double yawDeg()             { return yawDeg; }
        public double pitchDeg()           { return pitchDeg; }
        public double rollDeg()            { return rollDeg; }
        public int faceCount()             { return faceCount; }
        public int frameWidthPx()          { return frameWidthPx; }
        public int frameHeightPx()         { return frameHeightPx; }
    }

    /**
     * Result of a quality evaluation.
     */
    public static final class QualityResult {
        private final boolean passed;
        private final String failureReason;
        private final double qualityWeight;

        private QualityResult(boolean passed, String failureReason,
                              double qualityWeight) {
            this.passed = passed;
            this.failureReason = failureReason;
            this.qualityWeight = qualityWeight;
        }

        static QualityResult pass(double qualityWeight) {
            return new QualityResult(true, null, qualityWeight);
        }

        static QualityResult fail(String reason) {
            return new QualityResult(false, reason, 0.0);
        }

        public boolean passed()          { return passed; }
        public String failureReason()    { return failureReason; }
        public double qualityWeight()    { return qualityWeight; }
    }
}
