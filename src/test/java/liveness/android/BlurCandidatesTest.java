package liveness.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BlurCandidatesTest {
    private static final int W = 200;
    private static final int H = 200;

    private static int[] noise(long seed) {
        Random rnd = new Random(seed);
        int[] p = new int[W * H];
        for (int i = 0; i < p.length; i++) p[i] = rnd.nextInt(256);
        return p;
    }

    private static int[] boxBlur3(int[] p, int passes) {
        int[] cur = p;
        for (int k = 0; k < passes; k++) {
            int[] nxt = new int[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int s = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int yy = Math.min(H - 1, Math.max(0, y + dy));
                            int xx = Math.min(W - 1, Math.max(0, x + dx));
                            s += cur[yy * W + xx];
                        }
                    }
                    nxt[y * W + x] = s / 9;
                }
            }
            cur = nxt;
        }
        return cur;
    }

    private static byte[] toRgb(int[] p, double scale) {
        byte[] b = new byte[W * H * 3];
        for (int i = 0; i < p.length; i++) {
            byte v = (byte) (int) Math.round(p[i] * scale);
            b[3 * i] = v;
            b[3 * i + 1] = v;
            b[3 * i + 2] = v;
        }
        return b;
    }

    @Test
    void sharpScoresHigherThanBlurred() {
        int[] sharp = noise(7L);
        int[] blurred = boxBlur3(sharp, 3);
        double[] a = BlurCandidates.compute(toRgb(sharp, 1.0), W, H, 50, 50, 100, 100);
        double[] b = BlurCandidates.compute(toRgb(blurred, 1.0), W, H, 50, 50, 100, 100);
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(a[BlurCandidates.LAP_VAR] > b[BlurCandidates.LAP_VAR], "lapVar sharp > blurred");
        assertTrue(a[BlurCandidates.SHARP_RATIO] > b[BlurCandidates.SHARP_RATIO], "sharpRatio sharp > blurred");
    }

    @Test
    void normalisedVarianceIsBrightnessInvariantRawIsNot() {
        int[] sharp = noise(11L);
        double[] full = BlurCandidates.compute(toRgb(sharp, 1.0), W, H, 50, 50, 100, 100);
        double[] half = BlurCandidates.compute(toRgb(sharp, 0.5), W, H, 50, 50, 100, 100);
        assertNotNull(full);
        assertNotNull(half);
        assertTrue(half[BlurCandidates.LAP_VAR] < 0.5 * full[BlurCandidates.LAP_VAR], "raw variance drops with exposure");
        double rel = Math.abs(half[BlurCandidates.LAP_VAR_NORM] - full[BlurCandidates.LAP_VAR_NORM])
                / full[BlurCandidates.LAP_VAR_NORM];
        assertTrue(rel < 0.10, "normalised variance within 10%, was " + rel);
    }

    @Test
    void bboxOutsideFrameIsClamped() {
        double[] r = BlurCandidates.compute(toRgb(noise(3L), 1.0), W, H, -20, -20, 100, 100);
        assertNotNull(r);
        assertFalse(Double.isNaN(r[BlurCandidates.LAP_VAR]));
    }

    @Test
    void degenerateInputsReturnNull() {
        byte[] ok = toRgb(noise(5L), 1.0);
        assertNull(BlurCandidates.compute(null, W, H, 50, 50, 100, 100));
        assertNull(BlurCandidates.compute(ok, W, H, 50, 50, 0, 100));
        assertNull(BlurCandidates.compute(ok, W, H, 50, 50, 4, 4));
        assertNull(BlurCandidates.compute(ok, W, H, 300, 300, 50, 50));
        assertNull(BlurCandidates.compute(new byte[10], W, H, 50, 50, 100, 100));
    }

    @Test
    void flatFrameGivesZeroAndNoNaN() {
        int[] flat = new int[W * H];
        java.util.Arrays.fill(flat, 100);
        double[] r = BlurCandidates.compute(toRgb(flat, 1.0), W, H, 50, 50, 100, 100);
        assertNotNull(r);
        assertEquals(0.0, r[BlurCandidates.LAP_VAR], 1e-9);
        assertEquals(0.0, r[BlurCandidates.SHARP_RATIO], 1e-9);
        assertEquals(100.0, r[BlurCandidates.MEAN_LUMA], 1e-6);
        for (double v : r) assertFalse(Double.isNaN(v));
    }
}
