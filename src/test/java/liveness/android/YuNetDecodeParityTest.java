package liveness.android;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YuNetDecodeParityTest {

    private static final int[] STRIDES = {8, 16, 32};
    private static final int[] CHANNELS = {1, 1, 1, 1, 1, 1, 4, 4, 4, 10, 10, 10};

    @Test
    void flatDecoderMatchesTheFrozenNestedDecoderBitForBit() {
        int[][] frames = {{640, 480}, {1280, 720}, {640, 640}, {321, 479}};
        double[][] scales = {{1.0, 1.0}, {0.5, 0.5}, {1.0, 1.0}, {640.0 / 321.0, 640.0 / 321.0}};
        int observed = 0;
        for (int seed = 1; seed <= 40; seed++) {
            int f = seed % frames.length;
            float[][][][] nested = randomOutputs(new Random(seed), seed % 4 == 0);
            FaceDetector.Observation expected = ReferenceYuNetDecoder.decode(
                    nested, frames[f][0], frames[f][1], scales[f][0], scales[f][1]);
            FaceDetector.Observation viaNested = YuNetFaceDetector.decode(
                    nested, frames[f][0], frames[f][1], scales[f][0], scales[f][1]);
            FaceDetector.Observation viaFlat = YuNetFaceDetector.decodeFlat(
                    flatten(nested), frames[f][0], frames[f][1], scales[f][0], scales[f][1]);
            assertSame(expected, viaNested, "nested seed " + seed);
            assertSame(expected, viaFlat, "flat seed " + seed);
            observed += expected.faceCount;
        }
        assertTrue(observed > 0, "random tensors must produce some detections or the comparison is vacuous");
    }

    @Test
    void flatDecoderRejectsWrongTensorLengthsAndCounts() {
        float[][] flat = flatten(randomOutputs(new Random(3), false));
        assertThrows(IllegalArgumentException.class, () -> YuNetFaceDetector.decodeFlat(null, 640, 480, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decodeFlat(new float[11][], 640, 480, 1.0, 1.0));
        float[][] shortTensor = flat.clone();
        shortTensor[6] = new float[10];
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decodeFlat(shortTensor, 640, 480, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decodeFlat(flat, 0, 480, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decodeFlat(flat, 640, 480, Double.NaN, 1.0));
    }

    private static void assertSame(FaceDetector.Observation a, FaceDetector.Observation b, String label) {
        assertEquals(a.faceCount, b.faceCount, label + " faceCount");
        assertEquals(a.bboxX, b.bboxX, label + " bboxX");
        assertEquals(a.bboxY, b.bboxY, label + " bboxY");
        assertEquals(a.bboxW, b.bboxW, label + " bboxW");
        assertEquals(a.bboxH, b.bboxH, label + " bboxH");
        assertEquals(Double.doubleToLongBits(a.yawDeg), Double.doubleToLongBits(b.yawDeg));
        assertEquals(Double.doubleToLongBits(a.pitchDeg), Double.doubleToLongBits(b.pitchDeg));
        assertEquals(Double.doubleToLongBits(a.rollDeg), Double.doubleToLongBits(b.rollDeg));
        if (a.landmarks == null || b.landmarks == null) {
            assertTrue(a.landmarks == null && b.landmarks == null, label + " landmarks null mismatch");
            return;
        }
        assertEquals(a.landmarks.length, b.landmarks.length);
        for (int i = 0; i < a.landmarks.length; i++) {
            assertEquals(Double.doubleToLongBits(a.landmarks[i]), Double.doubleToLongBits(b.landmarks[i]));
        }
    }

    private static float[][] flatten(float[][][][] nested) {
        float[][] flat = new float[12][];
        for (int i = 0; i < 12; i++) {
            float[][] rows = nested[i][0];
            float[] out = new float[rows.length * CHANNELS[i]];
            for (int a = 0; a < rows.length; a++) {
                System.arraycopy(rows[a], 0, out, a * CHANNELS[i], CHANNELS[i]);
            }
            flat[i] = out;
        }
        return flat;
    }

    private static float[][][][] randomOutputs(Random r, boolean withNonFinite) {
        float[][][][] out = new float[12][][][];
        for (int level = 0; level < 3; level++) {
            int grid = 640 / STRIDES[level];
            int anchors = grid * grid;
            out[level] = new float[1][anchors][1];
            out[level + 3] = new float[1][anchors][1];
            out[level + 6] = new float[1][anchors][4];
            out[level + 9] = new float[1][anchors][10];
            for (int a = 0; a < anchors; a++) {
                out[level][0][a][0] = r.nextFloat() * 0.5f;
                out[level + 3][0][a][0] = r.nextFloat() * 0.5f;
                randomGeometry(r, out[level + 6][0][a], out[level + 9][0][a]);
            }
            for (int n = 0; n < 60; n++) {
                int a = r.nextInt(anchors);
                out[level][0][a][0] = 0.6f + r.nextFloat() * 0.6f;
                out[level + 3][0][a][0] = 0.6f + r.nextFloat() * 0.6f;
            }
            if (withNonFinite) {
                out[level][0][r.nextInt(anchors)][0] = Float.NaN;
                out[level + 3][0][r.nextInt(anchors)][0] = Float.POSITIVE_INFINITY;
                out[level + 6][0][r.nextInt(anchors)][2] = Float.POSITIVE_INFINITY;
                out[level + 9][0][r.nextInt(anchors)][3] = Float.NaN;
            }
        }
        return out;
    }

    private static void randomGeometry(Random r, float[] bbox, float[] kps) {
        bbox[0] = r.nextFloat() * 2 - 1;
        bbox[1] = r.nextFloat() * 2 - 1;
        bbox[2] = r.nextFloat() * 2 - 0.5f;
        bbox[3] = r.nextFloat() * 2 - 0.5f;
        for (int i = 0; i < kps.length; i++) kps[i] = r.nextFloat() * 4 - 2;
    }
}
