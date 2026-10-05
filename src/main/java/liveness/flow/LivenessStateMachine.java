package liveness.flow;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Table-driven state machine implementing the complete transition table
 * from HANDOFF-2 Section 16.2 (R2).
 * <p>
 * Every valid (state, event) pair is pre-loaded in the transition map.
 * Any pair not in the table throws {@link IllegalStateException}.
 * No boolean flags — all routing derives from {@code current()}.
 */
public final class LivenessStateMachine {

    /**
     * Key for the transition table: (fromState, event).
     */
    private static final class TransitionKey {
        final LivenessState from;
        final LivenessEvent event;

        TransitionKey(LivenessState from, LivenessEvent event) {
            this.from = from;
            this.event = event;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TransitionKey)) return false;
            TransitionKey k = (TransitionKey) o;
            return from == k.from && event == k.event;
        }

        @Override
        public int hashCode() {
            return from.hashCode() * 31 + event.hashCode();
        }
    }

    /**
     * The full transition table (immutable after class loading).
     * Built exactly from HANDOFF-2 16.2 rows 1-25.
     */
    private static final Map<TransitionKey, LivenessState> TABLE;

    static {
        Map<TransitionKey, LivenessState> t = new java.util.HashMap<>();

        // Row 1:  IDLE -> DEVICE_CHECK  (start capture)
        t.put(new TransitionKey(LivenessState.IDLE, LivenessEvent.START_CAPTURE),
                LivenessState.DEVICE_CHECK);

        // Row 2:  DEVICE_CHECK -> POSITIONING  (device ready)
        t.put(new TransitionKey(LivenessState.DEVICE_CHECK, LivenessEvent.DEVICE_READY),
                LivenessState.POSITIONING);

        // Row 3:  DEVICE_CHECK -> DEVICE_ERROR  (device/camera unavailable)
        t.put(new TransitionKey(LivenessState.DEVICE_CHECK, LivenessEvent.DEVICE_UNAVAILABLE),
                LivenessState.DEVICE_ERROR);

        // Row 4:  POSITIONING -> PASSIVE  (enough qualifying frames)
        t.put(new TransitionKey(LivenessState.POSITIONING, LivenessEvent.QUALITY_OK),
                LivenessState.PASSIVE);

        // Row 5:  POSITIONING -> RETRY_AVAILABLE  (face not detected / positioning timeout)
        t.put(new TransitionKey(LivenessState.POSITIONING, LivenessEvent.POSITIONING_TIMEOUT),
                LivenessState.RETRY_AVAILABLE);

        // Row 6:  PASSIVE -> FINAL_CAPTURE  (CLEAR_BONA_FIDE)
        t.put(new TransitionKey(LivenessState.PASSIVE, LivenessEvent.CLEAR_BONA_FIDE),
                LivenessState.FINAL_CAPTURE);

        // Row 7:  PASSIVE -> CHALLENGE_PROMPT  (UNCERTAIN)
        t.put(new TransitionKey(LivenessState.PASSIVE, LivenessEvent.UNCERTAIN),
                LivenessState.CHALLENGE_PROMPT);

        // Row 8:  PASSIVE -> BLOCKED_ATTACK  (CLEAR_ATTACK persisted N windows)
        t.put(new TransitionKey(LivenessState.PASSIVE, LivenessEvent.CLEAR_ATTACK),
                LivenessState.BLOCKED_ATTACK);

        // Row 9:  PASSIVE -> POSITIONING  (quality drops below minimum)
        t.put(new TransitionKey(LivenessState.PASSIVE, LivenessEvent.QUALITY_DROPPED),
                LivenessState.POSITIONING);

        // Row 10: PASSIVE -> RETRY_AVAILABLE  (liveness engine error)
        t.put(new TransitionKey(LivenessState.PASSIVE, LivenessEvent.ENGINE_ERROR),
                LivenessState.RETRY_AVAILABLE);

        // Row 11: CHALLENGE_PROMPT -> CHALLENGE_WAIT  (prompt issued)
        t.put(new TransitionKey(LivenessState.CHALLENGE_PROMPT, LivenessEvent.PROMPT_ISSUED),
                LivenessState.CHALLENGE_WAIT);

        // Row 12: CHALLENGE_WAIT -> CHALLENGE_PROMPT  (action confirmed, more challenges)
        t.put(new TransitionKey(LivenessState.CHALLENGE_WAIT, LivenessEvent.ACTION_CONFIRMED_MORE),
                LivenessState.CHALLENGE_PROMPT);

        // Row 13: CHALLENGE_WAIT -> FINAL_CAPTURE  (action confirmed, minimum met)
        t.put(new TransitionKey(LivenessState.CHALLENGE_WAIT, LivenessEvent.ACTION_CONFIRMED_DONE),
                LivenessState.FINAL_CAPTURE);

        // Row 14: CHALLENGE_WAIT -> RETRY_AVAILABLE  (timeout or wrong/failed action)
        t.put(new TransitionKey(LivenessState.CHALLENGE_WAIT, LivenessEvent.CHALLENGE_TIMEOUT),
                LivenessState.RETRY_AVAILABLE);

        // Row 15: CHALLENGE_WAIT -> BLOCKED_ATTACK  (PAD hard flag during challenge)
        t.put(new TransitionKey(LivenessState.CHALLENGE_WAIT, LivenessEvent.CHALLENGE_PAD_FLAG),
                LivenessState.BLOCKED_ATTACK);

        // Row 16: FINAL_CAPTURE -> VERIFYING_FINAL  (RCAPTURE returned)
        t.put(new TransitionKey(LivenessState.FINAL_CAPTURE, LivenessEvent.RCAPTURE_RETURNED),
                LivenessState.VERIFYING_FINAL);

        // Row 17: FINAL_CAPTURE -> DEVICE_ERROR  (RCAPTURE failure/timeout)
        t.put(new TransitionKey(LivenessState.FINAL_CAPTURE, LivenessEvent.RCAPTURE_FAILURE),
                LivenessState.DEVICE_ERROR);

        // Row 18: VERIFYING_FINAL -> SUCCESS  (all commit checks pass)
        t.put(new TransitionKey(LivenessState.VERIFYING_FINAL, LivenessEvent.COMMIT_CHECKS_PASS),
                LivenessState.SUCCESS);

        // Row 19: VERIFYING_FINAL -> RETRY_AVAILABLE  (same-face mismatch / final quality fail)
        t.put(new TransitionKey(LivenessState.VERIFYING_FINAL, LivenessEvent.SAME_FACE_FAIL),
                LivenessState.RETRY_AVAILABLE);

        // Row 20: VERIFYING_FINAL -> BLOCKED_ATTACK  (final PAD check CLEAR_ATTACK)
        t.put(new TransitionKey(LivenessState.VERIFYING_FINAL, LivenessEvent.FINAL_PAD_ATTACK),
                LivenessState.BLOCKED_ATTACK);

        // Row 21: VERIFYING_FINAL -> DEVICE_ERROR  (JWT/nonce/freshness binding fails)
        t.put(new TransitionKey(LivenessState.VERIFYING_FINAL, LivenessEvent.BINDING_FAILURE),
                LivenessState.DEVICE_ERROR);

        // Row 22: RETRY_AVAILABLE -> PASSIVE  (retry granted by RetryRateLimiter)
        t.put(new TransitionKey(LivenessState.RETRY_AVAILABLE, LivenessEvent.RETRY_GRANTED),
                LivenessState.PASSIVE);

        // Row 23: RETRY_AVAILABLE -> FAILED_MAX_RETRIES  (budget exhausted)
        t.put(new TransitionKey(LivenessState.RETRY_AVAILABLE, LivenessEvent.BUDGET_EXHAUSTED),
                LivenessState.FAILED_MAX_RETRIES);

        // Rows 24-25: any live non-terminal state -> DEVICE_ERROR
        // Row 24: DEVICE_DISCONNECT (reason DEVICE_UNAVAILABLE)
        // Row 25: IAD_FAILURE (reason INTEGRITY_FAILURE)
        for (LivenessState s : LivenessState.values()) {
            if (s.isLiveNonTerminal()) {
                t.put(new TransitionKey(s, LivenessEvent.DEVICE_DISCONNECT),
                        LivenessState.DEVICE_ERROR);
                t.put(new TransitionKey(s, LivenessEvent.IAD_FAILURE),
                        LivenessState.DEVICE_ERROR);
            }
        }

        TABLE = Collections.unmodifiableMap(t);
    }

    private LivenessState current;

    public LivenessStateMachine() {
        this.current = LivenessState.IDLE;
    }

    /** Current state. */
    public LivenessState current() {
        return current;
    }

    /**
     * Attempt a transition. Returns the new state if the transition is valid.
     *
     * @throws IllegalStateException if the (current, event) pair is not in the table
     */
    public LivenessState fire(LivenessEvent event) {
        TransitionKey key = new TransitionKey(current, event);
        LivenessState next = TABLE.get(key);
        if (next == null) {
            throw new IllegalStateException(
                    "Invalid transition: " + current + " + " + event);
        }
        current = next;
        return current;
    }

    /**
     * Returns the target state for a given (from, event) pair without
     * changing the machine's state. Returns {@code null} if the transition
     * is not in the table.
     */
    public static LivenessState peek(LivenessState from, LivenessEvent event) {
        return TABLE.get(new TransitionKey(from, event));
    }

    /**
     * Exposes the full transition table for test generation (StateMachineMatrixTest).
     * Keys are encoded as "FROM:EVENT" strings.
     */
    public static Map<String, LivenessState> transitionTable() {
        Map<String, LivenessState> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<TransitionKey, LivenessState> e : TABLE.entrySet()) {
            result.put(e.getKey().from + ":" + e.getKey().event, e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
}
