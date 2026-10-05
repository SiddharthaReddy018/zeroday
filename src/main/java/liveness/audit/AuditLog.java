package liveness.audit;

/**
 * Convenience wrapper around {@link AuditChain} for session-scoped logging (§6.6).
 * <p>
 * Provides typed log methods for common events (state transitions, scores,
 * challenges, etc.). Delegates to the underlying chain.
 */
public final class AuditLog {

    private final AuditChain chain;

    public AuditLog() {
        this.chain = new AuditChain();
    }

    /** Access the underlying chain for verification. */
    public AuditChain chain() {
        return chain;
    }

    /** Log a state transition. */
    public void logTransition(String fromState, String toState, String event) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "STATE",
                "TRANSITION",
                "from=" + fromState + ",to=" + toState + ",event=" + event));
    }

    /** Log a PAD score. */
    public void logScore(String scorer, int score, double quality) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "PAD",
                "SCORE",
                "scorer=" + scorer + ",score=" + score + ",quality=" + quality));
    }

    /** Log a band decision. */
    public void logBand(String band, int compositeScore, int windowCount) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "PAD",
                "BAND",
                "band=" + band + ",composite=" + compositeScore + ",windows=" + windowCount));
    }

    /** Log a challenge issue. */
    public void logChallengeIssued(String challengeId, String action, int sequenceIndex) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "CHALLENGE",
                "ISSUED",
                "challengeId=" + challengeId + ",action=" + action + ",seq=" + sequenceIndex));
    }

    /** Log a challenge result. */
    public void logChallengeResult(String challengeId, boolean success, long latencyMs) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "CHALLENGE",
                "RESULT",
                "challengeId=" + challengeId + ",success=" + success + ",latencyMs=" + latencyMs));
    }

    /** Log an integrity event. */
    public void logIntegrity(String component, String detail) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "INTEGRITY",
                "CHECK",
                "component=" + component + ",detail=" + detail));
    }

    /** Log a retry attempt. */
    public void logRetry(int attempt, int maxRetries) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "FLOW",
                "RETRY",
                "attempt=" + attempt + ",max=" + maxRetries));
    }

    /** Log an arbitrary event. */
    public void log(String channel, String event, String fields) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(), channel, event, fields));
    }

    /** Seal the chain — no further entries allowed. */
    public void seal() {
        chain.seal();
    }
}
