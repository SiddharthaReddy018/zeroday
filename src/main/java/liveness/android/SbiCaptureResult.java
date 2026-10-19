package liveness.android;

/** Parsed SBI rCapture response (tier T1). */
public final class SbiCaptureResult {
    private final String transactionId;
    private final long timestampMs;
    private final byte[] image;
    private final String signedData; // the JWT as returned; signature NOT verified here
    private final String deviceCode;

    public SbiCaptureResult(String transactionId, long timestampMs, byte[] image,
                            String signedData, String deviceCode) {
        this.transactionId = transactionId;
        this.timestampMs = timestampMs;
        this.image = image;
        this.signedData = signedData;
        this.deviceCode = deviceCode;
    }

    public String transactionId() { return transactionId; }
    public long timestampMs() { return timestampMs; }
    public byte[] image() { return image; }
    public String signedData() { return signedData; }
    public String deviceCode() { return deviceCode; }
}
