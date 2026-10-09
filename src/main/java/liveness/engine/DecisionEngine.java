package liveness.engine;

import liveness.policy.LivenessPolicy;
import liveness.policy.LivenessPolicy.Band;

/**
 * Decision engine (§5.3-5.4).
 * <p>
 * Hard gates first, then weighted fusion via {@link LivenessPolicy} band routing.
 * Attack persistence: CLEAR_ATTACK only after N consecutive quality-OK windows
 * below threshold (§5.1).
 */
public final class DecisionEngine {

    private final LivenessPolicy policy;
    private final int attackPersistenceWindows;
    private int consecutiveAttackWindows = 0;

    /**
     * @param policy                   the 4-band score routing policy
     * @param attackPersistenceWindows  consecutive windows required for CLEAR_ATTACK (default 3)
     */
    public DecisionEngine(LivenessPolicy policy, int attackPersistenceWindows) {
        this.policy = policy;
        this.attackPersistenceWindows = attackPersistenceWindows;
    }

    /**
     * Evaluates a composite score through hard gates and band routing.
     * <p>
     * Hard gate order (§5.3):
     * 1. captureIntegrityFail → DEVICE_ERROR (handled by caller)
     * 2. qualityBelowMinimum → QUALITY_FAILURE (→ POSITIONING)
     * 3. hardPadFlag → CLEAR_ATTACK (immediate, bypasses persistence)
     * 4. Score-based routing via policy
     *
     * @param compositeScore        quality-weighted PAD composite (0-100)
     * @param captureIntegrityFail  true if IAD check failed
     * @param qualityBelowMinimum   true if quality gate failed
     * @param hardPadFlag           true if a hard PAD indicator fired (e.g. screen bezel)
     * @return decision result with band, score, and reason
     */
    public DecisionResult evaluate(int compositeScore,
                                   boolean captureIntegrityFail,
                                   boolean qualityBelowMinimum,
                                   boolean hardPadFlag) {
        // Hard gate 1: capture integrity
        if (captureIntegrityFail) {
            return new DecisionResult(Band.QUALITY_FAILURE, compositeScore,
                    "Capture integrity failed");
        }

        // Hard gate 2: quality below minimum
        if (qualityBelowMinimum) {
            return new DecisionResult(Band.QUALITY_FAILURE, compositeScore,
                    "Quality below minimum threshold");
        }

        // Hard gate 3: hard PAD flag (immediate — bypasses persistence)
        if (hardPadFlag) {
            consecutiveAttackWindows = attackPersistenceWindows; // force persisted
            return new DecisionResult(Band.CLEAR_ATTACK, compositeScore,
                    "Hard PAD flag raised");
        }

        // Score-based routing
        Band routedBand = policy.route(compositeScore);
        recordWindow(routedBand);

        if (routedBand == Band.CLEAR_ATTACK) {
            if (!isAttackPersisted()) {
                // Not yet persisted — report as UNCERTAIN to continue accumulating
                return new DecisionResult(Band.UNCERTAIN, compositeScore,
                        "Attack pending persistence (" + consecutiveAttackWindows
                                + "/" + attackPersistenceWindows + ")");
            }
        }

        return new DecisionResult(routedBand, compositeScore, "Score-based evaluation");
    }

    /**
     * Records a scoring window result for attack persistence tracking.
     * CLEAR_ATTACK increments counter; anything else resets it.
     */
    public void recordWindow(Band band) {
        if (band == Band.CLEAR_ATTACK) {
            consecutiveAttackWindows++;
        } else {
            consecutiveAttackWindows = 0;
        }
    }

    /**
     * True when N consecutive quality-OK windows have scored below attackThreshold.
     */
    public boolean isAttackPersisted() {
        return consecutiveAttackWindows >= attackPersistenceWindows;
    }

    /** Resets persistence counter (e.g., on retry). */
    public void resetPersistence() {
        consecutiveAttackWindows = 0;
    }

    /** Current consecutive attack window count (for audit). */
    public int consecutiveAttackWindows() {
        return consecutiveAttackWindows;
    }

    /**
     * Result of a decision evaluation.
     */
    public static final class DecisionResult {
        private final Band band;
        private final int score;
        private final String reason;

        public DecisionResult(Band band, int score, String reason) {
            this.band = band;
            this.score = score;
            this.reason = reason;
        }

        public Band band()     { return band; }
        public int score()     { return score; }
        public String reason() { return reason; }
    }
}
