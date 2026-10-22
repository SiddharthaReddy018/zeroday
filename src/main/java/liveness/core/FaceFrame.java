package liveness.core;

import java.util.Objects;

/**
 * Immutable value holding one captured face frame's metadata and pixel data.
 * <p>
 * M2 expansion: adds raw pixel data reference, timestamps, landmarks,
 * face bounding box, and sequence number for capture integrity checks.
 * <p>
 * Quality checks run on the raw frame before crop/alignment (§5.5, R3).
 */
public final class FaceFrame {

    private final FrameQuality quality;
    private final String signature;
    private final int deviceId;

    // --- M2 additions ---
    private final long timestampMs;
    private final int sequenceNum;
    private final int widthPx;
    private final int heightPx;
    private final byte[] pixelData;       // raw frame bytes (ownership: caller must not modify)
    private final int faceBboxX;
    private final int faceBboxY;
    private final int faceBboxWidth;
    private final int faceBboxHeight;
    private final double yawDeg;
    private final double pitchDeg;
    private final double rollDeg;
    private final int faceCount;
    private final double laplacianVariance;
    private final double brightnessMean;
    private final double[] landmarks;     // flattened landmark coords (nullable until detector runs)
    private final long pHash;             // 64-bit perceptual hash for freeze/loop detection (0 = not computed)
    private final byte[] encodedBytes;    // original encoded bytes (JPEG/JP2/ISO) for final capture imageSha256

    /**
     * M1-compatible constructor (for existing tests).
     */
    public FaceFrame(FrameQuality quality, String signature, int deviceId) {
        this(quality, signature, deviceId, System.currentTimeMillis(), 0,
                640, 480, null, 0, 0, 0, 0,
                0.0, 0.0, 0.0, 1, 0.0, 0.0, null);
    }

    /**
     * Full M2 constructor.
     */
    public FaceFrame(FrameQuality quality, String signature, int deviceId,
                     long timestampMs, int sequenceNum,
                     int widthPx, int heightPx, byte[] pixelData,
                     int faceBboxX, int faceBboxY,
                     int faceBboxWidth, int faceBboxHeight,
                     double yawDeg, double pitchDeg, double rollDeg,
                     int faceCount, double laplacianVariance,
                     double brightnessMean, double[] landmarks) {
        this(quality, signature, deviceId, timestampMs, sequenceNum, widthPx, heightPx, pixelData,
             faceBboxX, faceBboxY, faceBboxWidth, faceBboxHeight, yawDeg, pitchDeg, rollDeg,
             faceCount, laplacianVariance, brightnessMean, landmarks, 0L, null);
    }

    /**
     * Full constructor including the perceptual hash (computed by the adapter from raw pixels).
     */
    public FaceFrame(FrameQuality quality, String signature, int deviceId,
                     long timestampMs, int sequenceNum,
                     int widthPx, int heightPx, byte[] pixelData,
                     int faceBboxX, int faceBboxY,
                     int faceBboxWidth, int faceBboxHeight,
                     double yawDeg, double pitchDeg, double rollDeg,
                     int faceCount, double laplacianVariance,
                     double brightnessMean, double[] landmarks, long pHash) {
        this(quality, signature, deviceId, timestampMs, sequenceNum, widthPx, heightPx, pixelData,
             faceBboxX, faceBboxY, faceBboxWidth, faceBboxHeight, yawDeg, pitchDeg, rollDeg,
             faceCount, laplacianVariance, brightnessMean, landmarks, pHash, null);
    }

    /**
     * Full constructor including perceptual hash and encoded image bytes.
     */
    public FaceFrame(FrameQuality quality, String signature, int deviceId,
                     long timestampMs, int sequenceNum,
                     int widthPx, int heightPx, byte[] pixelData,
                     int faceBboxX, int faceBboxY,
                     int faceBboxWidth, int faceBboxHeight,
                     double yawDeg, double pitchDeg, double rollDeg,
                     int faceCount, double laplacianVariance,
                     double brightnessMean, double[] landmarks, long pHash,
                     byte[] encodedBytes) {
        this.quality = Objects.requireNonNull(quality, "quality");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.deviceId = deviceId;
        this.timestampMs = timestampMs;
        this.sequenceNum = sequenceNum;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.pixelData = pixelData;
        this.faceBboxX = faceBboxX;
        this.faceBboxY = faceBboxY;
        this.faceBboxWidth = faceBboxWidth;
        this.faceBboxHeight = faceBboxHeight;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.rollDeg = rollDeg;
        this.faceCount = faceCount;
        this.laplacianVariance = laplacianVariance;
        this.brightnessMean = brightnessMean;
        this.landmarks = landmarks;
        this.pHash = pHash;
        this.encodedBytes = encodedBytes;
    }

    // --- M1 getters ---
    public FrameQuality quality() { return quality; }
    public String signature()     { return signature; }
    public int deviceId()         { return deviceId; }

    // --- M2 getters ---
    public long timestampMs()         { return timestampMs; }
    public int sequenceNum()          { return sequenceNum; }
    public int widthPx()              { return widthPx; }
    public int heightPx()             { return heightPx; }
    public byte[] pixelData()         { return pixelData; }
    public byte[] encodedBytes()       { return encodedBytes; }
    public int faceBboxX()            { return faceBboxX; }
    public int faceBboxY()            { return faceBboxY; }
    public int faceBboxWidth()        { return faceBboxWidth; }
    public int faceBboxHeight()       { return faceBboxHeight; }
    public double yawDeg()            { return yawDeg; }
    public double pitchDeg()          { return pitchDeg; }
    public double rollDeg()           { return rollDeg; }
    public int faceCount()            { return faceCount; }
    public double laplacianVariance() { return laplacianVariance; }
    public double brightnessMean()    { return brightnessMean; }
    public double[] landmarks()       { return landmarks; }
    public long pHash()               { return pHash; }

    /** True if this frame has pixel data attached. */
    public boolean hasPixelData() {
        return pixelData != null && pixelData.length > 0;
    }

    /** True if this frame has encoded bytes attached. */
    public boolean hasEncodedBytes() {
        return encodedBytes != null && encodedBytes.length > 0;
    }

    /** True if landmarks have been computed. */
    public boolean hasLandmarks() {
        return landmarks != null && landmarks.length > 0;
    }

    @Override
    public String toString() {
        return "FaceFrame{" + quality + ", sig=" + signature + ", dev=" + deviceId
                + ", ts=" + timestampMs + ", seq=" + sequenceNum
                + ", " + widthPx + "x" + heightPx
                + ", faces=" + faceCount + "}";
    }
}
