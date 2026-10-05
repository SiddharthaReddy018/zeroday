package liveness.challenge;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A single bound challenge (§6.3).
 * <p>
 * Fields: challengeId, sessionNonce, action, expectedSequence,
 * issuedAtMs, expiresAtMs, sequenceIndex.
 */
public final class BoundChallenge {

    private final String challengeId;
    private final String sessionNonce;
    private final String action;
    private final List<String> expectedSequence;
    private final long issuedAtMs;
    private final long expiresAtMs;
    private final int sequenceIndex;

    public BoundChallenge(String sessionNonce, String action,
                          List<String> expectedSequence,
                          long issuedAtMs, long expiresAtMs,
                          int sequenceIndex) {
        this.challengeId = UUID.randomUUID().toString();
        this.sessionNonce = Objects.requireNonNull(sessionNonce, "sessionNonce");
        this.action = Objects.requireNonNull(action, "action");
        this.expectedSequence = Collections.unmodifiableList(expectedSequence);
        this.issuedAtMs = issuedAtMs;
        this.expiresAtMs = expiresAtMs;
        this.sequenceIndex = sequenceIndex;
    }

    public String challengeId()           { return challengeId; }
    public String sessionNonce()          { return sessionNonce; }
    public String action()                { return action; }
    public List<String> expectedSequence() { return expectedSequence; }
    public long issuedAtMs()              { return issuedAtMs; }
    public long expiresAtMs()             { return expiresAtMs; }
    public int sequenceIndex()            { return sequenceIndex; }

    /** True if the challenge has expired relative to {@code nowMs}. */
    public boolean isExpired(long nowMs) {
        return nowMs > expiresAtMs;
    }

    @Override
    public String toString() {
        return "BoundChallenge{id=" + challengeId
                + ", action=" + action
                + ", seq=" + sequenceIndex
                + ", expires=" + expiresAtMs + "}";
    }
}
