package liveness.android;

import static org.junit.jupiter.api.Assertions.*;

import liveness.engine.LandmarkLayout;
import org.junit.jupiter.api.Test;

class FaceAlignerTest {

    /** Smooth RGB test image so interpolation error stays tiny. */
    private static byte[] image(int w, int h) {
        byte[] p = new byte[w * h * 3];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 3;
                p[o] = (byte) (x * 255 / (w - 1));
                p[o + 1] = (byte) (y * 255 / (h - 1));
                p[o + 2] = (byte) ((x + y) * 255 / (w + h - 2));
            }
        return p;
    }

    @Test void templatePointsOnA112ImageGiveIdentity() {
        byte[] img = image(112, 112);
        byte[] out = FaceAligner.alignCrop(img, 112, 112, FaceAligner.TEMPLATE.clone());
        int maxDiff = 0;
        for (int i = 0; i < out.length; i++) maxDiff = Math.max(maxDiff, Math.abs((out[i] & 255) - (img[i] & 255)));
        assertTrue(maxDiff <= 1, "max diff " + maxDiff);
    }

    @Test void scaledAndShiftedFaceIsMappedBack() {
        // 224x224 image whose content is the 112 image scaled x2 (nearest-free: build analytically)
        int W = 224;
        byte[] img = image(W, W);
        double[] pts = new double[10];
        for (int i = 0; i < 5; i++) {
            pts[2 * i] = FaceAligner.TEMPLATE[2 * i] * 2;
            pts[2 * i + 1] = FaceAligner.TEMPLATE[2 * i + 1] * 2;
        }
        double[] t = FaceAligner.fit(pts);
        assertEquals(0.5, t[0], 1e-9);   // scale
        assertEquals(1.0, t[1], 1e-9);   // cos
        assertEquals(0.0, t[2], 1e-9);   // sin
        byte[] out = FaceAligner.alignCrop(img, W, W, pts);
        assertEquals(112 * 112 * 3, out.length);
        // centre pixel of output = centre of source region
        int c = (56 * 112 + 56) * 3;
        int sx = 112, sy = 112;
        assertEquals(img[(sy * W + sx) * 3] & 255, out[c] & 255, 3);
    }

    @Test void rotationIsRecovered() {
        double ang = Math.toRadians(20);
        double c = Math.cos(ang), s = Math.sin(ang);
        double[] pts = new double[10];
        for (int i = 0; i < 5; i++) {
            double x = FaceAligner.TEMPLATE[2 * i] - 56, y = FaceAligner.TEMPLATE[2 * i + 1] - 56;
            pts[2 * i] = 150 + 1.5 * (c * x - s * y);
            pts[2 * i + 1] = 140 + 1.5 * (s * x + c * y);
        }
        double[] t = FaceAligner.fit(pts);
        // fit maps src->dst, so it undoes the 20 degree rotation and the 1.5x scale
        assertEquals(1 / 1.5, t[0], 1e-9);
        assertEquals(Math.cos(-ang), t[1], 1e-9);
        assertEquals(Math.sin(-ang), t[2], 1e-9);
    }

    @Test void outsideImageIsBlack() {
        byte[] img = new byte[40 * 40 * 3];
        java.util.Arrays.fill(img, (byte) 200);
        double[] far = new double[10];
        for (int i = 0; i < 5; i++) { far[2 * i] = 2000 + i; far[2 * i + 1] = 2000 + 3 * i; }
        byte[] out = FaceAligner.alignCrop(img, 40, 40, far);
        for (byte b : out) assertEquals(0, b & 255);
    }

    @Test void rejectsBadInput() {
        byte[] img = new byte[30];
        boolean threw = false;
        try { FaceAligner.alignCrop(img, 40, 40, FaceAligner.TEMPLATE); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue(threw);
        threw = false;
        try { FaceAligner.alignCrop(new byte[40 * 40 * 3], 40, 40, new double[10]); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue(threw); // all points identical -> degenerate
        threw = false;
        double[] nan = FaceAligner.TEMPLATE.clone(); nan[3] = Double.NaN;
        try { FaceAligner.alignCrop(new byte[40 * 40 * 3], 40, 40, nan); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue(threw);
    }

    @Test void align5SlotFeedsAligner() {
        double[] lm = new double[LandmarkLayout.FULL_LENGTH];
        System.arraycopy(FaceAligner.TEMPLATE, 0, lm, LandmarkLayout.ALIGN5, 10);
        assertNotNull(FaceAligner.alignCrop(image(112, 112), 112, 112, LandmarkLayout.align5(lm)));
    }
}
