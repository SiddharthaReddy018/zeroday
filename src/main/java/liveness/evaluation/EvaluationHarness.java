package liveness.evaluation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * M5 Evaluation Harness Orchestrator (§12, §24, HANDOFF-2 M5).
 * <p>
 * 1. Loads manifest from CSV
 * 2. Splits by subject (train/dev/test using manifest split column)
 * 3. Scores each sample in the test split (via a pluggable scorer)
 * 4. Computes per-species APCER with Wilson CIs
 * 5. Computes BPCER with Wilson CI
 * 6. Computes worst-species headline APCER
 * 7. Generates DET curve points
 * 8. Runs fairness analysis
 * 9. Generates text report
 * 10. Validates Rule of 30
 */
public final class EvaluationHarness {

    /**
     * Pluggable scorer interface. The harness calls this for each sample.
     * The scorer returns a score 0-100 (higher = more live).
     */
    public interface Scorer {
        /**
         * Score a single sample.
         *
         * @param entry the manifest entry to score
         * @return PAD score 0-100 (higher = more live)
         */
        int score(ManifestEntry entry);
    }

    private final Scorer scorer;
    private final int threshold;

    /**
     * @param scorer    pluggable scoring function
     * @param threshold decision threshold (score >= threshold → accepted as bona fide)
     */
    public EvaluationHarness(Scorer scorer, int threshold) {
        this.scorer = scorer;
        this.threshold = threshold;
    }

    /**
     * Runs the full evaluation on a manifest file.
     *
     * @param manifestPath path to the CSV manifest
     * @return the generated evaluation report
     * @throws IOException if manifest cannot be read
     */
    public EvaluationResult run(Path manifestPath) throws IOException {
        List<ManifestEntry> allEntries = ManifestLoader.load(manifestPath);
        return run(allEntries);
    }

    /**
     * Runs the full evaluation on pre-loaded manifest entries.
     */
    public EvaluationResult run(List<ManifestEntry> allEntries) {
        // Split by the manifest's split column
        List<ManifestEntry> trainEntries = new ArrayList<>();
        List<ManifestEntry> devEntries = new ArrayList<>();
        List<ManifestEntry> testEntries = new ArrayList<>();

        for (ManifestEntry e : allEntries) {
            switch (e.split()) {
                case "train":
                    trainEntries.add(e);
                    break;
                case "dev":
                    devEntries.add(e);
                    break;
                case "test":
                    testEntries.add(e);
                    break;
                default:
                    // skip unknown splits
                    break;
            }
        }

        // Score test split only (§12.3: run test split once with frozen thresholds)
        List<ScoredSample> scoredSamples = new ArrayList<>();
        LatencyMetrics latency = new LatencyMetrics();

        for (ManifestEntry entry : testEntries) {
            long startNs = System.nanoTime();
            int score = scorer.score(entry);
            long elapsed = System.nanoTime() - startNs;
            latency.record("pad_scoring", elapsed);

            ScoredSample sample = new ScoredSample(
                    score, entry.label(),
                    entry.species() != null ? entry.species() : "",
                    entry.subjectId(),
                    entry.skinTone(), entry.ageGroup(), entry.gender(),
                    entry.lighting(), entry.glasses());
            scoredSamples.add(sample);
        }

        // Compute metrics
        PadMetrics padMetrics = new PadMetrics(scoredSamples, threshold);
        FairnessAnalyzer fairness = new FairnessAnalyzer(scoredSamples, threshold);
        IadMetrics iadMetrics = new IadMetrics(); // populated externally

        // Generate report
        EvaluationReport report = new EvaluationReport(padMetrics, iadMetrics, fairness, latency);

        return new EvaluationResult(
                allEntries.size(), trainEntries.size(), devEntries.size(), testEntries.size(),
                scoredSamples.size(), padMetrics, iadMetrics, fairness, latency, report);
    }

    /**
     * Validates the manifest for evaluation readiness.
     *
     * @return list of warnings/errors (empty = ready)
     */
    public static List<String> validateManifest(List<ManifestEntry> entries) {
        List<String> issues = new ArrayList<>();

        // Check for test split
        boolean hasTest = false;
        Set<String> testBfSubjects = new HashSet<>();
        Set<String> testSpecies = new HashSet<>();
        int testAttacks = 0;
        int testBonafide = 0;

        for (ManifestEntry e : entries) {
            if ("test".equals(e.split())) {
                hasTest = true;
                if (e.isBonafide()) {
                    testBonafide++;
                    testBfSubjects.add(e.subjectId());
                } else if (e.isAttack()) {
                    testAttacks++;
                    if (e.species() != null && !e.species().isEmpty()) {
                        testSpecies.add(e.species());
                    }
                }
            }
        }

        if (!hasTest) {
            issues.add("No test split found in manifest");
        }
        if (testBonafide == 0) {
            issues.add("No bona fide samples in test split");
        }
        if (testAttacks == 0) {
            issues.add("No attack samples in test split");
        }
        if (testBfSubjects.size() < 30) {
            issues.add("Rule of 30 not satisfied: only " + testBfSubjects.size()
                    + " bona fide subjects in test split (need >=30)");
        }
        if (testSpecies.isEmpty()) {
            issues.add("No PAI species labels found in test attack samples");
        }

        return issues;
    }

    // ── Result ─────────────────────────────────────────────────────────

    /** Evaluation result bundle. */
    public static final class EvaluationResult {
        private final int totalEntries;
        private final int trainEntries;
        private final int devEntries;
        private final int testEntries;
        private final int scoredSamples;
        private final PadMetrics padMetrics;
        private final IadMetrics iadMetrics;
        private final FairnessAnalyzer fairness;
        private final LatencyMetrics latency;
        private final EvaluationReport report;

        public EvaluationResult(int totalEntries, int trainEntries, int devEntries,
                                int testEntries, int scoredSamples,
                                PadMetrics padMetrics, IadMetrics iadMetrics,
                                FairnessAnalyzer fairness, LatencyMetrics latency,
                                EvaluationReport report) {
            this.totalEntries = totalEntries;
            this.trainEntries = trainEntries;
            this.devEntries = devEntries;
            this.testEntries = testEntries;
            this.scoredSamples = scoredSamples;
            this.padMetrics = padMetrics;
            this.iadMetrics = iadMetrics;
            this.fairness = fairness;
            this.latency = latency;
            this.report = report;
        }

        public int totalEntries()          { return totalEntries; }
        public int trainEntries()          { return trainEntries; }
        public int devEntries()            { return devEntries; }
        public int testEntries()           { return testEntries; }
        public int scoredSamples()         { return scoredSamples; }
        public PadMetrics padMetrics()     { return padMetrics; }
        public IadMetrics iadMetrics()     { return iadMetrics; }
        public FairnessAnalyzer fairness() { return fairness; }
        public LatencyMetrics latency()    { return latency; }
        public EvaluationReport report()   { return report; }
    }
}
