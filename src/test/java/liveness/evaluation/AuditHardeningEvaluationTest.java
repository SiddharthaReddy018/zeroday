package liveness.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evaluation-side regression tests for the Oct 2026 external audit fixes.
 * Split out of liveness.flow.AuditHardeningTest unchanged: liveness.evaluation is a desktop-only
 * package (excluded from the Android unit-test source set), so tests that need IadMetrics or
 * WilsonConfidenceInterval live here. Both still run under Maven.
 */
class AuditHardeningEvaluationTest {

    @Test
    void iadRateIsNaNWithNoTrials() {
        IadMetrics m = new IadMetrics();
        assertTrue(Double.isNaN(m.freezeDetectionRate()));
        assertTrue(Double.isNaN(m.loopDetectionRate()));
        assertTrue(Double.isNaN(m.nonceSwapRejectionRate()));
    }

    @Test
    void wilsonStringHasTwoBoundsNotCenter() {
        String s = WilsonConfidenceInterval.compute(0, 9).toString();
        assertEquals(1, s.chars().filter(ch -> ch == ',').count(), s);
    }
}
