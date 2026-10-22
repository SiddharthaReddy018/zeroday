package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the full evaluation harness orchestration.
 */
public class EvaluationHarnessTest {

    @Test
    public void runWithMockScorer() {
        List<ManifestEntry> entries = buildManifest();

        // Mock scorer: bona fide → high score, attack → low score
        EvaluationHarness.Scorer scorer = new EvaluationHarness.Scorer() {
            public int score(ManifestEntry entry) {
                return entry.isBonafide() ? 85 : 15;
            }
        };

        EvaluationHarness harness = new EvaluationHarness(scorer, 70);
        EvaluationHarness.EvaluationResult result = harness.run(entries);

        assertTrue(result.scoredSamples() > 0);
        assertEquals(0.0, result.padMetrics().bpcer().rate(), 0.001,
                "All bona fide scored 85 (> 70) → BPCER = 0");

        for (String species : result.padMetrics().species()) {
            assertEquals(0.0, result.padMetrics().apcer(species).rate(), 0.001,
                    "All attacks scored 15 (< 70) → APCER = 0 for " + species);
        }
    }

    @Test
    public void onlyTestSplitIsScored() {
        List<ManifestEntry> entries = buildManifest();

        final int[] scoreCallCount = {0};
        EvaluationHarness.Scorer scorer = new EvaluationHarness.Scorer() {
            public int score(ManifestEntry entry) {
                scoreCallCount[0]++;
                return 50;
            }
        };

        EvaluationHarness harness = new EvaluationHarness(scorer, 70);
        EvaluationHarness.EvaluationResult result = harness.run(entries);

        assertEquals(result.testEntries(), scoreCallCount[0],
                "Only test split entries should be scored");
        assertTrue(result.trainEntries() > 0, "Should have train entries");
        assertTrue(result.devEntries() > 0, "Should have dev entries");
    }

    @Test
    public void reportIsGenerated() {
        List<ManifestEntry> entries = buildManifest();

        EvaluationHarness.Scorer scorer = new EvaluationHarness.Scorer() {
            public int score(ManifestEntry entry) {
                return entry.isBonafide() ? 85 : 15;
            }
        };

        EvaluationHarness harness = new EvaluationHarness(scorer, 70);
        EvaluationHarness.EvaluationResult result = harness.run(entries);

        String report = result.report().generate();
        assertNotNull(report);
        assertTrue(report.contains("Evaluation Report"));
        assertTrue(report.contains("Per-Species APCER"));
        assertTrue(report.contains("BPCER"));
    }

    @Test
    public void manifestValidationWarnings() {
        List<ManifestEntry> entries = new ArrayList<>();
        // Only 2 bona fide subjects
        entries.add(new ManifestEntry("a.jpg", "bona_fide", "", "s1", "test"));
        entries.add(new ManifestEntry("b.jpg", "bona_fide", "", "s2", "test"));
        entries.add(new ManifestEntry("c.jpg", "attack", "print", "s3", "test"));

        List<String> issues = EvaluationHarness.validateManifest(entries);
        boolean hasRuleOf30 = false;
        for (String issue : issues) {
            if (issue.contains("Rule of 30")) hasRuleOf30 = true;
        }
        assertTrue(hasRuleOf30, "Should warn about Rule of 30");
    }

    @Test
    public void latencyIsTracked() {
        List<ManifestEntry> entries = buildManifest();

        EvaluationHarness.Scorer scorer = new EvaluationHarness.Scorer() {
            public int score(ManifestEntry entry) {
                // Simulate some work
                long x = 0;
                for (int i = 0; i < 1000; i++) x += i;
                return entry.isBonafide() ? 85 : 15;
            }
        };

        EvaluationHarness harness = new EvaluationHarness(scorer, 70);
        EvaluationHarness.EvaluationResult result = harness.run(entries);

        assertTrue(result.latency().count("pad_scoring") > 0);
        assertTrue(result.latency().percentile("pad_scoring", 50) >= 0);
    }

    @Test
    public void strictRuleOf30EnforcedByDefault() {
        List<ManifestEntry> entries = new ArrayList<>();
        // Only 10 bona fide and 10 attacks in test, train has 5
        for (int i = 0; i < 5; i++) {
            entries.add(new ManifestEntry("tr_bf_" + i, "bona_fide", "", "s" + i, "train"));
        }
        for (int i = 0; i < 10; i++) {
            entries.add(new ManifestEntry("te_bf_" + i, "bona_fide", "", "s" + i, "test"));
            entries.add(new ManifestEntry("te_at_" + i, "attack", "print", "s" + i, "test"));
        }

        EvaluationHarness harness = new EvaluationHarness(e -> 80, 70);
        // Default run throws IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> harness.run(entries));
        assertTrue(ex.getMessage().contains("Rule of 30"));

        // allowExploratory=true bypasses and marks report [EXPLORATORY]
        EvaluationHarness.EvaluationResult res = harness.run(entries, true);
        assertNotNull(res);
        String report = res.report().generate();
        assertTrue(report.contains("[EXPLORATORY]"));
    }

    @Test
    public void emptySplitThrowsIllegalStateException() {
        List<ManifestEntry> entries = new ArrayList<>();
        // 35 bona fide and 35 attacks in test, but 0 in train
        for (int i = 0; i < 35; i++) {
            entries.add(new ManifestEntry("te_bf_" + i, "bona_fide", "", "s" + i, "test"));
            entries.add(new ManifestEntry("te_at_" + i, "attack", "print", "s" + i, "test"));
        }

        EvaluationHarness harness = new EvaluationHarness(e -> 80, 70);
        assertThrows(IllegalStateException.class, () -> harness.run(entries));

        EvaluationHarness.EvaluationResult res = harness.run(entries, true);
        assertNotNull(res);
    }

    @Test
    public void iadMechanismRunnerPopulatesRealMetrics() {
        List<ManifestEntry> entries = buildManifest();
        EvaluationHarness harness = new EvaluationHarness(e -> 80, 70);
        EvaluationHarness.EvaluationResult result = harness.run(entries);

        IadMetrics iad = result.iadMetrics();
        assertNotNull(iad);
        assertTrue(iad.freezeTotal() >= 30);
        assertTrue(iad.loopTotal() >= 30);
        assertTrue(iad.nonceSwapTotal() >= 30);
        assertTrue(iad.legitimateTotal() >= 30);

        assertTrue(iad.freezeDetectionRate() > 0.9);
        assertTrue(iad.loopDetectionRate() > 0.9);
        assertTrue(iad.nonceSwapRejectionRate() > 0.9);
        assertTrue(iad.mechanismCoverage() > 0.99);
        assertFalse(Double.isNaN(iad.averageDetectionLatencyMs()));
    }

    private List<ManifestEntry> buildManifest() {
        List<ManifestEntry> entries = new ArrayList<>();
        // Train split
        for (int i = 0; i < 10; i++) {
            entries.add(new ManifestEntry("train_bf_" + i + ".jpg", "bona_fide", "", "t_bf_" + i, "train"));
            entries.add(new ManifestEntry("train_atk_" + i + ".jpg", "attack", "print", "t_atk_" + i, "train"));
        }
        // Dev split
        for (int i = 0; i < 5; i++) {
            entries.add(new ManifestEntry("dev_bf_" + i + ".jpg", "bona_fide", "", "d_bf_" + i, "dev"));
            entries.add(new ManifestEntry("dev_atk_" + i + ".jpg", "attack", "screen", "d_atk_" + i, "dev"));
        }
        // Test split with 35 bona fide subjects (Rule of 30)
        for (int i = 0; i < 35; i++) {
            entries.add(new ManifestEntry("test_bf_" + i + ".jpg", "bona_fide", "", "bf_" + i, "test"));
        }
        for (int i = 0; i < 20; i++) {
            entries.add(new ManifestEntry("test_print_" + i + ".jpg", "attack", "print", "atk_p_" + i, "test"));
        }
        for (int i = 0; i < 15; i++) {
            entries.add(new ManifestEntry("test_screen_" + i + ".jpg", "attack", "screen", "atk_s_" + i, "test"));
        }
        for (int i = 0; i < 10; i++) {
            entries.add(new ManifestEntry("test_video_" + i + ".mp4", "attack", "video", "atk_v_" + i, "test"));
        }
        return entries;
    }
}
