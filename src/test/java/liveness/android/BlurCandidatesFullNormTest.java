package liveness.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BlurCandidatesFullNormTest {
    private static final int W = 200;
    private static final int H = 200;

    private static byte[] gray(int[] p) {
        byte[] b = new byte[W * H * 3];
        for (int i = 0; i < p.length; i++) {
            byte v = (byte) p[i];
            b[3 * i] = v;
            b[3 * i + 1] = v;
            b[3 * i + 2] = v;
        }
        return b;
    }

    private static int[] noise(long seed) {
        Random r = new Random(seed);
        int[] p = new int[W * H];
        for (int i = 0; i < p.length; i++) p[i] = r.nextInt(256);
        return p;
    }

    @Test
    void matchesComputeWholeFrameNorm() {
        byte[] rgb = gray(noise(3L));
        double[] full = BlurCandidates.compute(rgb, W, H, 0, 0, W, H);
        assertEquals(full[BlurCandidates.LAP_VAR_NORM], BlurCandidates.fullFrameNorm(rgb, W, H), 1e-9);
    }

    @Test
    void flatFrameIsZeroAndBadInputIsMinusOne() {
        byte[] flat = new byte[W * H * 3];
        assertEquals(0.0, BlurCandidates.fullFrameNorm(flat, W, H), 1e-12);
        assertEquals(-1.0, BlurCandidates.fullFrameNorm(null, W, H), 0.0);
        assertEquals(-1.0, BlurCandidates.fullFrameNorm(new byte[10], W, H), 0.0);
        assertTrue(BlurCandidates.fullFrameNorm(gray(noise(5L)), W, H) > 0.0);
    }
}
