package liveness.core;

/**
 * Frame quality dimensions (M2 — replaces M1 stub enum).
 * <p>
 * Uses raw physical units per R3: degrees, pixels, Laplacian variance,
 * clipped-pixel fractions. Never mapped 0-100 scores.
 * <p>
 * The M1 enum values GOOD and SUSPECT are preserved as constants
 * for backward compatibility with existing tests.
 */
public final class FrameQuality {

    // --- M1 backward-compatible constants ---
    public static final FrameQuality GOOD = new FrameQuality(true, 1.0, "GOOD");
    public static final FrameQuality SUSPECT = new FrameQuality(false, 0.3, "SUSPECT");

    private final boolean acceptable;
    private final double qualityWeight;
    private final String label;

    // --- Raw quality dimensions (R3) ---
    private final double laplacianVariance;    // blur: higher = sharper
    private final double brightnessMean;       // 0-255
    private final double clippedShadowFraction; // fraction of very dark pixels
    private final double clippedHighlightFraction; // fraction of saturated pixels
    private final int faceSizePx;             // face bbox size in pixels
    private final double yawDeg;              // absolute yaw from frontal
    private final double pitchDeg;            // absolute pitch from frontal
    private final double rollDeg;             // absolute roll from frontal
    private final int faceCount;              // number of detected faces

    /**
     * Simple constructor for M1 compatibility.
     */
    private FrameQuality(boolean acceptable, double qualityWeight, String label) {
        this.acceptable = acceptable;
        this.qualityWeight = qualityWeight;
        this.label = label;
        this.laplacianVariance = acceptable ? 200.0 : 50.0;
        this.brightnessMean = acceptable ? 128.0 : 30.0;
        this.clippedShadowFraction = 0.0;
        this.clippedHighlightFraction = 0.0;
        this.faceSizePx = acceptable ? 200 : 30;
        this.yawDeg = 0.0;
        this.pitchDeg = 0.0;
        this.rollDeg = 0.0;
        this.faceCount = 1;
    }

    /**
     * Full constructor with raw physical dimensions.
     */
    public FrameQuality(boolean acceptable, double qualityWeight, String label,
                        double laplacianVariance, double brightnessMean,
                        double clippedShadowFraction, double clippedHighlightFraction,
                        int faceSizePx, double yawDeg, double pitchDeg, double rollDeg,
                        int faceCount) {
        this.acceptable = acceptable;
        this.qualityWeight = qualityWeight;
        this.label = label;
        this.laplacianVariance = laplacianVariance;
        this.brightnessMean = brightnessMean;
        this.clippedShadowFraction = clippedShadowFraction;
        this.clippedHighlightFraction = clippedHighlightFraction;
        this.faceSizePx = faceSizePx;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.rollDeg = rollDeg;
        this.faceCount = faceCount;
    }

    /** Whether the frame passes the quality gate. */
    public boolean isAcceptable() { return acceptable; }

    /**
     * Quality weight for fusion (0.0-1.0).
     * Higher = sharper/better lit = contributes more to composite score.
     */
    public double qualityWeight() { return qualityWeight; }

    /** Human-readable label for audit logging. */
    public String label() { return label; }

    // --- Raw dimension getters (R3: always raw units) ---
    public double laplacianVariance()        { return laplacianVariance; }
    public double brightnessMean()           { return brightnessMean; }
    public double clippedShadowFraction()    { return clippedShadowFraction; }
    public double clippedHighlightFraction() { return clippedHighlightFraction; }
    public int faceSizePx()                  { return faceSizePx; }
    public double yawDeg()                   { return yawDeg; }
    public double pitchDeg()                 { return pitchDeg; }
    public double rollDeg()                  { return rollDeg; }
    public int faceCount()                   { return faceCount; }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameQuality)) return false;
        FrameQuality that = (FrameQuality) o;
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }
}
