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
        sb.append(String.format("| Freeze detection | %d | %d | %.4f |\n",
                freezeTotal, freezeDetected, freezeDetectionRate()));
        sb.append(String.format("| Loop replay detection | %d | %d | %.4f |\n",
                loopTotal, loopDetected, loopDetectionRate()));
        sb.append(String.format("| Nonce-swap rejection | %d | %d | %.4f |\n",
                nonceSwapTotal, nonceSwapRejected, nonceSwapRejectionRate()));
        sb.append(String.format("| False IAD (legitimate) | %d | %d | %.4f |\n",
                legitimateTotal, legitimateFalseAlarms, falseIadRate()));
        sb.append("\n> These are detection rates, NOT APCER values (§12.2).\n");
        return sb.toString();
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
