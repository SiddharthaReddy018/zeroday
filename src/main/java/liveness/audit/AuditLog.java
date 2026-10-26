package liveness.audit;

/**
 * Convenience wrapper around {@link AuditChain} for session-scoped logging (§6.6).
 * <p>
 * Provides typed log methods for common events (state transitions, scores,
 * challenges, etc.). Delegates to the underlying chain.
 * <p>
 * No biometric data in logs — scores and event codes only.
 */
public final class AuditLog {

    private final AuditChain chain;
    private final String sessionId;

    public AuditLog(AuditChain chain, String sessionId) {
        this.chain = chain;
        this.sessionId = sessionId;
    }

    /** No-arg convenience constructor for backward compatibility. */
    public AuditLog() {
        this(new AuditChain(), "default");
    }

    /** Access the underlying chain for verification. */
    public AuditChain chain() {
        return chain;
    }

    /** Session ID this log belongs to. */
    public String sessionId() {
        return sessionId;
    }

    /** Log a state transition. Accepts enum states for type safety. */
    public void logTransition(Object fromState, Object toState, String event) {
        System.err.println("TRANSITION: from=" + fromState + " to=" + toState + " event=" + event);
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "STATE",
                "TRANSITION",
                "session=" + sessionId + ",from=" + fromState
                        + ",to=" + toState + ",event=" + event));
    }

    /** Log a PAD score with a detail string. */
    public void logScore(String scorer, int score, String detail) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "PAD",
                "SCORE",
                "scorer=" + scorer + ",score=" + score + ",detail=" + detail));
    }

    /** Log a band decision with reason. */
    public void logBand(String band, int compositeScore, String reason) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "PAD",
                "BAND",
                "band=" + band + ",composite=" + compositeScore + ",reason=" + reason));
    }

    /** Log a challenge issue. */
    public void logChallengeIssued(String action, int sequenceIndex) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "CHALLENGE",
                "ISSUED",
                "session=" + sessionId + ",action=" + action + ",seq=" + sequenceIndex));
    }

    /** Log a challenge result. */
    public void logChallengeResult(String action, boolean success, String detail) {
        chain.append(new AuditEvent(
                System.currentTimeMillis(),
                "CHALLENGE",
                "RESULT",
                "action=" + action + ",success=" + success + ",detail=" + detail));
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

    /** Exports the sealed audit trail. */
    public String exportTrail() {
        return chain.exportTrail();
    }
}
