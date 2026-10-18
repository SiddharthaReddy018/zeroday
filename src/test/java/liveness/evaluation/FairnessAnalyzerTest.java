package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for FairnessAnalyzer (§12.4, §24.4, R7).
 */
public class FairnessAnalyzerTest {

    @Test
    public void biasIndexWithNoDemographics() {
        List<ScoredSample> samples = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            samples.add(new ScoredSample(80, "bona_fide", "", "bf_" + i));
        }
        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        assertEquals(0.0, fa.biasIndex(), 0.001,
                "No demographics → bias index should be 0");
    }

    @Test
    public void biasIndexWithSkinToneGroups() {
        List<ScoredSample> samples = new ArrayList<>();
        // Group mst_1: high scores (no BPCER)
        for (int i = 0; i < 35; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_l_" + i,
                    "mst_1", null, null, null, null));
        }
        // Group mst_10: some low scores (higher BPCER)
        for (int i = 0; i < 35; i++) {
            int score = (i < 10) ? 50 : 90; // 10/35 misclassified
            samples.add(new ScoredSample(score, "bona_fide", "", "bf_d_" + i,
                    "mst_10", null, null, null, null));
        }
        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        double bias = fa.biasIndex();
        assertTrue(bias > 0.0, "Bias index should be > 0 with different group BPCERs");
    }

    @Test
    public void flaggedGroupsDetected() {
        List<ScoredSample> samples = new ArrayList<>();
        // Good group
        for (int i = 0; i < 40; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_good_" + i,
                    null, null, "male", "good", null));
        }
        // Bad group with high BPCER
        for (int i = 0; i < 40; i++) {
            int score = (i < 20) ? 50 : 90; // 50% BPCER
            samples.add(new ScoredSample(score, "bona_fide", "", "bf_bad_" + i,
                    null, null, "female", "low", null));
        }

        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        List<String> flags = fa.flaggedGroups();
        assertFalse(flags.isEmpty(), "Should flag groups with >5pp BPCER swing");
    }

    @Test
    public void exploratoryLabelling() {
        List<ScoredSample> samples = new ArrayList<>();
        // Only 10 subjects in one skin tone group (< 30)
        for (int i = 0; i < 10; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_" + i,
                    "mst_3", null, null, null, null));
        }
        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        Map<String, FairnessAnalyzer.GroupResult> groups = fa.groupBpcerBySkinTone();

        assertTrue(groups.containsKey("mst_3"));
        assertTrue(groups.get("mst_3").exploratory(),
                "Cell with <30 subjects must be EXPLORATORY");
    }

    @Test
    public void conclusiveLabelling() {
        List<ScoredSample> samples = new ArrayList<>();
        // 35 unique subjects in one group
        for (int i = 0; i < 35; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_unique_" + i,
                    "mst_5", null, null, null, null));
        }
        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        Map<String, FairnessAnalyzer.GroupResult> groups = fa.groupBpcerBySkinTone();

        assertTrue(groups.containsKey("mst_5"));
        assertFalse(groups.get("mst_5").exploratory(),
                "Cell with >=30 subjects should be conclusive");
    }

    @Test
    public void reportGeneration() {
        List<ScoredSample> samples = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_" + i,
                    "mst_5", "20-30", "male", "good", "no"));
        }
        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        String report = fa.report();

        assertTrue(report.contains("Fairness Analysis"));
        assertTrue(report.contains("Bias Index"));
        assertTrue(report.contains("Wilson score intervals"));
        assertTrue(report.contains("ANOVA p-values"), "Must not use p-values (R7)");
    }
}
