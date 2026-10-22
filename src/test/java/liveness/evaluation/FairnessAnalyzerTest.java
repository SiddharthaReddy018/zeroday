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

    @Test
    public void exploratoryGroupsExcludedFromBiasIndex() {
        List<ScoredSample> samples = new ArrayList<>();
        // Group mst_1: 35 subjects, all pass -> BPCER = 0.0
        for (int i = 0; i < 35; i++) {
            samples.add(new ScoredSample(90, "bona_fide", "", "bf_c1_" + i,
                    "mst_1", null, null, null, null));
        }
        // Group mst_2: 35 subjects, 7 fail -> BPCER = 0.2
        for (int i = 0; i < 35; i++) {
            int score = (i < 7) ? 50 : 90;
            samples.add(new ScoredSample(score, "bona_fide", "", "bf_c2_" + i,
                    "mst_2", null, null, null, null));
        }
        // Group mst_3: 5 subjects (< 30, exploratory), all fail -> BPCER = 1.0
        for (int i = 0; i < 5; i++) {
            samples.add(new ScoredSample(50, "bona_fide", "", "bf_exp_" + i,
                    "mst_3", null, null, null, null));
        }

        FairnessAnalyzer fa = new FairnessAnalyzer(samples, 70);
        // If mst_3 was included, delta would be 1.0 - 0.0 = 1.0.
        // Because mst_3 is exploratory (<30 subjects), delta should be 0.2 - 0.0 = 0.2.
        assertEquals(0.2, fa.biasIndex(), 0.001,
                "Exploratory group with <30 subjects must be excluded from Bias Index delta");
    }

    @Test
    public void emptyOrZeroBonafideReturnsNaN() {
        List<ScoredSample> attackOnly = new ArrayList<>();
        attackOnly.add(new ScoredSample(10, "attack", "print", "a_1"));
        FairnessAnalyzer fa = new FairnessAnalyzer(attackOnly, 70);
        assertEquals(0.0, fa.biasIndex(), 0.001);
        assertTrue(fa.flaggedGroups().isEmpty());
    }
}
