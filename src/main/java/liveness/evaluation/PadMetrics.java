package liveness.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ISO/IEC 30107-3 PAD metrics calculator (§12.1, §24.1).
 * <p>
 * Computes APCER per PAI species, BPCER, worst-species APCER,
 * D-EER, and DET curve points. All rates with Wilson CIs.
 * <p>
 * <b>Never computes or reports ACER</b> — ISO/IEC 30107-3 explicitly forbids
 * combined/averaged metrics (§24.1, R6).
 */
public final class PadMetrics {

    private final List<ScoredSample> samples;
    private final int threshold;

    /**
     * @param samples   scored evaluation samples
     * @param threshold decision threshold (0-100); score >= threshold is accepted as bona fide
     */
    public PadMetrics(List<ScoredSample> samples, int threshold) {
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.threshold = threshold;
    }

    // ── Per-species APCER ──────────────────────────────────────────────

    /**
     * APCER for a single PAI species: proportion of attack presentations
     * of species s classified as bona fide (score >= threshold).
     */
    public SpeciesResult apcer(String species) {
        int attacks = 0;
        int misclassified = 0;
        for (ScoredSample s : samples) {
            if (s.isAttack() && species.equals(s.species())) {
                attacks++;
                if (s.score() >= threshold) {
                    misclassified++;
                }
            }
        }
        if (attacks == 0) {
            return new SpeciesResult(species, Double.NaN, 0,
                    new WilsonConfidenceInterval.Interval(Double.NaN, Double.NaN, Double.NaN));
        }
        double rate = (double) misclassified / attacks;
        WilsonConfidenceInterval.Interval ci =
                WilsonConfidenceInterval.compute(misclassified, attacks);
        return new SpeciesResult(species, rate, attacks, ci);
    }

    /**
     * APCER for all species found in the samples, returned in deterministic order.
     */
    public Map<String, SpeciesResult> apcerAllSpecies() {
        Set<String> speciesSet = new java.util.TreeSet<>();
        for (ScoredSample s : samples) {
            if (s.isAttack() && s.species() != null && !s.species().isEmpty()) {
                speciesSet.add(s.species());
            }
        }
        Map<String, SpeciesResult> results = new java.util.LinkedHashMap<>();
        for (String sp : speciesSet) {
            results.put(sp, apcer(sp));
        }
        return results;
    }

    /**
     * Worst-species APCER — the ISO system-level headline recommendation
     * is the maximum APCER over all species (R6).
     */
    public SpeciesResult worstSpeciesApcer() {
        Map<String, SpeciesResult> all = apcerAllSpecies();
        SpeciesResult worst = null;
        for (SpeciesResult r : all.values()) {
            if (Double.isNaN(r.rate())) continue;
            if (worst == null || r.rate() > worst.rate()) {
                worst = r;
            }
        }
        return worst;
    }

    // ── BPCER ──────────────────────────────────────────────────────────

    /**
     * BPCER: proportion of bona fide presentations classified as attack
     * (score < threshold).
     */
    public RateResult bpcer() {
        int bonafide = 0;
        int misclassified = 0;
        for (ScoredSample s : samples) {
            if (s.isBonafide()) {
                bonafide++;
                if (s.score() < threshold) {
                    misclassified++;
                }
            }
        }
        if (bonafide == 0) {
            return new RateResult(Double.NaN, 0,
                    new WilsonConfidenceInterval.Interval(Double.NaN, Double.NaN, Double.NaN));
        }
        double rate = (double) misclassified / bonafide;
        WilsonConfidenceInterval.Interval ci =
                WilsonConfidenceInterval.compute(misclassified, bonafide);
        return new RateResult(rate, bonafide, ci);
    }

    /**
     * Count of unique bona fide subjects — for Rule of 30 validation.
     */
    public int bonafideSubjectCount() {
        Set<String> subjects = new HashSet<>();
        for (ScoredSample s : samples) {
            if (s.isBonafide()) {
                subjects.add(s.subjectId());
            }
        }
        return subjects.size();
    }

    /**
     * Whether the Rule of 30 is satisfied (>=30 bona fide subjects).
     */
    public boolean ruleOf30Satisfied() {
        return bonafideSubjectCount() >= 30;
    }

    /** Total count of bona fide presentation samples. */
    public int bonafideCount() {
        int count = 0;
        for (ScoredSample s : samples) {
            if (s.isBonafide()) count++;
        }
        return count;
    }

    /** Total count of attack presentation samples across all species. */
    public int totalAttackCount() {
        int count = 0;
        for (ScoredSample s : samples) {
            if (s.isAttack()) count++;
        }
        return count;
    }

    // ── DET curve & D-EER ──────────────────────────────────────────────

    /**
     * DET curve: list of (threshold, pooledApcer, bpcer) triples
     * across the full threshold range 0-100.
     */
    public List<DetPoint> detCurve() {
        List<DetPoint> points = new ArrayList<>();
        for (int t = 0; t <= 100; t++) {
            double apcerT = pooledApcerAtThreshold(t);
            double bpcerT = bpcerAtThreshold(t);
            points.add(new DetPoint(t, apcerT, bpcerT));
        }
        return points;
    }

    /**
     * D-EER: Detection Equal Error Rate — operating point where pooled APCER ≈ BPCER.
     * Returns the threshold and the approximate equal error rate.
     */
    public DetPoint deer() {
        List<DetPoint> curve = detCurve();
        DetPoint best = curve.get(0);
        double bestDiff = Double.MAX_VALUE;
        for (DetPoint p : curve) {
            if (Double.isNaN(p.apcer()) || Double.isNaN(p.bpcer())) continue;
            double diff = Math.abs(p.apcer() - p.bpcer());
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return new DetPoint(best.threshold(), best.apcer(), best.bpcer(), "D-EER");
    }

    private double pooledApcerAtThreshold(int t) {
        int attacks = 0;
        int misclassified = 0;
        for (ScoredSample s : samples) {
            if (s.isAttack()) {
                attacks++;
                if (s.score() >= t) {
                    misclassified++;
                }
            }
        }
        return attacks == 0 ? Double.NaN : (double) misclassified / attacks;
    }

    private double bpcerAtThreshold(int t) {
        int bonafide = 0;
        int misclassified = 0;
        for (ScoredSample s : samples) {
            if (s.isBonafide()) {
                bonafide++;
                if (s.score() < t) {
                    misclassified++;
                }
            }
        }
        return bonafide == 0 ? Double.NaN : (double) misclassified / bonafide;
    }

    // ── Set of species ─────────────────────────────────────────────────

    /**
     * Returns all distinct PAI species in the attack samples.
     */
    public Set<String> species() {
        Set<String> result = new HashSet<>();
        for (ScoredSample s : samples) {
            if (s.isAttack() && s.species() != null && !s.species().isEmpty()) {
                result.add(s.species());
            }
        }
        return result;
    }

    public int threshold() { return threshold; }
    public int sampleCount() { return samples.size(); }

    // ── Result types ───────────────────────────────────────────────────

    /** Per-species APCER result. */
    public static final class SpeciesResult {
        private final String species;
        private final double rate;
        private final int count;
        private final WilsonConfidenceInterval.Interval ci;

        public SpeciesResult(String species, double rate, int count,
                             WilsonConfidenceInterval.Interval ci) {
            this.species = species;
            this.rate = rate;
            this.count = count;
            this.ci = ci;
        }

        public String species() { return species; }
        public double rate()    { return rate; }
        public int count()      { return count; }
        public WilsonConfidenceInterval.Interval ci() { return ci; }
    }

    /** General rate result with CI. */
    public static final class RateResult {
        private final double rate;
        private final int count;
        private final WilsonConfidenceInterval.Interval ci;

        public RateResult(double rate, int count, WilsonConfidenceInterval.Interval ci) {
            this.rate = rate;
            this.count = count;
            this.ci = ci;
        }

        public double rate() { return rate; }
        public int count()   { return count; }
        public WilsonConfidenceInterval.Interval ci() { return ci; }
    }

    /** DET curve point with operating point label. */
    public static final class DetPoint {
        private final int threshold;
        private final double apcer;
        private final double bpcer;
        private final String label;

        public DetPoint(int threshold, double apcer, double bpcer) {
            this(threshold, apcer, bpcer, "operating_point_t" + threshold);
        }

        public DetPoint(int threshold, double apcer, double bpcer, String label) {
            this.threshold = threshold;
            this.apcer = apcer;
            this.bpcer = bpcer;
            this.label = label != null ? label : ("operating_point_t" + threshold);
        }

        public int threshold() { return threshold; }
        public double apcer()  { return apcer; }
        public double bpcer()  { return bpcer; }
        public String label()  { return label; }
    }
}
