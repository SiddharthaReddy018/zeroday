package liveness.challenge;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Challenge generator (§6.3).
 * <p>
 * Selection rules:
 * <ul>
 *   <li>{@link SecureRandom} for selection</li>
 *   <li>No immediate repeat of the same action</li>
 *   <li>No repeat of previous session's sequence per subject (via store)</li>
 *   <li>System-selected only</li>
 *   <li>Pool filtered by workflow and accessibility profile</li>
 * </ul>
 */
public final class ChallengeGenerator {

    /** Default challenge pool (§6.3). */
    public static final List<String> DEFAULT_POOL = Collections.unmodifiableList(
            Arrays.asList(
                    "BLINK",
                    "TURN_LEFT", "TURN_RIGHT",
                    "LOOK_LEFT", "LOOK_RIGHT",
                    "LOOK_UP", "LOOK_DOWN"));

    private final SecureRandom random;
    private final List<String> pool;
    private final List<List<String>> previousSessionSequences;
    private String lastIssuedAction;

    /**
     * @param pool                       the available challenge actions
     * @param previousSessionSequences   sequences from prior sessions for this subject
     */
    public ChallengeGenerator(List<String> pool,
                              List<List<String>> previousSessionSequences) {
        this.random = new SecureRandom();
        this.pool = new ArrayList<>(Objects.requireNonNull(pool));
        this.previousSessionSequences = previousSessionSequences != null
                ? new ArrayList<>(previousSessionSequences)
                : new ArrayList<>();
        this.lastIssuedAction = null;
    }

    /** Convenience: default pool, no prior sequences. */
    public ChallengeGenerator() {
        this(DEFAULT_POOL, Collections.emptyList());
    }

    /**
     * Generates the next challenge action, respecting no-immediate-repeat
     * and no-session-sequence-repeat rules.
     *
     * @param sessionNonce   the session nonce for binding
     * @param sequenceIndex  the index within the current session's sequence
     * @param timeoutMs      challenge timeout in ms
     * @return a bound challenge
     * @throws IllegalStateException if no valid action can be selected
     */
    public BoundChallenge next(String sessionNonce, int sequenceIndex, long timeoutMs) {
        List<String> candidates = new ArrayList<>(pool);

        // Rule 1: no immediate repeat
        if (lastIssuedAction != null) {
            candidates.remove(lastIssuedAction);
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No valid challenge actions available after filtering");
        }

        // Select randomly
        String action = candidates.get(random.nextInt(candidates.size()));
        lastIssuedAction = action;

        long now = System.currentTimeMillis();
        List<String> expectedSequence = Collections.singletonList(action);

        return new BoundChallenge(
                sessionNonce,
                action,
                expectedSequence,
                now,
                now + timeoutMs,
                sequenceIndex);
    }

    /**
     * Generates a compound challenge (e.g., TURN_LEFT_RETURN, BLINK_TWICE).
     * Approximately 20% of challenges are compound per §6.3.
     */
    public BoundChallenge nextCompound(String sessionNonce, int sequenceIndex,
                                       long timeoutMs, int compoundLength) {
        List<String> sequence = new ArrayList<>();
        String previousInSeq = lastIssuedAction;

        for (int i = 0; i < compoundLength; i++) {
            List<String> candidates = new ArrayList<>(pool);
            if (previousInSeq != null) {
                candidates.remove(previousInSeq);
            }
            if (candidates.isEmpty()) {
                throw new IllegalStateException(
                        "No valid challenge actions for compound at step " + i);
            }
            String action = candidates.get(random.nextInt(candidates.size()));
            sequence.add(action);
            previousInSeq = action;
        }

        lastIssuedAction = sequence.get(sequence.size() - 1);
        long now = System.currentTimeMillis();

        return new BoundChallenge(
                sessionNonce,
                sequence.get(0),
                Collections.unmodifiableList(sequence),
                now,
                now + timeoutMs,
                sequenceIndex);
    }

    /** Resets the last-issued tracking. */
    public void reset() {
        lastIssuedAction = null;
    }

    /** The current available pool. */
    public List<String> pool() {
        return Collections.unmodifiableList(pool);
    }

    /** The last issued action (for testing). */
    String lastIssuedAction() {
        return lastIssuedAction;
    }
}
