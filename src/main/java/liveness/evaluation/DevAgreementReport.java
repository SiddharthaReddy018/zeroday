package liveness.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Agreement of two PAD models on the SAME images of the DEV split (desktop-only evaluation tooling).
 * <p>
 * Purpose: decide whether an alternative model file (for example the dynamically quantized INT8 copy) behaves
 * like the evaluated and calibrated reference (FP32) at the frozen threshold, without touching the TEST split
 * (ADR-011: TEST is run once with frozen thresholds). Reported facts: score differences and decision flips at the
 * threshold, split by bona fide / attack. It deliberately does NOT claim an error rate: the DEV split has very few
 * attacks, so it cannot separate two models by APCER.
 * <p>
 * Hard guard: every pair must come from a manifest entry whose split is {@code dev}. A train or test entry is
 * rejected, so test labels can never enter this report.
 * <p>
 * Decision rule matches the harness: score &gt;= threshold means accepted as bona fide.
 */
public final class DevAgreementReport {

    /** Cap on flipped samples listed in the report; the counts are always complete. */
    static final int MAX_LISTED_FLIPS = 50;

    /** One DEV image scored by both models. */
    public static final class Pair {
        private final ManifestEntry entry;
        private final int scoreA;
        private final int scoreB;

        public Pair(ManifestEntry entry, int scoreA, int scoreB) {
            if (entry == null) {
                throw new IllegalArgumentException("entry must not be null");
            }
            if (!"dev".equals(entry.split())) {
                throw new IllegalArgumentException("only DEV split entries are allowed in an agreement report, got split '"
                        + entry.split() + "' for " + entry.path());
            }
            requireScore(scoreA, "scoreA");
            requireScore(scoreB, "scoreB");
            this.entry = entry;
            this.scoreA = scoreA;
            this.scoreB = scoreB;
        }

        public ManifestEntry entry() { return entry; }
        public int scoreA() { return scoreA; }
        public int scoreB() { return scoreB; }
    }

    private static void requireScore(int score, String name) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(name + " must be 0-100, got: " + score);
        }
    }

    /** Returns only the DEV entries of a manifest. Used before scoring so non-DEV images are never opened. */
    public static List<ManifestEntry> devOnly(List<ManifestEntry> entries) {
        List<ManifestEntry> dev = new ArrayList<>();
        for (ManifestEntry e : entries) {
            if ("dev".equals(e.split())) {
                dev.add(e);
            }
        }
        return dev;
    }

    private final String nameA;
    private final String nameB;
    private final int threshold;
    private final List<Pair> pairs;

    private final int bonaFide;
    private final int attacks;
    private final int identical;
    private final int maxAbsDiff;
    private final double meanAbsDiff;
    private final double meanDiff;
    private final double correlation;

    private final int bonaFideAcceptToReject;
    private final int bonaFideRejectToAccept;
    private final int attackAcceptToReject;
    private final int attackRejectToAccept;

    private final int bonaFideRejectedA;
    private final int bonaFideRejectedB;
    private final int attackAcceptedA;
    private final int attackAcceptedB;

    private DevAgreementReport(String nameA, String nameB, int threshold, List<Pair> pairs) {
        this.nameA = nameA;
        this.nameB = nameB;
        this.threshold = threshold;
        this.pairs = pairs;

        int bf = 0, atk = 0, same = 0, maxAbs = 0;
        long sumAbs = 0, sumDiff = 0;
        int bfAR = 0, bfRA = 0, atkAR = 0, atkRA = 0;
        int bfRejA = 0, bfRejB = 0, atkAccA = 0, atkAccB = 0;
        double sumA = 0, sumB = 0;
        for (Pair p : pairs) {
            int d = p.scoreB - p.scoreA;
            if (d == 0) same++;
            maxAbs = Math.max(maxAbs, Math.abs(d));
            sumAbs += Math.abs(d);
            sumDiff += d;
            sumA += p.scoreA;
            sumB += p.scoreB;
            boolean accA = p.scoreA >= threshold;
            boolean accB = p.scoreB >= threshold;
            if (p.entry.isBonafide()) {
                bf++;
                if (!accA) bfRejA++;
                if (!accB) bfRejB++;
                if (accA && !accB) bfAR++;
                if (!accA && accB) bfRA++;
            } else {
                atk++;
                if (accA) atkAccA++;
                if (accB) atkAccB++;
                if (accA && !accB) atkAR++;
                if (!accA && accB) atkRA++;
            }
        }
        int n = pairs.size();
        double meanA = sumA / n, meanB = sumB / n;
        double cov = 0, varA = 0, varB = 0;
        for (Pair p : pairs) {
            double da = p.scoreA - meanA, db = p.scoreB - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        this.bonaFide = bf;
        this.attacks = atk;
        this.identical = same;
        this.maxAbsDiff = maxAbs;
        this.meanAbsDiff = (double) sumAbs / n;
        this.meanDiff = (double) sumDiff / n;
        this.correlation = (varA == 0 || varB == 0) ? Double.NaN : cov / Math.sqrt(varA * varB);
        this.bonaFideAcceptToReject = bfAR;
        this.bonaFideRejectToAccept = bfRA;
        this.attackAcceptToReject = atkAR;
        this.attackRejectToAccept = atkRA;
        this.bonaFideRejectedA = bfRejA;
        this.bonaFideRejectedB = bfRejB;
        this.attackAcceptedA = atkAccA;
        this.attackAcceptedB = atkAccB;
    }

    /**
     * @param nameA     reference model label (for example "FP32")
     * @param nameB     alternative model label (for example "INT8")
     * @param pairs     DEV samples scored by both models
     * @param threshold decision threshold, 0-100 (score &gt;= threshold is accepted)
     */
    public static DevAgreementReport compute(String nameA, String nameB, List<Pair> pairs, int threshold) {
        if (nameA == null || nameA.trim().isEmpty() || nameB == null || nameB.trim().isEmpty()) {
            throw new IllegalArgumentException("model names must not be blank");
        }
        if (pairs == null || pairs.isEmpty()) {
            throw new IllegalArgumentException("no DEV pairs to compare");
        }
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("threshold must be 0-100, got: " + threshold);
        }
        for (Pair p : pairs) {
            if (p == null) {
                throw new IllegalArgumentException("pairs must not contain null");
            }
        }
        return new DevAgreementReport(nameA, nameB, threshold, new ArrayList<>(pairs));
    }

    public int total()                   { return pairs.size(); }
    public int bonaFide()                { return bonaFide; }
    public int attacks()                 { return attacks; }
    public int identicalScores()         { return identical; }
    public int maxAbsDiff()              { return maxAbsDiff; }
    public double meanAbsDiff()          { return meanAbsDiff; }
    /** Mean of (B - A). */
    public double meanSignedDiff()       { return meanDiff; }
    /** Pearson correlation of the two score vectors; NaN if either has zero variance. */
    public double correlation()          { return correlation; }
    public int bonaFideAcceptToReject()  { return bonaFideAcceptToReject; }
    public int bonaFideRejectToAccept()  { return bonaFideRejectToAccept; }
    public int attackAcceptToReject()    { return attackAcceptToReject; }
    public int attackRejectToAccept()    { return attackRejectToAccept; }
    /** Total decision flips at the threshold, either direction, either label. */
    public int totalFlips() {
        return bonaFideAcceptToReject + bonaFideRejectToAccept + attackAcceptToReject + attackRejectToAccept;
    }

    /** Markdown report. All numbers are computed from the pairs; nothing is typed in. */
    public String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# DEV-split agreement: ").append(nameA).append(" vs ").append(nameB).append("\n\n");
        sb.append("Split: dev only. Threshold: ").append(threshold)
                .append(" (score >= threshold is accepted as bona fide). A = ").append(nameA)
                .append(" (reference), B = ").append(nameB).append(".\n\n");

        sb.append("## Samples\n\n");
        sb.append("- Total: ").append(total()).append(" (bona fide ").append(bonaFide)
                .append(", attack ").append(attacks).append(")\n\n");

        sb.append("## Score differences (B - A, integer scores 0-100)\n\n");
        sb.append("- Identical scores: ").append(identical).append(" of ").append(total()).append("\n");
        sb.append("- Mean absolute difference: ").append(fmt(meanAbsDiff)).append("\n");
        sb.append("- Mean signed difference: ").append(fmt(meanDiff)).append("\n");
        sb.append("- Max absolute difference: ").append(maxAbsDiff).append("\n");
        sb.append("- Pearson correlation: ").append(Double.isNaN(correlation) ? "n/a (zero variance)" : fmt(correlation)).append("\n\n");

        sb.append("## Decision flips at threshold ").append(threshold).append("\n\n");
        sb.append("- Total flips: ").append(totalFlips()).append(" of ").append(total()).append("\n");
        sb.append("- Bona fide: ").append(nameA).append(" accepts and ").append(nameB).append(" rejects: ")
                .append(bonaFideAcceptToReject).append(" (adds false rejects)\n");
        sb.append("- Bona fide: ").append(nameA).append(" rejects and ").append(nameB).append(" accepts: ")
                .append(bonaFideRejectToAccept).append("\n");
        sb.append("- Attack: ").append(nameA).append(" rejects and ").append(nameB).append(" accepts: ")
                .append(attackRejectToAccept).append(" (adds false accepts)\n");
        sb.append("- Attack: ").append(nameA).append(" accepts and ").append(nameB).append(" rejects: ")
                .append(attackAcceptToReject).append("\n\n");

        sb.append("## Raw counts at the threshold (descriptive only)\n\n");
        sb.append("| Model | Bona fide rejected | Attacks accepted |\n|---|---|---|\n");
        sb.append("| ").append(nameA).append(" | ").append(bonaFideRejectedA).append(" of ").append(bonaFide)
                .append(" | ").append(attackAcceptedA).append(" of ").append(attacks).append(" |\n");
        sb.append("| ").append(nameB).append(" | ").append(bonaFideRejectedB).append(" of ").append(bonaFide)
                .append(" | ").append(attackAcceptedB).append(" of ").append(attacks).append(" |\n\n");

        sb.append("## Flipped samples\n\n");
        int listed = 0;
        int total = totalFlips();
        if (total == 0) {
            sb.append("None.\n\n");
        } else {
            sb.append("| path | label | species | A | B |\n|---|---|---|---|---|\n");
            for (Pair p : pairs) {
                boolean accA = p.scoreA >= threshold;
                boolean accB = p.scoreB >= threshold;
                if (accA != accB && listed < MAX_LISTED_FLIPS) {
                    sb.append("| ").append(p.entry.path()).append(" | ").append(p.entry.label())
                            .append(" | ").append(p.entry.species() == null ? "" : p.entry.species())
                            .append(" | ").append(p.scoreA).append(" | ").append(p.scoreB).append(" |\n");
                    listed++;
                }
            }
            if (total > listed) {
                sb.append("\nListing capped at ").append(MAX_LISTED_FLIPS).append(" of ").append(total).append(" flips.\n");
            }
            sb.append("\n");
        }

        sb.append("## How to read this\n\n");
        sb.append("- Zero flips means the two models made the same decision on every DEV image at this threshold. "
                + "It does not prove equal accuracy: the DEV split has few attacks.\n");
        sb.append("- Any flip means ").append(nameB).append(" is a different classifier at threshold ").append(threshold)
                .append(" and would need its own calibration and decision record.\n");
        sb.append("- Scores are rounded to integers by the production scorer, so small differences below 1 point are not visible.\n");
        sb.append("- Bona fide images are still images; this is not a live-presentation measurement and says nothing about speed.\n");
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
