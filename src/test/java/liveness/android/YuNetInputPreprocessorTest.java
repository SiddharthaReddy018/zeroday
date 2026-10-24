package liveness.android;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YuNetInputPreprocessorTest {

    private static final int SIZE = 640;
    private static final int PLANE = SIZE * SIZE;

    @Test
    void matchesTheOriginalPerPixelImplementationBitForBitAcrossSizes() {
        int[][] sizes = {{640, 480}, {1280, 720}, {1920, 1080}, {320, 240}, {641, 479},
                {100, 300}, {300, 100}, {1, 1}, {640, 640}, {2, 640}, {640, 2}, {700, 640}};
        Random random = new Random(7);
        for (int[] size : sizes) {
            byte[] rgb = randomRgb(random, size[0], size[1]);
            YuNetInputPreprocessor preprocessor = new YuNetInputPreprocessor();
            YuNetInputPreprocessor.Resize resize = preprocessor.prepare(rgb, size[0], size[1]);
            float[] expected = new float[3 * PLANE];
            double[] expectedScales = referencePrepare(rgb, size[0], size[1], expected);
            assertEquals(Double.doubleToLongBits(expectedScales[0]), Double.doubleToLongBits(resize.scaleX));
            assertEquals(Double.doubleToLongBits(expectedScales[1]), Double.doubleToLongBits(resize.scaleY));
            assertSameBits(expected, preprocessor.buffer(), size[0] + "x" + size[1]);
        }
    }

    @Test
    void reusedBufferStaysIdenticalWhenFrameShapeChanges() {
        int[][] sequence = {{640, 480}, {480, 640}, {100, 300}, {640, 480}, {641, 479},
                {1280, 720}, {300, 100}, {640, 640}, {640, 480}, {640, 480}};
        Random random = new Random(11);
        YuNetInputPreprocessor reused = new YuNetInputPreprocessor();
        for (int i = 0; i < sequence.length; i++) {
            int w = sequence[i][0];
            int h = sequence[i][1];
            byte[] rgb = randomRgb(random, w, h);
            reused.prepare(rgb, w, h);
            float[] expected = new float[3 * PLANE];
            referencePrepare(rgb, w, h, expected);
            assertSameBits(expected, reused.buffer(), "frame " + i + " " + w + "x" + h);
        }
    }

    @Test
    void identityLayoutFillsPlanesAsBgrAndLeavesPaddingZero() {
        byte[] rgb = new byte[640 * 480 * 3];
        rgb[0] = (byte) 10;
        rgb[1] = (byte) 20;
        rgb[2] = (byte) 30;
        YuNetInputPreprocessor p = new YuNetInputPreprocessor();
        p.prepare(rgb, 640, 480);
        float[] b = p.buffer();
        assertEquals(30.0, b[0], 0.0);
        assertEquals(20.0, b[PLANE], 0.0);
        assertEquals(10.0, b[2 * PLANE], 0.0);
        assertEquals(0.0, b[480 * SIZE], 0.0);
        assertTrue(b[479 * SIZE + 639] == 0.0f);
    }

    private static byte[] randomRgb(Random random, int w, int h) {
        byte[] rgb = new byte[w * h * 3];
        random.nextBytes(rgb);
        return rgb;
    }

    private static void assertSameBits(float[] expected, float[] actual, String label) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToRawIntBits(expected[i]) != Float.floatToRawIntBits(actual[i])) {
                throw new AssertionError(label + ": first mismatch at " + i + " expected " + expected[i]
                        + " actual " + actual[i]);
            }
        }
    }

    /** Frozen copy of the original per-pixel implementation (PR #6, 058d46f). Test oracle only. */
    private static double[] referencePrepare(byte[] rgb, int width, int height, float[] inputBuffer) {
        double scale = Math.min(SIZE / (double) width, SIZE / (double) height);
        int resizedWidth = Math.max(1, Math.min(SIZE, (int) Math.round(width * scale)));
        int resizedHeight = Math.max(1, Math.min(SIZE, (int) Math.round(height * scale)));
        double scaleX = resizedWidth / (double) width;
        double scaleY = resizedHeight / (double) height;
        Arrays.fill(inputBuffer, 0.0f);
        for (int y = 0; y < resizedHeight; y++) {
            double sourceY = clamp((y + 0.5) / scaleY - 0.5, 0.0, height - 1.0);
            int y0 = (int) Math.floor(sourceY);
            int y1 = Math.min(height - 1, y0 + 1);
            double fy = sourceY - y0;
            for (int x = 0; x < resizedWidth; x++) {
                double sourceX = clamp((x + 0.5) / scaleX - 0.5, 0.0, width - 1.0);
                int x0 = (int) Math.floor(sourceX);
                int x1 = Math.min(width - 1, x0 + 1);
                double fx = sourceX - x0;
                int pixel = y * SIZE + x;
                int src00 = (y0 * width + x0) * 3;
                int src10 = (y0 * width + x1) * 3;
                int src01 = (y1 * width + x0) * 3;
                int src11 = (y1 * width + x1) * 3;
                inputBuffer[pixel] = (float) interpolate(rgb, src00, src10, src01, src11, 2, fx, fy);
                inputBuffer[PLANE + pixel] = (float) interpolate(rgb, src00, src10, src01, src11, 1, fx, fy);
                inputBuffer[2 * PLANE + pixel] = (float) interpolate(rgb, src00, src10, src01, src11, 0, fx, fy);
            }
        }
        return new double[]{scaleX, scaleY};
    }

    private static double interpolate(byte[] rgb, int p00, int p10, int p01, int p11,
                                      int channel, double fx, double fy) {
        double v00 = rgb[p00 + channel] & 0xff;
        double v10 = rgb[p10 + channel] & 0xff;
        double v01 = rgb[p01 + channel] & 0xff;
        double v11 = rgb[p11 + channel] & 0xff;
        double top = v00 * (1.0 - fx) + v10 * fx;
        double bottom = v01 * (1.0 - fx) + v11 * fx;
        return top * (1.0 - fy) + bottom * fy;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
