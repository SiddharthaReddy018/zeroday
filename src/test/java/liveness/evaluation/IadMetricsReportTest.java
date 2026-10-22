package liveness.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The IAD report must carry its provenance label, per-row Wilson CIs and no "coverage" headline. */
class IadMetricsReportTest {

    @Test
    void reportIsLabelledHasConfidenceIntervalsAndNoCoverageHeadline() {
        IadMetrics m = new IadMetrics();
        for (int i = 0; i < 30; i++) {
            m.recordFreezeAttempt(true);
            m.recordLoopAttempt(i % 10 != 0);
            m.recordNonceSwapAttempt(true);
            m.recordLegitimateSession(false);
        }
        String r = m.report();
        assertTrue(r.contains("synthetic, mechanism-level, not real-camera"));
        assertTrue(r.contains("95% Wilson CI"));
        assertTrue(r.contains("[0.8"), "loop row (27/30) must show a Wilson interval, got:\n" + r);
        assertFalse(r.contains("Mechanism coverage"), "coverage headline overstates evidence");
        assertTrue(r.contains("low-motion loops"), "known loop limits must be stated");
    }

    @Test
    void zeroTrialsStillPrintsNotMeasured() {
        String r = new IadMetrics().report();
        assertTrue(r.contains("not measured"));
        assertFalse(r.contains("0.0000"));
    }
}
