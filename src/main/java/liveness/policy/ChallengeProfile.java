package liveness.policy;

/**
 * Challenge profile abstraction (R5, HANDOFF-2 15.2).
 * <p>
 * A profile may relax time, pool, and retries — but NEVER
 * security thresholds (attackThreshold, passThreshold,
 * persistence windows, capture-integrity rules, same-face thresholds).
 * <p>
 * Profiles are selected by the operator or MOSIP config,
 * never inferred from the face (R4).
 */
public enum ChallengeProfile {

    /** Standard challenge parameters. */
    DEFAULT(5000, 3, 3, true),

    /** Relaxed timeouts and retries for accessibility needs. */
    ACCESSIBILITY(10000, 5, 3, false),

    /** Relaxed for low-quality capture environments. */
    LOW_QUALITY(8000, 4, 3, true),

    /** Passive-only + operator attestation for infants (§14 item 9). */
    INFANT(15000, 5, 2, false),

    /** Per-workflow custom settings (validated same as others). */
    WORKFLOW_SPECIFIC(5000, 3, 3, true);

    private final long challengeTimeoutMs;
    private final int maxRetries;
    private final int maxChallenges;
    private final boolean allowCompound;

    ChallengeProfile(long challengeTimeoutMs, int maxRetries,
                     int maxChallenges, boolean allowCompound) {
        this.challengeTimeoutMs = challengeTimeoutMs;
        this.maxRetries = maxRetries;
        this.maxChallenges = maxChallenges;
        this.allowCompound = allowCompound;
    }

    public long challengeTimeoutMs() { return challengeTimeoutMs; }
    public int maxRetries() { return maxRetries; }
    public int maxChallenges() { return maxChallenges; }
    public boolean allowCompound() { return allowCompound; }

    /**
     * Validates that this profile does NOT loosen security-critical parameters
     * relative to the base config (R5).
     *
     * @throws IllegalArgumentException if the profile violates R5 bounds
     */
    public void validateAgainst(LivenessConfig baseConfig) {
        // R5: profiles may NOT change attackThreshold, passThreshold,
        // persistence windows, capture-integrity rules, or same-face thresholds.
        // These are already in LivenessConfig and are never per-profile.
        // We only validate that the profile's own values are within
        // server-set bounds.
        if (maxRetries < 0) {
            throw new IllegalArgumentException(
                    "Profile " + name() + " maxRetries must be >= 0");
        }
        if (maxChallenges < 1) {
            throw new IllegalArgumentException(
                    "Profile " + name() + " maxChallenges must be >= 1");
        }
        if (challengeTimeoutMs < 1000) {
            throw new IllegalArgumentException(
                    "Profile " + name() + " challengeTimeoutMs must be >= 1000ms");
        }
    }
}
