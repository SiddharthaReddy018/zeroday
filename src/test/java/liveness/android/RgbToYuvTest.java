package liveness.android;

import liveness.capture.FrameFingerprint;
import liveness.crypto.EcdsaSignerStub;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class RgbToYuvTest {

    private static final int W = 64, H = 48;

    /** Smooth gradient kept inside 20..220 so no channel clamps. */
    private static byte[] gradient() {
        byte[] rgb = new byte[W * H * 3];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int o = (y * W + x) * 3;
                rgb[o] = (byte) (20 + x * 200 / W);
                rgb[o + 1] = (byte) (20 + y * 200 / H);
                rgb[o + 2] = (byte) (20 + (x + y) * 200 / (W + H));
            }
        }
        return rgb;
    }

    @Test
    void roundTripStaysCloseOnSmoothImage() {
        byte[] rgb = gradient();
        byte[] back = YuvConverter.toRgb(RgbToYuv.toFrame(rgb, W, H, 0, 0L));
        int max = 0;
        for (int i = 0; i < rgb.length; i++) max = Math.max(max, Math.abs((rgb[i] & 0xFF) - (back[i] & 0xFF)));
        assertTrue(max <= 4, "max per-channel error " + max);
    }

    @Test
    void rejectsOddDimensionsAndShortBuffers() {
        assertThrows(IllegalArgumentException.class, () -> RgbToYuv.toFrame(new byte[63 * 48 * 3], 63, 48, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> RgbToYuv.toFrame(new byte[10], W, H, 0, 0L));
    }

    @Test
    void digestMarkMakesEveryNeighbourDifferIncludingWrap() {
        YuvFrame base = RgbToYuv.toFrame(gradient(), W, H, 0, 0L);
        int n = PipelineBenchmark.FRAME_POOL;
        byte[][] d = new byte[n][];
        for (int i = 0; i < n; i++) {
            YuvFrame f = RgbToYuv.withDigestMark(base, i, i);
            d[i] = FrameFingerprint.computeContentDigestFromPixels(YuvConverter.toRgb(f), W, H);
            assertNotNull(d[i]);
        }
        for (int i = 0; i < n; i++) {
            assertFalse(java.util.Arrays.equals(d[i], d[(i + 1) % n]), "digest equal at " + i);
        }
    }

    @Test
    void digestMarkDoesNotTouchBaseAndRejectsRotation() {
        YuvFrame base = RgbToYuv.toFrame(gradient(), W, H, 0, 0L);
        byte[] before = base.y.clone();
        RgbToYuv.withDigestMark(base, 5, 1L);
        assertArrayEquals(before, base.y);
        YuvFrame rot = RgbToYuv.toFrame(gradient(), W, H, 270, 0L);
        assertThrows(IllegalArgumentException.class, () -> RgbToYuv.withDigestMark(rot, 0, 0L));
    }

    @Test
    void runWithRequiresDetectorAndPoolTogether() {
        FaceEmbedder emb = new FaceEmbedder() {
            @Override public float[] embed(byte[] rgb, int w, int h) { throw new UnsupportedOperationException(); }
        };
        FaceDetector det = (rgb, w, h) -> FaceDetector.Observation.NONE;
        assertThrows(IllegalArgumentException.class, () -> PipelineBenchmark.runWith(Paths.get("x.onnx"),
                new EcdsaSignerStub(), emb, PipelineBenchmark.Mode.LOCKSTEP, det, "d", null, null,
                640, 480, 0, 30, 0, 50));
    }
}
