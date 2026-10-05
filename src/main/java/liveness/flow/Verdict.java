package liveness.flow;

/**
 * The final verdict of a liveness session (§6.5).
 * Only {@link #PASS} produces an attestation.
 */
public enum Verdict {

    /** Liveness verified — attestation is produced. */
    PASS,

    /** Attack detected — session terminates. */
    ATTACK,

    /** Maximum retries exhausted — guide to recovery. */
    MAX_RETRIES,

    /** Device error — terminal for this session. */
    DEVICE_FAILURE,

    /** Session still in progress — no final verdict yet. */
    PENDING
}
