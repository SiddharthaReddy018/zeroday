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

    private final SameFaceChecker sameFace;
    private final FaceEmbedder embedder;
    private final long maxGapMs;

    public CrossStreamBinder(SameFaceChecker sameFace, FaceEmbedder embedder, long maxGapMs) {
        this.sameFace = Objects.requireNonNull(sameFace, "sameFace");
        this.embedder = Objects.requireNonNull(embedder, "embedder");
        if (maxGapMs <= 0) throw new IllegalArgumentException("maxGapMs");
        this.maxGapMs = maxGapMs;
    }

    /**
     * @param sessionNonce       nonce the session appended to the transactionId
     * @param returnedTxId       transactionId taken from the SBI response
     * @param lastHostFrame      last CameraX frame the engine judged (must carry RGB pixels)
     * @param rcaptureImageRgb   rCapture image decoded to RGB by the glue (decoder is platform-specific)
     */
    public Result verify(String sessionNonce, String returnedTxId, FaceFrame lastHostFrame,
                         long rcaptureTimestampMs, byte[] rcaptureImageRgb, int rcW, int rcH) {
        if (sessionNonce == null || returnedTxId == null
                || !returnedTxId.endsWith(":" + sessionNonce)) {
            return Result.NONCE_MISMATCH;
        }
        if (lastHostFrame == null || !lastHostFrame.hasPixelData()) return Result.NO_HOST_FRAME;
        if (Math.abs(rcaptureTimestampMs - lastHostFrame.timestampMs()) > maxGapMs) {
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
