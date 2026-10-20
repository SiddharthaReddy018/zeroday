package liveness.evaluation;

/**
 * Injection Attack Detection metrics (§12.2, §24.2).
 * <p>
 * Reported in a <b>separate table</b> from PAD metrics — these are
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

    public IadMetrics() {
        // all zeros
    }

    // ── Recording ──────────────────────────────────────────────────────

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

    // ── Rates ──────────────────────────────────────────────────────────

    /** Freeze detection rate: detected / total freeze attempts. */
    public double freezeDetectionRate() {
        return freezeTotal == 0 ? 0.0 : (double) freezeDetected / freezeTotal;
    }

    /** Loop detection rate: detected / total loop replay attempts. */
    public double loopDetectionRate() {
        return loopTotal == 0 ? 0.0 : (double) loopDetected / loopTotal;
    }

    /** Nonce-swap rejection rate: rejected / total nonce swap attempts. */
    public double nonceSwapRejectionRate() {
        return nonceSwapTotal == 0 ? 0.0 : (double) nonceSwapRejected / nonceSwapTotal;
    }

    /** False IAD rate: false alarms on legitimate sessions / total legitimate. */
    public double falseIadRate() {
        return legitimateTotal == 0 ? 0.0 : (double) legitimateFalseAlarms / legitimateTotal;
    }

    // ── Report ─────────────────────────────────────────────────────────

    /**
     * Generates the IAD metrics table (separate from PAD per §12.2).
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Injection Attack Detection Metrics (CEN/TS 18099 concepts)\n\n");
        sb.append("| Attack Type | Attempts | Detected/Rejected | Rate |\n");
        sb.append("|---|---|---|---|\n");
        row(sb, "Freeze detection", freezeTotal, freezeDetected, freezeDetectionRate());
        row(sb, "Loop replay detection", loopTotal, loopDetected, loopDetectionRate());
        row(sb, "Nonce-swap rejection", nonceSwapTotal, nonceSwapRejected, nonceSwapRejectionRate());
        row(sb, "False IAD (legitimate)", legitimateTotal, legitimateFalseAlarms, falseIadRate());
        sb.append("\n> These are detection rates, NOT APCER values (§12.2).\n");
        return sb.toString();
    }

    /** R9: a row with no trials is "not measured", never a 0.0000 rate. */
    private static void row(StringBuilder sb, String name, int total, int hit, double rate) {
        if (total == 0) {
            sb.append(String.format("| %s | 0 | - | not measured (no trials in this run) |\n", name));
        } else {
            sb.append(String.format("| %s | %d | %d | %.4f |\n", name, total, hit, rate));
        }
    }

    // ── Accessors for testing ──────────────────────────────────────────

    public int freezeTotal()            { return freezeTotal; }
    public int freezeDetected()         { return freezeDetected; }
    public int loopTotal()              { return loopTotal; }
    public int loopDetected()           { return loopDetected; }
    public int nonceSwapTotal()         { return nonceSwapTotal; }
    public int nonceSwapRejected()      { return nonceSwapRejected; }
    public int legitimateTotal()        { return legitimateTotal; }
    public int legitimateFalseAlarms()  { return legitimateFalseAlarms; }
}
