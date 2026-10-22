package liveness.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fairness analysis per §12.4, §24.4, R7.
 * <p>
 * Bias Index = Σ_c (metric_max_c − metric_min_c) over categories.
 * Categories: Monk Skin Tone, lighting, glasses, age group, gender.
 * <p>
 * A cell with fewer than 30 bona fide subjects is labelled "exploratory" (R7)
 * and excluded from the conclusive Bias Index to prevent noisy estimates.
 * Reports rates/differences with CIs, not ANOVA p-values.
 * Age bins: 0-20, 20-30, 30-40, 40-50, 50+ (comparable with U26).
 */
public final class FairnessAnalyzer {

    /** Minimum bona fide subjects per cell for conclusive analysis (Rule of 30). */
    private static final int MIN_SUBJECTS_PER_CELL = 30;

    /** BPCER swing threshold for flagging (±5 percentage points). */
    private static final double SWING_THRESHOLD_PP = 5.0;

    private final List<ScoredSample> samples;
    private final int threshold;

    public FairnessAnalyzer(List<ScoredSample> samples, int threshold) {
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.threshold = threshold;
    }

    // ── Bias Index ─────────────────────────────────────────────────────

    /**
     * Bias Index (DFIC paper, Gonçalves et al.):
     * Σ_c (metric_max_c − metric_min_c) over demographic categories.
     * Lower is more fair.
     * Exploratory groups (<30 subjects) are excluded from the delta per Rule of 30.
     *
     * @return the aggregate Bias Index over all available categories
     */
    public double biasIndex() {
        double totalBias = 0.0;
        totalBias += categoryBias(groupBpcerBySkinTone());
        totalBias += categoryBias(groupBpcerByGender());
        totalBias += categoryBias(groupBpcerByAgeGroup());
        totalBias += categoryBias(groupBpcerByLighting());
        totalBias += categoryBias(groupBpcerByGlasses());
        return totalBias;
    }

    /**
     * Per-category bias: max − min BPCER across conclusive groups within a category.
     * Per Rule of 30 (R7), groups with <30 subjects are excluded from the delta.
     */
    private double categoryBias(Map<String, GroupResult> groups) {
        List<GroupResult> conclusive = new ArrayList<>();
        for (GroupResult g : groups.values()) {
            if (!g.exploratory() && !Double.isNaN(g.bpcer())) {
                conclusive.add(g);
            }
        }
        if (conclusive.size() < 2) return 0.0;
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (GroupResult g : conclusive) {
            if (g.bpcer() > max) max = g.bpcer();
            if (g.bpcer() < min) min = g.bpcer();
        }
        return max - min;
    }

    // ── Grouping by demographic ────────────────────────────────────────

    public Map<String, GroupResult> groupBpcerBySkinTone() {
        return groupBpcer(new GroupExtractor() {
            public String extract(ScoredSample s) { return s.skinTone(); }
        });
    }

    public Map<String, GroupResult> groupBpcerByGender() {
        return groupBpcer(new GroupExtractor() {
            public String extract(ScoredSample s) { return s.gender(); }
        });
    }

    public Map<String, GroupResult> groupBpcerByAgeGroup() {
        return groupBpcer(new GroupExtractor() {
            public String extract(ScoredSample s) { return s.ageGroup(); }
        });
    }

    public Map<String, GroupResult> groupBpcerByLighting() {
        return groupBpcer(new GroupExtractor() {
            public String extract(ScoredSample s) { return s.lighting(); }
        });
    }

    public Map<String, GroupResult> groupBpcerByGlasses() {
        return groupBpcer(new GroupExtractor() {
            public String extract(ScoredSample s) { return s.glasses(); }
        });
    }

    private Map<String, GroupResult> groupBpcer(GroupExtractor extractor) {
        // Group bona fide samples by the demographic attribute using LinkedHashMap for deterministic ordering
        Map<String, List<ScoredSample>> groups = new LinkedHashMap<>();
        for (ScoredSample s : samples) {
            if (!s.isBonafide()) continue;
            String key = extractor.extract(s);
            if (key == null || key.isEmpty()) continue;
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<>());
            }
            groups.get(key).add(s);
        }

        Map<String, GroupResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, List<ScoredSample>> entry : groups.entrySet()) {
            List<ScoredSample> group = entry.getValue();
            int total = group.size();
            int misclassified = 0;
            Set<String> subjects = new HashSet<>();
            for (ScoredSample s : group) {
                subjects.add(s.subjectId());
                if (s.score() < threshold) {
                    misclassified++;
                }
            }
            double bpcer = total == 0 ? Double.NaN : (double) misclassified / total;
            WilsonConfidenceInterval.Interval ci = total == 0
                    ? new WilsonConfidenceInterval.Interval(Double.NaN, Double.NaN, Double.NaN)
                    : WilsonConfidenceInterval.compute(misclassified, total);
            boolean exploratory = subjects.size() < MIN_SUBJECTS_PER_CELL;
            results.put(entry.getKey(),
                    new GroupResult(entry.getKey(), bpcer, total, subjects.size(), ci, exploratory));
        }
        return results;
    }

    // ── Flags ──────────────────────────────────────────────────────────

    /**
     * Flags any group whose BPCER swings > ±5 pp from the overall BPCER.
     */
    public List<String> flaggedGroups() {
        double overallBpcer = overallBpcer();
        if (Double.isNaN(overallBpcer)) return Collections.emptyList();
        List<String> flagged = new ArrayList<>();
        addFlags(flagged, groupBpcerBySkinTone(), overallBpcer, "skin_tone");
        addFlags(flagged, groupBpcerByGender(), overallBpcer, "gender");
        addFlags(flagged, groupBpcerByAgeGroup(), overallBpcer, "age_group");
        addFlags(flagged, groupBpcerByLighting(), overallBpcer, "lighting");
        addFlags(flagged, groupBpcerByGlasses(), overallBpcer, "glasses");
        return flagged;
    }

    private void addFlags(List<String> flagged, Map<String, GroupResult> groups,
                          double overallBpcer, String category) {
        for (GroupResult g : groups.values()) {
            if (Double.isNaN(g.bpcer())) continue;
            double swingPp = Math.abs(g.bpcer - overallBpcer) * 100.0;
            if (swingPp > SWING_THRESHOLD_PP) {
                flagged.add(category + "=" + g.group
                        + " (BPCER=" + String.format("%.4f", g.bpcer)
                        + ", swing=" + String.format("%.1f", swingPp) + "pp"
                        + (g.exploratory ? ", EXPLORATORY" : "") + ")");
            }
        }
    }

    private double overallBpcer() {
        int bonafide = 0;
        int misclassified = 0;
        for (ScoredSample s : samples) {
            if (s.isBonafide()) {
                bonafide++;
                if (s.score() < threshold) misclassified++;
            }
        }
        return bonafide == 0 ? Double.NaN : (double) misclassified / bonafide;
    }

    // ── Report ─────────────────────────────────────────────────────────

    /**
     * Generates the fairness analysis report section.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Fairness Analysis (§12.4, §24.4)\n\n");
        sb.append(String.format("**Bias Index:** %.6f (lower = more fair)\n\n", biasIndex()));

        appendGroupTable(sb, "Monk Skin Tone", groupBpcerBySkinTone());
        appendGroupTable(sb, "Gender", groupBpcerByGender());
        appendGroupTable(sb, "Age Group", groupBpcerByAgeGroup());
        appendGroupTable(sb, "Lighting", groupBpcerByLighting());
        appendGroupTable(sb, "Glasses", groupBpcerByGlasses());

        List<String> flags = flaggedGroups();
        if (!flags.isEmpty()) {
            sb.append("### Flagged Groups (BPCER swing > ±5pp)\n\n");
            for (String f : flags) {
                sb.append("- ").append(f).append("\n");
            }
        } else {
            sb.append("No groups flagged for BPCER swing > ±5pp.\n");
        }
        sb.append("\n> CIs are Wilson score intervals at 95%. Cells with <30 subjects are EXPLORATORY (R7).\n");
        sb.append("> No ANOVA p-values reported — effect sizes and CIs only (R7).\n");
        return sb.toString();
    }

    private void appendGroupTable(StringBuilder sb, String category,
                                  Map<String, GroupResult> groups) {
        if (groups.isEmpty()) return;
        sb.append("### ").append(category).append("\n\n");
        sb.append("| Group | BPCER | N | Subjects | CI (95%) | Status |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (GroupResult g : groups.values()) {
            sb.append(String.format("| %s | %.4f | %d | %d | %s | %s |\n",
                    g.group, g.bpcer, g.total, g.subjectCount, g.ci,
                    g.exploratory ? "EXPLORATORY" : "conclusive"));
        }
        sb.append("\n");
    }

    // ── Result types ───────────────────────────────────────────────────

    /** Result for a single demographic group. */
    public static final class GroupResult {
        private final String group;
        private final double bpcer;
        private final int total;
        private final int subjectCount;
        private final WilsonConfidenceInterval.Interval ci;
        private final boolean exploratory;

        public GroupResult(String group, double bpcer, int total, int subjectCount,
                           WilsonConfidenceInterval.Interval ci, boolean exploratory) {
            this.group = group;
            this.bpcer = bpcer;
            this.total = total;
            this.subjectCount = subjectCount;
            this.ci = ci;
            this.exploratory = exploratory;
        }

        public String group()       { return group; }
        public double bpcer()       { return bpcer; }
        public int total()          { return total; }
        public int subjectCount()   { return subjectCount; }
        public WilsonConfidenceInterval.Interval ci() { return ci; }
        public boolean exploratory(){ return exploratory; }
    }

    private interface GroupExtractor {
        String extract(ScoredSample s);
    }
}
