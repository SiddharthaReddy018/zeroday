package liveness.android;

import liveness.core.FaceFrame;
import liveness.engine.SameFaceChecker;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class CrossStreamBinderTest {

    private static final byte[] DUMMY_RGB = new byte[112 * 112 * 3];

    @Test
    void timestampNormalizationHandlesVariousUnits() {
        long epochMs = 1770000000000L;
        long epochSec = 1770000000L;
        long epochMicros = 1770000000000000L;
        long epochNanos = 1770000000000000000L;

        assertEquals(epochMs, CrossStreamBinder.normalizeToUtcEpochMs(epochMs));
        assertEquals(epochMs, CrossStreamBinder.normalizeToUtcEpochMs(epochSec));
        assertEquals(epochMs, CrossStreamBinder.normalizeToUtcEpochMs(epochMicros));
        assertEquals(epochMs, CrossStreamBinder.normalizeToUtcEpochMs(epochNanos));
    }

    @Test
    void parseUtcTimestampMsHandlesIsoAndEpoch() {
        String isoUtc = "2026-10-22T02:30:00Z";
        long expectedMs = java.time.OffsetDateTime.parse(isoUtc).toInstant().toEpochMilli();

        assertEquals(expectedMs, CrossStreamBinder.parseUtcTimestampMs(isoUtc));

        String isoOffset = "2026-10-22T08:00:00+05:30";
        long expectedOffsetMs = java.time.OffsetDateTime.parse(isoOffset).toInstant().toEpochMilli();
        assertEquals(expectedOffsetMs, CrossStreamBinder.parseUtcTimestampMs(isoOffset));

        assertEquals(expectedMs, CrossStreamBinder.parseUtcTimestampMs(String.valueOf(expectedMs)));
    }

    private static FaceFrame hostFrame(long ts) {
        return new FaceFrame(liveness.core.FrameQuality.GOOD, "camerax:c", 1, ts, 1, 112, 112, DUMMY_RGB,
                0, 0, 112, 112, 0, 0, 0, 1, 200.0, 128.0, null, 7L);
    }

    @Test
    void boundedClockSkewWithinTolerancePasses() {
        FaceEmbedder embedder = (pix, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker sameFace = new SameFaceChecker(0.5);
        long maxGapMs = 3000L;
        long clockSkewToleranceMs = 2000L;
        CrossStreamBinder binder = new CrossStreamBinder(sameFace, embedder, maxGapMs, clockSkewToleranceMs);

        long hostTime = 1770000000000L;
        FaceFrame frame = hostFrame(hostTime);

        // Gap of 4000ms: > maxGapMs (3000), but within maxGapMs + clockSkewToleranceMs (5000)
        long rcaptureTime = hostTime + 4000L;
        CrossStreamBinder.Result result = binder.verify("nonce1", "tx:nonce1", frame,
                rcaptureTime, DUMMY_RGB, 112, 112);

        assertEquals(CrossStreamBinder.Result.OK, result);
    }

    @Test
    void excessiveGapExceedsTimeGapLimit() {
        FaceEmbedder embedder = (pix, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker sameFace = new SameFaceChecker(0.5);
        long maxGapMs = 3000L;
        long clockSkewToleranceMs = 2000L;
        CrossStreamBinder binder = new CrossStreamBinder(sameFace, embedder, maxGapMs, clockSkewToleranceMs);

        long hostTime = 1770000000000L;
        FaceFrame frame = hostFrame(hostTime);

        // Gap of 6000ms exceeds maxGapMs + clockSkewToleranceMs (5000)
        long rcaptureTime = hostTime + 6000L;
        CrossStreamBinder.Result result = binder.verify("nonce1", "tx:nonce1", frame,
                rcaptureTime, DUMMY_RGB, 112, 112);

        assertEquals(CrossStreamBinder.Result.TIME_GAP_EXCEEDED, result);
    }

    @Test
    void overloadedVerifyAcceptsIsoStringTimestamp() {
        FaceEmbedder embedder = (pix, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker sameFace = new SameFaceChecker(0.5);
        CrossStreamBinder binder = new CrossStreamBinder(sameFace, embedder, 5000L, 2000L);

        String isoTime = "2026-10-22T02:30:00Z";
        long hostTime = Instant.parse(isoTime).toEpochMilli() - 1000L;
        FaceFrame frame = hostFrame(hostTime);

        CrossStreamBinder.Result result = binder.verify("nonce1", "tx:nonce1", frame,
                isoTime, DUMMY_RGB, 112, 112);

        assertEquals(CrossStreamBinder.Result.OK, result);
    }

    @Test
    void nonceMismatchReturnsNonceMismatch() {
        FaceEmbedder embedder = (pix, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker sameFace = new SameFaceChecker(0.5);
        CrossStreamBinder binder = new CrossStreamBinder(sameFace, embedder, 5000L);

        FaceFrame frame = hostFrame(1000L);
        CrossStreamBinder.Result result = binder.verify("nonce1", "tx:wrongNonce", frame,
                1000L, DUMMY_RGB, 112, 112);

        assertEquals(CrossStreamBinder.Result.NONCE_MISMATCH, result);
    }

    @Test
    void missingHostFrameReturnsNoHostFrame() {
        FaceEmbedder embedder = (pix, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker sameFace = new SameFaceChecker(0.5);
        CrossStreamBinder binder = new CrossStreamBinder(sameFace, embedder, 5000L);

        CrossStreamBinder.Result result = binder.verify("nonce1", "tx:nonce1", null,
                1000L, DUMMY_RGB, 112, 112);

        assertEquals(CrossStreamBinder.Result.NO_HOST_FRAME, result);
    }
}
