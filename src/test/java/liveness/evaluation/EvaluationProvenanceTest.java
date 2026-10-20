package liveness.evaluation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationProvenanceTest {

    private EvaluationReport report() {
        List<ScoredSample> s = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            s.add(new ScoredSample(90, "bona_fide", "", "b" + i, "mst_2", "20-30", "female", "ok", "no"));
            s.add(new ScoredSample(10, "attack", "print", "a" + i, "mst_2", "20-30", "female", "ok", "no"));
        }
        return new EvaluationReport(new PadMetrics(s, 70), new IadMetrics(),
                new FairnessAnalyzer(s, 70), new LatencyMetrics());
    }

    @Test
    void reportWithoutProvenanceIsFlagged() {
        assertFalse(report().validateProvenance().isEmpty());
    }

    @Test
    void provenanceAppearsInReportAndSatisfiesCheck() {
        EvaluationReport r = report().addProvenance("Scorer: real ONNX").addProvenance("Bona fide are stills");
        assertTrue(r.validateProvenance().isEmpty());
        String md = r.generate();
        assertTrue(md.contains("Evaluation Provenance and Limits"));
        assertTrue(md.contains("Bona fide are stills"));
        assertTrue(r.validateContract().isEmpty(), "R6 contract must still hold");
    }

    @Test
    void notMeasuredMetricsAreListedWithReasonAndNeverGetANumber() {
        EvaluationReport r = report().addProvenance("x")
                .addNotMeasured("APAR", "needs live capture");
        String md = r.generate();
        assertTrue(md.contains("Metrics Not Measured in This Run"));
        assertTrue(md.contains("| APAR | needs live capture |"));
    }

    @Test
    void latencyMergeKeepsRawMeasurements() {
        LatencyMetrics a = new LatencyMetrics(), b = new LatencyMetrics();
        a.record("pad_scoring", 5_000_000L);
        b.record("ps_pd_total", 2_000_000L);
        b.record("ps_pd_total", 4_000_000L);
        a.mergeFrom(b);
        assertEquals(2, a.count("ps_pd_total"));
        assertEquals(1, a.count("pad_scoring"));
        assertEquals(4.0, a.max("ps_pd_total"), 1e-9);
    }

    @Test
    void iadRowsWithNoTrialsAreNotShownAsZeroRate() {
        String md = new IadMetrics().report();
        assertTrue(md.contains("not measured (no trials in this run)"));
        assertFalse(md.contains("0.0000"));
    }
}
