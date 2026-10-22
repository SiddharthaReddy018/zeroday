package liveness.android;

import liveness.core.FaceFrame;
import liveness.engine.SameFaceChecker;

import java.util.Objects;

/**
 * Binds the host CameraX liveness stream (T3) to the SBI rCapture image (T1) (S21.2 / S18.6):
 * session nonce in the returned transactionId + maximum time gap + same-face consistency.
 * All three must pass. Fails closed: missing frames, missing embeddings or empty images reject.
 */
public final class CrossStreamBinder {

    public enum Result { OK, NONCE_MISMATCH, TIME_GAP_EXCEEDED, NO_HOST_FRAME, EMBEDDING_FAILED, DIFFERENT_FACE }

    public static final long DEFAULT_CLOCK_SKEW_TOLERANCE_MS = 2_000L;

    private final SameFaceChecker sameFace;
    private final FaceEmbedder embedder;
    private final long maxGapMs;
    private final long clockSkewToleranceMs;

    public CrossStreamBinder(SameFaceChecker sameFace, FaceEmbedder embedder, long maxGapMs) {
        this(sameFace, embedder, maxGapMs, DEFAULT_CLOCK_SKEW_TOLERANCE_MS);
    }

    public CrossStreamBinder(SameFaceChecker sameFace, FaceEmbedder embedder, long maxGapMs, long clockSkewToleranceMs) {
        this.sameFace = Objects.requireNonNull(sameFace, "sameFace");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        if (maxGapMs <= 0) throw new IllegalArgumentException("maxGapMs");
        this.maxGapMs = maxGapMs;
        this.clockSkewToleranceMs = Math.max(0, clockSkewToleranceMs);
        if (sameFace.isStubbed() || !(embedder instanceof SFaceEmbedder)) {
            liveness.policy.StubPolicy.check(true, "CrossStreamBinder with stub/test components");
        }
    }

    public long maxGapMs() { return maxGapMs; }
    public long clockSkewToleranceMs() { return clockSkewToleranceMs; }

    /**
     * Normalizes a timestamp to UTC epoch milliseconds.
     * Handles timestamps given in seconds, milliseconds, microseconds, or nanoseconds.
     */
    public static long normalizeToUtcEpochMs(long timestamp) {
        if (timestamp <= 0) return timestamp;
        if (timestamp < 100_000_000_000L) { // seconds (e.g. 1.7e9)
            return timestamp * 1000L;
        }
        if (timestamp > 10_000_000_000_000_000L) { // nanoseconds (e.g. 1.7e18)
            return timestamp / 1_000_000L;
        }
        if (timestamp > 10_000_000_000_000L) { // microseconds (e.g. 1.7e15)
            return timestamp / 1_000L;
        }
        return timestamp; // epoch ms
    }

    /**
     * Parses an ISO-8601 string or numeric timestamp to UTC epoch milliseconds.
     */
    public static long parseUtcTimestampMs(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return -1L;
        }
        String trimmed = timestampStr.trim();
        try {
            return normalizeToUtcEpochMs(Long.parseLong(trimmed));
        } catch (NumberFormatException nfe) {
            return java.time.Instant.parse(trimmed).toEpochMilli();
        }
    }

    /**
     * Overloaded verify accepting ISO-8601 string or string timestamp.
     */
    public Result verify(String sessionNonce, String returnedTxId, FaceFrame lastHostFrame,
                         String rcaptureTimestampIso, byte[] rcaptureImageRgb, int rcW, int rcH) {
        long tsMs = parseUtcTimestampMs(rcaptureTimestampIso);
        return verify(sessionNonce, returnedTxId, lastHostFrame, tsMs, rcaptureImageRgb, rcW, rcH);
    }

    /**
     * @param sessionNonce       nonce the session appended to the transactionId
     * @param returnedTxId       transactionId taken from the SBI response
     * @param lastHostFrame      last CameraX frame the engine judged (must carry RGB pixels)
     * @param rcaptureTimestampMs timestamp in ms (or normalized by binder)
     * @param rcaptureImageRgb   rCapture image decoded to RGB by the glue (decoder is platform-specific)
     */
    public Result verify(String sessionNonce, String returnedTxId, FaceFrame lastHostFrame,
                         long rcaptureTimestampMs, byte[] rcaptureImageRgb, int rcW, int rcH) {
        if (sameFace.isStubbed() || !(embedder instanceof SFaceEmbedder)) {
            liveness.policy.StubPolicy.check(true, "CrossStreamBinder.verify with stub/test components");
        }
        if (sessionNonce == null || returnedTxId == null
                || !returnedTxId.endsWith(":" + sessionNonce)) {
            return Result.NONCE_MISMATCH;
        }
        if (lastHostFrame == null || !lastHostFrame.hasPixelData()) return Result.NO_HOST_FRAME;

        long normRcapture = normalizeToUtcEpochMs(rcaptureTimestampMs);
        long normHost = normalizeToUtcEpochMs(lastHostFrame.timestampMs());
        long gap = Math.abs(normRcapture - normHost);
        if (gap > (maxGapMs + clockSkewToleranceMs)) {
            return Result.TIME_GAP_EXCEEDED;
        }
        if (rcaptureImageRgb == null || rcaptureImageRgb.length == 0) return Result.EMBEDDING_FAILED;
        float[] a, b;
        try {
            a = embedder.embed(lastHostFrame.pixelData(), lastHostFrame.widthPx(), lastHostFrame.heightPx());
            b = embedder.embed(rcaptureImageRgb, rcW, rcH);
        } catch (RuntimeException e) {
            return Result.EMBEDDING_FAILED;
        }
        if (a == null || b == null || a.length == 0 || a.length != b.length) return Result.EMBEDDING_FAILED;
        return sameFace.isSameFace(a, b) ? Result.OK : Result.DIFFERENT_FACE;
    }
}
