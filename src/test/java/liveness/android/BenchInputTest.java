package liveness.android;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchInputTest {

    /**
     * Golden values come from an INDEPENDENT implementation of xorshift64* (Python, not this class), so this pins
     * that desktop JVM and Android ART cannot drift apart and that PAD logits are comparable across them.
     */
    @Test
    void padInputMatchesIndependentReference() {
        float[] in = BenchInput.padInput(7L);
        assertEquals(3 * 128 * 128, in.length);
        assertEquals(0x1.a3f75ep-1f, in[0], 0f);
        assertEquals(0x1.db48d8p-1f, in[1], 0f);
        assertEquals(0x1.6df9dp-4f, in[2], 0f);
        assertEquals(0x1.b8d78p-4f, in[3], 0f);
    }

    @Test
    void padInputIsDeterministicAndInUnitRange() {
        float[] a = BenchInput.padInput(7L);
        float[] b = BenchInput.padInput(7L);
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, BenchInput.padInput(8L)));
        for (float f : a) assertTrue(f >= 0f && f < 1f, "out of [0,1): " + f);
    }

    @Test
    void seedZeroDoesNotLockTheGeneratorAtZero() {
        BenchInput.Rng r = new BenchInput.Rng(0);
        assertEquals(0x0d83b3e29a21487aL, r.nextLong());
        assertNotEquals(0L, r.nextLong());
    }

    @Test
    void syntheticYuvHasCameraLikeLayoutAndRealisticLuma() {
        YuvFrame f = BenchInput.syntheticYuv(640, 480, 270, 1234L, 1L);
        assertEquals(640 * 480, f.y.length);
        assertEquals(640, f.yRowStride);
        assertEquals(2, f.uvPixelStride);
        assertEquals(640, f.uvRowStride);
        assertEquals(640 * 240, f.u.length);
        assertEquals(270, f.rotationDegrees);
        assertEquals(1234L, f.timestampMs);
        // luma is the xorshift stream: first 8 bytes pinned against the independent reference
        int[] expected = {93, 157, 172, 73, 139, 142, 164, 135};
        for (int i = 0; i < 8; i++) assertEquals(expected[i], f.y[i] & 0xFF);
        long sum = 0;
        for (byte b : f.y) {
            int v = b & 0xFF;
            assertTrue(v >= 64 && v <= 191, "luma out of [64,191]: " + v);
            sum += v;
        }
        double mean = sum / (double) f.y.length;
        assertTrue(mean > 120 && mean < 136, "mean luma " + mean);
    }

    @Test
    void syntheticYuvConvertsWithTheRealConverterAndDiffersPerSeed() {
        YuvFrame a = BenchInput.syntheticYuv(64, 48, 270, 0L, 1L);
        YuvFrame b = BenchInput.syntheticYuv(64, 48, 270, 0L, 2L);
        byte[] rgbA = YuvConverter.toRgb(a);
        assertEquals(64 * 48 * 3, rgbA.length);
        assertEquals(48, YuvConverter.outputWidth(a));
        assertEquals(64, YuvConverter.outputHeight(a));
        assertFalse(java.util.Arrays.equals(a.y, b.y));
        assertArrayEquals(a.y, BenchInput.syntheticYuv(64, 48, 270, 99L, 1L).y, "content must not depend on timestamp");
    }

    @Test
    void syntheticYuvRejectsOddOrNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> BenchInput.syntheticYuv(63, 48, 0, 0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> BenchInput.syntheticYuv(64, 47, 0, 0L, 1L));
        assertThrows(IllegalArgumentException.class, () -> BenchInput.syntheticYuv(0, 48, 0, 0L, 1L));
    }

    @Test
    void syntheticYuvHandlesSizesNotDivisibleByEightLumaBytes() {
        YuvFrame f = BenchInput.syntheticYuv(10, 6, 0, 0L, 3L); // 60 luma bytes = 7 full longs + 4 tail bytes
        assertEquals(60, f.y.length);
        for (byte b : f.y) assertTrue((b & 0xFF) >= 64 && (b & 0xFF) <= 191);
    }
}
