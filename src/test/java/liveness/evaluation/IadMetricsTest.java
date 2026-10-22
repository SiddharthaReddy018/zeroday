package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

/**
 * Tests for IadMetrics (§12.2, §24.2).
 */
public class IadMetricsTest {

    @Test
    public void initialMetricsAreZeroAndReturnNaN() {
        IadMetrics metrics = new IadMetrics();
        assertEquals(0, metrics.freezeTotal());
        assertEquals(0, metrics.freezeDetected());
        assertEquals(0, metrics.loopTotal());
        assertEquals(0, metrics.loopDetected());
        assertEquals(0, metrics.nonceSwapTotal());
        assertEquals(0, metrics.nonceSwapRejected());
        assertEquals(0, metrics.legitimateTotal());
        assertEquals(0, metrics.legitimateFalseAlarms());

        assertTrue(Double.isNaN(metrics.freezeDetectionRate()));
        assertTrue(Double.isNaN(metrics.loopDetectionRate()));
        assertTrue(Double.isNaN(metrics.nonceSwapRejectionRate()));
        assertTrue(Double.isNaN(metrics.falseIadRate()));

        String report = metrics.report();
        assertTrue(report.contains("not measured (no trials in this run)"));
        assertEquals("CEN_TS_18099_BASELINE", metrics.operatingPoint());
    }

    @Test
    public void syntheticEvaluationComputesCorrectRates() {
        IadMetrics metrics = new IadMetrics();
        Random rng = new Random(42);

        metrics.runSyntheticEvaluation(
                100, 0.95, // freeze
                80, 0.90,  // loop
                60, 1.0,   // nonce swap
                200, 0.02, // legit
                rng
        );

        assertEquals(100, metrics.freezeTotal());
        assertEquals(80, metrics.loopTotal());
        assertEquals(60, metrics.nonceSwapTotal());
        assertEquals(200, metrics.legitimateTotal());

        assertFalse(Double.isNaN(metrics.freezeDetectionRate()));
        assertTrue(metrics.freezeDetectionRate() > 0.85);

        assertFalse(Double.isNaN(metrics.loopDetectionRate()));
        assertTrue(metrics.loopDetectionRate() > 0.80);

        assertEquals(1.0, metrics.nonceSwapRejectionRate(), 0.001);
        assertTrue(metrics.falseIadRate() < 0.10);

        String report = metrics.report();
        assertFalse(report.contains("not measured"));
        assertTrue(report.contains("Freeze detection"));
        assertTrue(report.contains("Loop replay detection"));
        assertTrue(report.contains("Nonce-swap rejection"));
        assertTrue(report.contains("False IAD (legitimate)"));
    }
}
