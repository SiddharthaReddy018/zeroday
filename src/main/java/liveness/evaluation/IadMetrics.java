package liveness.evaluation;

import java.util.Locale;

/**
 * Injection Attack Detection metrics (Section 12.2, Section 24.2).
 * <p>
 * Reported in a <b>separate table</b> from PAD metrics -- these are
 * detection rates, NOT APCER values.
 */
public final class IadMetrics {

    private int freezeTotal;
    private int freezeDetected;
    private int loopTotal;
    private int loopDetected;
    private int nonceSwapTotal;
    private int nonceSwapRejected;
    private int legitimateTotal;
    private int legitimateFalseAlarms;
    private long totalDetectionLatencyNs;
    private int latencyMeasurements;

    public IadMetrics() {
        // all zeros
    }

    // -- Recording ------------------------------------------------------

    public void recordFreezeAttempt(boolean detected) {
        freezeTotal++;
        if (detected) freezeDetected++;
    }

    public void recordLoopAttempt(boolean detected) {
        loopTotal++;
        if (detected) loopDetected++;
    }

    public void recordNonceSwapAttempt(boolean rejected) {
        nonceSwapTotal++;
        if (rejected) nonceSwapRejected++;
    }

    public void recordLegitimateSession(boolean falseAlarm) {
        legitimateTotal++;
        if (falseAlarm) legitimateFalseAlarms++;
    }

    /** Operating point descriptor. */
    public String operatingPoint() {
        return "CEN_TS_18099_BASELINE";
    }

    /**
     * Executes a synthetic mechanism runner simulating IAD trials.
     * Useful for automated test environments where physical hardware injection is not present.
     */
    public void runSyntheticEvaluation(int freezeTrials, double freezeDetectionProb,
                                       int loopTrials, double loopDetectionProb,
                                       int nonceTrials, double nonceRejectionProb,
                                       int legitTrials, double falseAlarmProb,
                                       java.util.Random rng) {
        for (int i = 0; i < freezeTrials; i++) {
            recordFreezeAttempt(rng.nextDouble() < freezeDetectionProb);
        }
        for (int i = 0; i < loopTrials; i++) {
            recordLoopAttempt(rng.nextDouble() < loopDetectionProb);
        }
        for (int i = 0; i < nonceTrials; i++) {
            recordNonceSwapAttempt(rng.nextDouble() < nonceRejectionProb);
        }
        for (int i = 0; i < legitTrials; i++) {
            recordLegitimateSession(rng.nextDouble() < falseAlarmProb);
        }
    }

    // -- Rates ----------------------------------------------------------

    /** Freeze detection rate: detected / total freeze attempts. */
    public double freezeDetectionRate() {
        return freezeTotal == 0 ? Double.NaN : (double) freezeDetected / freezeTotal;
    }

    /** Loop detection rate: detected / total loop replay attempts. */
    public double loopDetectionRate() {
        return loopTotal == 0 ? Double.NaN : (double) loopDetected / loopTotal;
    }

    /** Nonce-swap rejection rate: rejected / total nonce swap attempts. */
    public double nonceSwapRejectionRate() {
        return nonceSwapTotal == 0 ? Double.NaN : (double) nonceSwapRejected / nonceSwapTotal;
    }

    /** False IAD rate: false alarms on legitimate sessions / total legitimate. */
    public double falseIadRate() {
        return legitimateTotal == 0 ? Double.NaN : (double) legitimateFalseAlarms / legitimateTotal;
    }

    public void recordDetectionLatencyNs(long ns) {
        if (ns > 0) {
            totalDetectionLatencyNs += ns;
            latencyMeasurements++;
        }
    }

    public double averageDetectionLatencyMs() {
        return latencyMeasurements == 0 ? Double.NaN : (double) totalDetectionLatencyNs / (latencyMeasurements * 1_000_000.0);
    }

    /**
     * Mechanism coverage: proportion of tracked IAD threat mechanisms (freeze, loop replay, nonce-swap)
     * evaluated with at least one presentation.
     */
    public double mechanismCoverage() {
        int mechanisms = 3;
        int active = (freezeTotal > 0 ? 1 : 0) + (loopTotal > 0 ? 1 : 0) + (nonceSwapTotal > 0 ? 1 : 0);
        return (double) active / mechanisms;
    }

    /**
     * Overall IAD bypass rate: proportion of all attack presentations that were not detected/rejected.
     */
    public double bypassRate() {
        int attackTotal = freezeTotal + loopTotal + nonceSwapTotal;
        if (attackTotal == 0) return Double.NaN;
        int bypassed = (freezeTotal - freezeDetected) + (loopTotal - loopDetected) + (nonceSwapTotal - nonceSwapRejected);
        return (double) bypassed / attackTotal;
    }

    // -- Report ---------------------------------------------------------

    /**
     * Generates the IAD metrics table (separate from PAD per Section 12.2).
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Injection Attack Detection Metrics (CEN/TS 18099 concepts)\n\n");
        sb.append("> Results are synthetic, mechanism-level, not real-camera.\n\n");
        sb.append("| Attack Type | Attempts | Detected/Rejected | Rate | 95% Wilson CI |\n");
        sb.append("|---|---|---|---|---|");
        sb.append("\n");
        row(sb, "Freeze detection", freezeTotal, freezeDetected, freezeDetectionRate());
        row(sb, "Loop replay detection", loopTotal, loopDetected, loopDetectionRate());
        row(sb, "Nonce-swap rejection", nonceSwapTotal, nonceSwapRejected, nonceSwapRejectionRate());
        row(sb, "False IAD (legitimate)", legitimateTotal, legitimateFalseAlarms, falseIadRate());
        sb.append("\n> These are detection rates, NOT APCER values (Section 12.2).\n");
        sb.append("> Known limits of the loop rule (synthetic probes): short or low-motion loops (about 3 px/frame or less) are mostly missed;\n");
        sb.append("> periodic or large legitimate motion can look like a loop. Loop detection is a soft signal until real-camera tuning (Part 2 X13).\n");
        if (freezeTotal > 0 || loopTotal > 0 || nonceSwapTotal > 0) {
            sb.append("\n");
            sb.append(String.format(Locale.ROOT, "- **Overall IAD bypass rate:** %.4f\n", bypassRate()));
            if (!Double.isNaN(averageDetectionLatencyMs())) {
                sb.append(String.format(Locale.ROOT, "- **Average detection latency:** %.2f ms\n", averageDetectionLatencyMs()));
            }
        }
        return sb.toString();
    }

    /** R9: a row with no trials is "not measured", never a 0.0000 rate. */
    private static void row(StringBuilder sb, String name, int total, int hit, double rate) {
        if (total == 0) {
            sb.append(String.format(Locale.ROOT, "| %s | 0 | - | not measured (no trials in this run) | - |\n", name));
        } else {
            WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(hit, total);
            sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %.4f | %s |\n", name, total, hit, rate, ci));
        }
    }

    // -- Accessors for testing ------------------------------------------

    public int freezeTotal()            { return freezeTotal; }
    public int freezeDetected()         { return freezeDetected; }
    public int loopTotal()              { return loopTotal; }
    public int loopDetected()           { return loopDetected; }
    public int nonceSwapTotal()         { return nonceSwapTotal; }
    public int nonceSwapRejected()      { return nonceSwapRejected; }
    public int legitimateTotal()        { return legitimateTotal; }
    public int legitimateFalseAlarms()  { return legitimateFalseAlarms; }
}
