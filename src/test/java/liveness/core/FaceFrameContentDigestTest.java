package liveness.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import liveness.capture.FrameFingerprint;
import org.junit.jupiter.api.Test;

class FaceFrameContentDigestTest {
    private static final int W = 64, H = 48;

    private static byte[] rgb(int seed) {
        byte[] p = new byte[W * H * 3];
        for (int i = 0; i < p.length; i++) p[i] = (byte) ((i * 31 + seed) & 0xFF);
        return p;
    }

    private static FaceFrame frame(byte[] px, int w, int h, long pHash) {
        return new FaceFrame(FrameQuality.GOOD, "sig", 1, 1000L, 1, w, h, px,
                0, 0, 0, 0, 0.0, 0.0, 0.0, 1, 0.0, 0.0, null, pHash, null, null);
    }

    @Test void rgbFramesDifferingOnlyAtBottomGetDifferentDigests() {
        byte[] a = rgb(7);
        byte[] b = a.clone();
        // change the bottom rows only (old code read RGB as gray and only saw the top third)
        for (int y = H - 4; y < H; y++)
            for (int x = 0; x < W; x++) b[(y * W + x) * 3] ^= 0x55;
        // make sure a sampled point is in the changed region
        assertFalse(Arrays.equals(
                FrameFingerprint.computeContentDigestFromPixels(a, W, H),
                FrameFingerprint.computeContentDigestFromPixels(b, W, H)));
        assertFalse(Arrays.equals(frame(a, W, H, 0).contentDigest(), frame(b, W, H, 0).contentDigest()));
    }

    @Test void identicalRgbFramesGetEqualDigests() {
        assertArrayEquals(frame(rgb(3), W, H, 0).contentDigest(), frame(rgb(3), W, H, 0).contentDigest());
    }

    @Test void grayPathUnchanged() {
        byte[] gray = new byte[W * H];
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) (i * 13);
        assertArrayEquals(FrameFingerprint.computeContentDigest(gray, W, H),
                FrameFingerprint.computeContentDigestFromPixels(gray, W, H));
        assertArrayEquals(FrameFingerprint.computeContentDigest(gray, W, H),
                frame(gray, W, H, 0).contentDigest());
    }

    @Test void tooSmallFallsBackToPHashThenNull() {
        assertNotNull(frame(new byte[10], W, H, 0x1234L).contentDigest());
        assertNull(frame(new byte[10], W, H, 0L).contentDigest());
        assertNull(FrameFingerprint.computeContentDigestFromPixels(null, W, H));
    }
}
