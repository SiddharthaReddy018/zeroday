package liveness.policy;

/**
 * 4-band score routing (§5.1).
 * <p>
 * Two thresholds: {@code attackThreshold < passThreshold} (enforced by
 * {@link LivenessConfig}). A score 0–100 (higher = more live internally)
 * is routed to one of four bands.
 */
public final class LivenessPolicy {

    /** The four score bands from §5.1. */
    public enum Band {
        /** Quality gate failed — route to POSITIONING, not an attack. */
        QUALITY_FAILURE,

        /** Score < attackThreshold for N consecutive quality-OK windows. */
        CLEAR_ATTACK,

        /** attackThreshold <= score < passThreshold — needs active challenge. */
        UNCERTAIN,

        /** score >= passThreshold — proceed to FINAL_CAPTURE. */
        CLEAR_BONA_FIDE
    }

    private final int attackThreshold;
    private final int passThreshold;

    /**
     * @throws IllegalArgumentException if attackThreshold >= passThreshold
     */
    public LivenessPolicy(int attackThreshold, int passThreshold) {
        if (attackThreshold >= passThreshold) {
            throw new IllegalArgumentException(
                    "attackThreshold (" + attackThreshold
                            + ") must be < passThreshold (" + passThreshold + ")");
        }
        this.attackThreshold = attackThreshold;
        this.passThreshold = passThreshold;
    }

    /** Creates a policy from a {@link LivenessConfig}. */
    public LivenessPolicy(LivenessConfig config) {
        this(config.attackThreshold(), config.passThreshold());
    }

    /**
     * Routes a single score (0–100) to a band.
     * Quality failures are handled externally before calling this method.
     */
    public Band route(int score) {
        if (score < attackThreshold) {
            return Band.CLEAR_ATTACK;
        } else if (score < passThreshold) {
            return Band.UNCERTAIN;
        } else {
            return Band.CLEAR_BONA_FIDE;
        }
    }

    public int attackThreshold() { return attackThreshold; }
    public int passThreshold() { return passThreshold; }
}
