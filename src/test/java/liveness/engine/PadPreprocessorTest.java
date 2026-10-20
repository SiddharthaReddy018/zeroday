package liveness.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PadPreprocessorTest {

    private static byte[] solid(int w, int h, int r, int g, int b) {
        byte[] a = new byte[w * h * 3];
        for (int i = 0; i < w * h; i++) { a[3 * i] = (byte) r; a[3 * i + 1] = (byte) g; a[3 * i + 2] = (byte) b; }
        return a;
    }

    @Test
    void outputShapeChannelOrderAndRange() {
        float[] out = PadPreprocessor.prepare(solid(200, 160, 255, 128, 0), 200, 160, 50, 40, 80, 80);
        assertEquals(3 * 128 * 128, out.length);
        assertEquals(1.0f, out[0], 1e-4);                       // R plane first
        assertEquals(128 / 255f, out[128 * 128], 1e-3);          // G plane second
        assertEquals(0.0f, out[2 * 128 * 128], 1e-4);            // B plane third
    }

    @Test
    void upscalePathAlsoProducesValidRange() {
        float[] out = PadPreprocessor.prepare(solid(40, 40, 10, 20, 30), 40, 40, 10, 10, 20, 20);
        for (float v : out) assertTrue(v >= 0f && v <= 1f);
        assertEquals(10 / 255f, out[0], 1e-3);
    }

    @Test
    void cropLeavingTheImageUsesReflectionNotZeros() {
        // face box in the corner -> 1.5x crop extends outside; reflected border of a solid image stays solid
        float[] out = PadPreprocessor.prepare(solid(100, 100, 200, 200, 200), 100, 100, 0, 0, 60, 60);
        for (int i = 0; i < 128 * 128; i++) assertEquals(200 / 255f, out[i], 1e-3);
    }

    @Test
    void reflect101() {
        assertEquals(1, PadPreprocessor.reflect(-1, 5));
        assertEquals(3, PadPreprocessor.reflect(5, 5));
        assertEquals(0, PadPreprocessor.reflect(0, 5));
        assertEquals(4, PadPreprocessor.reflect(4, 5));
    }

    @Test
    void invalidBuffersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PadPreprocessor.prepare(null, 10, 10, 0, 0, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> PadPreprocessor.prepare(new byte[10], 10, 10, 0, 0, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> PadPreprocessor.prepare(new byte[300], 0, 10, 0, 0, 5, 5));
    }
}
