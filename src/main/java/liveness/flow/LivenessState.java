package liveness.flow;

/**
 * The 13 states of the liveness state machine (§4, 16.2).
 * Terminal states end the session; recovery (if allowed) starts a new session
 * through {@link RetryRateLimiter}.
 */
public enum LivenessState {

    /** Session not yet started. */
    IDLE(false),

    /** Device discovery / readiness check (MOSIPDINFO / MOSIPDISC). */
    DEVICE_CHECK(false),

    /** Operator/subject positioning the face in the oval guide. */
    POSITIONING(false),

    /** Passive PAD scoring is running on incoming frames. */
    PASSIVE(false),

    /** An active challenge has been selected and shown to the subject. */
    CHALLENGE_PROMPT(false),

    /** Waiting for the subject to perform the prompted challenge action. */
    CHALLENGE_WAIT(false),

    /** RCAPTURE has been triggered; waiting for device response. */
    FINAL_CAPTURE(false),

    /** Verifying same-face, final PAD, JWT/nonce/freshness on the RCAPTURE result. */
    VERIFYING_FINAL(false),

    /** Liveness verified — attestation produced. Terminal. */
    SUCCESS(true),

    /** Retry is available; subject may try again (budget permitting). */
    RETRY_AVAILABLE(false),

    /** PAD detected a presentation attack — terminal for this session. */
    BLOCKED_ATTACK(true),

    /** Retry budget exhausted — terminal; guide to recovery. */
    FAILED_MAX_RETRIES(true),

    /** Device/camera error — terminal for this session; recovery via new session. */
    DEVICE_ERROR(true);

    private final boolean terminal;

    LivenessState(boolean terminal) {
        this.terminal = terminal;
    }

    /** True if no further transitions are possible from this state within the session. */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * The set of non-terminal "live" states from which mid-stream events
     * (device disconnect, IAD failure) can trigger transitions.
     * Used by rows 24 and 25 of the 16.2 table.
     */
    public boolean isLiveNonTerminal() {
        return this == POSITIONING || this == PASSIVE
                || this == CHALLENGE_PROMPT || this == CHALLENGE_WAIT;
    }
}
