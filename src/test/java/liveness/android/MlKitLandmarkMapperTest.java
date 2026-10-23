package liveness.android;

import liveness.engine.LandmarkLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MlKitLandmarkMapperTest {
    @Test
    void mapsContourGeometryIntoSharedLayoutAndAlignmentOrder() {
        double[] lm = MlKitLandmarkMapper.map(
                eye(0, 0), eye(12, 0),
                new float[]{8, 15, 10, 13, 12, 12, 14, 13, 16, 15},
                new float[]{8, 15, 10, 16, 12, 17, 14, 16, 16, 15},
                new float[]{8, 16, 10, 17, 12, 18, 14, 17, 16, 16},
                new float[]{8, 16, 10, 19, 12, 20, 14, 19, 16, 16},
                new float[]{10, 10, 12, 11, 14, 10});

        assertTrue(LandmarkLayout.hasBase(lm));
        assertTrue(LandmarkLayout.hasAlign5(lm));
        assertTrue(Double.isFinite(LandmarkLayout.meanEar(lm)));
        assertTrue(Double.isFinite(LandmarkLayout.mar(lm)));
        assertEquals(16.0, lm[LandmarkLayout.ALIGN5], 1e-6,
                "alignment order starts with subject-right eye, mapped from the second eye contour");
        assertEquals(4.0, lm[LandmarkLayout.ALIGN5 + 2], 1e-6);
        assertEquals(12.0, lm[LandmarkLayout.ALIGN5 + 4], 1e-6);
        assertEquals(8.0, lm[LandmarkLayout.ALIGN5 + 6], 1e-6);
        assertEquals(16.0, lm[LandmarkLayout.ALIGN5 + 8], 1e-6);
    }

    @Test
    void missingOrDegenerateContoursRemainUnavailable() {
        double[] missing = MlKitLandmarkMapper.map(
                eye(0, 0), eye(12, 0),
                null, null, null, null, null);
        assertFalse(LandmarkLayout.hasBase(missing));
        assertFalse(LandmarkLayout.hasAlign5(missing));
        assertTrue(Double.isNaN(missing[LandmarkLayout.MOUTH]));

        float[] flatEye = new float[]{0, 5, 2, 5, 4, 5, 6, 5, 8, 5, 4, 5};
        double[] degenerate = MlKitLandmarkMapper.map(
                flatEye, eye(12, 0),
                new float[]{8, 15, 12, 12, 16, 15},
                new float[]{8, 16, 12, 15, 16, 16},
                new float[]{8, 17, 12, 18, 16, 17},
                new float[]{8, 19, 12, 20, 16, 19},
                new float[]{10, 10, 12, 11, 14, 10});
        assertFalse(LandmarkLayout.hasBase(degenerate));
    }

    @Test
    void rejectsOddLengthAndNonFiniteContoursInsteadOfInventingPoints() {
        double[] odd = MlKitLandmarkMapper.map(
                new float[]{1, 2, 3}, eye(12, 0),
                new float[]{8, 15, 12, 12, 16, 15},
                new float[]{8, 16, 12, 15, 16, 16},
                new float[]{8, 17, 12, 18, 16, 17},
                new float[]{8, 19, 12, 20, 16, 19},
                new float[]{10, 10, 12, 11, 14, 10});
        assertFalse(LandmarkLayout.hasBase(odd));

        float[] nonFinite = eye(0, 0);
        nonFinite[3] = Float.NaN;
        double[] nan = MlKitLandmarkMapper.map(
                nonFinite, eye(12, 0),
                new float[]{8, 15, 12, 12, 16, 15},
                new float[]{8, 16, 12, 15, 16, 16},
                new float[]{8, 17, 12, 18, 16, 17},
                new float[]{8, 19, 12, 20, 16, 19},
                new float[]{10, 10, 12, 11, 14, 10});
        assertFalse(LandmarkLayout.hasBase(nan));
    }

    private static float[] eye(float xOffset, float yOffset) {
        return new float[]{
                xOffset, yOffset + 5,
                xOffset + 2, yOffset + 2,
                xOffset + 4, yOffset + 1,
                xOffset + 6, yOffset + 2,
                xOffset + 8, yOffset + 5,
                xOffset + 6, yOffset + 8,
                xOffset + 4, yOffset + 9,
                xOffset + 2, yOffset + 8
        };
    }
}
