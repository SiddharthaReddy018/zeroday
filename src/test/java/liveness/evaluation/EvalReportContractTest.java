package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * R6 contract test (HANDOFF-2 §15.5, §15.2):
 * - Report has per-species APCER rows with CIs
 * - Report has BPCER row
 * - Report has worst-species headline
 * - Report does NOT contain standalone ACER
 * - IAD is in a separate table
 * - Report refuses pooled-only (must have per-species breakdown)
 */
public class EvalReportContractTest {

    private List<ScoredSample> buildTestSamples() {
        List<ScoredSample> samples = new ArrayList<>();
        // 40 bona fide subjects (Rule of 30 satisfied)
        for (int i = 1; i <= 40; i++) {
            samples.add(new ScoredSample(80 + (i % 20), "bona_fide", "", "bf_" + i));
        }
        // Print attacks
        for (int i = 1; i <= 20; i++) {
            samples.add(new ScoredSample(15 + (i % 10), "attack", "print", "atk_p_" + i));
        }
        // Screen attacks
        for (int i = 1; i <= 15; i++) {
            samples.add(new ScoredSample(20 + (i % 15), "attack", "screen", "atk_s_" + i));
        }
        // Video attacks
        for (int i = 1; i <= 10; i++) {
            samples.add(new ScoredSample(25 + (i % 8), "attack", "video", "atk_v_" + i));
        }
        return samples;
    }

    @Test
    public void reportHasPerSpeciesApcerRowsWithCIs() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, new IadMetrics(), null, null);
        String text = report.generate();

        // Must contain each species
        assertTrue(text.contains("print"), "Report must contain 'print' species row");
        assertTrue(text.contains("screen"), "Report must contain 'screen' species row");
        assertTrue(text.contains("video"), "Report must contain 'video' species row");

        // Must contain CI brackets
        assertTrue(text.contains("["), "Report must contain Wilson CI brackets");
    }

    @Test
    public void reportHasBpcerRow() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, null, null, null);
        String text = report.generate();

        assertTrue(text.contains("BPCER"), "Report must contain BPCER");
    }

    @Test
    public void reportHasWorstSpeciesHeadline() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, null, null, null);
        String text = report.generate();

        assertTrue(text.contains("Worst-species headline"),
                "Report must contain worst-species headline APCER");
    }

    @Test
    public void reportDoesNotContainStandaloneAcer() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, new IadMetrics(), null, null);
        String text = report.generate();

        // Check no standalone ACER (not part of APCER or BPCER)
        String[] lines = text.split("\n");
        for (String line : lines) {
            String stripped = line.replaceAll("APCER", "").replaceAll("BPCER", "");
            assertFalse(stripped.contains("ACER"),
                    "Report must not contain standalone ACER metric. Line: " + line.trim());
        }
    }

    @Test
    public void iadInSeparateTable() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        IadMetrics iad = new IadMetrics();
        iad.recordFreezeAttempt(true);
        iad.recordLoopAttempt(true);

        EvaluationReport report = new EvaluationReport(metrics, iad, null, null);
        String text = report.generate();

        assertTrue(text.contains("Injection Attack Detection"),
                "IAD metrics must be in a separate section/table");
        // IAD section should come after PAD section
        int padIdx = text.indexOf("Per-Species APCER");
        int iadIdx = text.indexOf("Injection Attack Detection");
        assertTrue(iadIdx > padIdx, "IAD table must be separate from and after PAD table");
    }

    @Test
    public void reportRefusesPooledOnly() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        // Must have per-species breakdown, not just pooled
        Map<String, PadMetrics.SpeciesResult> perSpecies = metrics.apcerAllSpecies();
        assertTrue(perSpecies.size() >= 3,
                "Must report per-species APCER (found " + perSpecies.size() + " species)");

        // Each species must have its own row
        assertTrue(perSpecies.containsKey("print"));
        assertTrue(perSpecies.containsKey("screen"));
        assertTrue(perSpecies.containsKey("video"));
    }

    @Test
    public void contractValidationPasses() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        IadMetrics iad = new IadMetrics();
        iad.recordFreezeAttempt(true);

        EvaluationReport report = new EvaluationReport(metrics, iad, null, null);
        List<String> violations = report.validateContract();

        assertTrue(violations.isEmpty(),
                "Report contract violations: " + violations);
    }

    @Test
    public void ruleOf30Validated() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        assertTrue(metrics.ruleOf30Satisfied(),
                "Test data has 40 bona fide subjects — Rule of 30 must be satisfied");
        assertEquals(40, metrics.bonafideSubjectCount());
    }

    @Test
    public void ruleOf30FailsWithTooFewSubjects() {
        List<ScoredSample> samples = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            samples.add(new ScoredSample(80, "bona_fide", "", "bf_" + i));
        }
        PadMetrics metrics = new PadMetrics(samples, 70);

        assertFalse(metrics.ruleOf30Satisfied(),
                "Only 10 subjects — Rule of 30 must not be satisfied");
    }

    @Test
    public void reportContainsCoverageStatement() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, null, null, null);
        String text = report.generate();

        assertTrue(text.contains("Honest Coverage Statement"),
                "Report must contain the honest coverage statement (§15.5)");
        assertTrue(text.contains("Experimental resistance"),
                "Coverage statement must include 3D mask as experimental resistance");
        assertTrue(text.contains("Mitigation only"),
                "Coverage statement must include deepfake as mitigation only");
    }

    @Test
    public void validateContractEnforcesSampleSizeAndConfidenceIntervals() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, new IadMetrics(), null, null);
        String text = report.generate();

        assertTrue(text.contains("N (attacks)"), "Must include N column");
        assertTrue(text.contains("(N="), "Headline metrics must include N");
        assertTrue(text.contains("95% CI"), "Must include 95% CI column");
        assertTrue(report.validateContract().isEmpty());
    }

    @Test
    public void validateContractFailsIfHeadlineIsPooled() {
        List<ScoredSample> samples = buildTestSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        EvaluationReport report = new EvaluationReport(metrics, null, null, null);
        String rep = report.generate().replace("Worst-species headline APCER", "Pooled APCER headline");

        List<String> violations = report.validateContract(rep);
        assertFalse(violations.isEmpty());
        boolean hasPooledViolation = violations.stream().anyMatch(v -> v.contains("pooled"));
        assertTrue(hasPooledViolation, "Should reject pooled headline APCER");
    }

    @Test
    public void validateContractFailsIfMissingExploratoryTagWhenNUnder30() {
        List<ScoredSample> samples = new ArrayList<>();
        // Only 5 bona fide samples
        for (int i = 0; i < 5; i++) {
            samples.add(new ScoredSample(85, "bona_fide", "", "s" + i));
            samples.add(new ScoredSample(15, "attack", "print", "s" + i));
        }
        PadMetrics metrics = new PadMetrics(samples, 70);

        EvaluationReport report = new EvaluationReport(metrics, null, null, null);
        String repWithoutExploratory = report.generate().replace("[EXPLORATORY]", "");

        List<String> violations = report.validateContract(repWithoutExploratory);
        assertFalse(violations.isEmpty());
        boolean hasExploratoryViolation = violations.stream().anyMatch(v -> v.contains("[EXPLORATORY]"));
        assertTrue(hasExploratoryViolation, "Should fail contract validation if missing [EXPLORATORY] when N < 30");
    }
}
