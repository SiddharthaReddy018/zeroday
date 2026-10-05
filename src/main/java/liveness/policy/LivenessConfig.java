package liveness.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration holder for the liveness system (§7).
 * <p>
 * All config keys use the namespace {@code mosip.registration.liveness.*}
 * or {@code mosip.face_authentication.liveness.*}. Defaults are provided
 * for every key; server-signed overrides are respected within bounds.
 */
public final class LivenessConfig {

    // --- Score thresholds (server-controlled, no local override) ---
    private final int attackThreshold;
    private final int passThreshold;
    private final int attackPersistenceWindows;

    // --- Active challenge ---
    private final boolean activeEnabled;
    private final int minChallenges;
    private final int maxChallenges;
    private final long challengeTimeoutMs;

    // --- Retry ---
    private final int maxRetries;

    // --- Quality gate seeds ---
    private final int faceSizeMinPx;
    private final int poseMaxYawDeg;
    private final int poseMaxPitchDeg;
    private final int minQualifyingFrames;

    // --- Frame sequence monitor ---
    private final int frameFreezePHashWindowSize;
    private final long rcaptureFreshnessWindowMs;

    // --- Config version ---
    private final int configVersion;

    // --- Challenge profile ---
    private final String challengeProfile;

    private LivenessConfig(Builder b) {
        this.attackThreshold = b.attackThreshold;
        this.passThreshold = b.passThreshold;
        this.attackPersistenceWindows = b.attackPersistenceWindows;
        this.activeEnabled = b.activeEnabled;
        this.minChallenges = b.minChallenges;
        this.maxChallenges = b.maxChallenges;
        this.challengeTimeoutMs = b.challengeTimeoutMs;
        this.maxRetries = b.maxRetries;
        this.faceSizeMinPx = b.faceSizeMinPx;
        this.poseMaxYawDeg = b.poseMaxYawDeg;
        this.poseMaxPitchDeg = b.poseMaxPitchDeg;
        this.minQualifyingFrames = b.minQualifyingFrames;
        this.frameFreezePHashWindowSize = b.frameFreezePHashWindowSize;
        this.rcaptureFreshnessWindowMs = b.rcaptureFreshnessWindowMs;
        this.configVersion = b.configVersion;
        this.challengeProfile = b.challengeProfile;
    }

    // --- Getters ---
    public int attackThreshold() { return attackThreshold; }
    public int passThreshold() { return passThreshold; }
    public int attackPersistenceWindows() { return attackPersistenceWindows; }
    public boolean activeEnabled() { return activeEnabled; }
    public int minChallenges() { return minChallenges; }
    public int maxChallenges() { return maxChallenges; }
    public long challengeTimeoutMs() { return challengeTimeoutMs; }
    public int maxRetries() { return maxRetries; }
    public int faceSizeMinPx() { return faceSizeMinPx; }
    public int poseMaxYawDeg() { return poseMaxYawDeg; }
    public int poseMaxPitchDeg() { return poseMaxPitchDeg; }
    public int minQualifyingFrames() { return minQualifyingFrames; }
    public int frameFreezePHashWindowSize() { return frameFreezePHashWindowSize; }
    public long rcaptureFreshnessWindowMs() { return rcaptureFreshnessWindowMs; }
    public int configVersion() { return configVersion; }
    public String challengeProfile() { return challengeProfile; }

    /** Returns defaults for M1 testing. */
    public static LivenessConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int attackThreshold = 30;
        private int passThreshold = 70;
        private int attackPersistenceWindows = 3;
        private boolean activeEnabled = true;
        private int minChallenges = 2;
        private int maxChallenges = 3;
        private long challengeTimeoutMs = 5000;
        private int maxRetries = 3;
        private int faceSizeMinPx = 64;
        private int poseMaxYawDeg = 30;
        private int poseMaxPitchDeg = 30;
        private int minQualifyingFrames = 5;
        private int frameFreezePHashWindowSize = 5;
        private long rcaptureFreshnessWindowMs = 30000;
        private int configVersion = 1;
        private String challengeProfile = "DEFAULT";

        public Builder attackThreshold(int v)         { this.attackThreshold = v; return this; }
        public Builder passThreshold(int v)           { this.passThreshold = v; return this; }
        public Builder attackPersistenceWindows(int v) { this.attackPersistenceWindows = v; return this; }
        public Builder activeEnabled(boolean v)       { this.activeEnabled = v; return this; }
        public Builder minChallenges(int v)            { this.minChallenges = v; return this; }
        public Builder maxChallenges(int v)            { this.maxChallenges = v; return this; }
        public Builder challengeTimeoutMs(long v)      { this.challengeTimeoutMs = v; return this; }
        public Builder maxRetries(int v)               { this.maxRetries = v; return this; }
        public Builder faceSizeMinPx(int v)            { this.faceSizeMinPx = v; return this; }
        public Builder poseMaxYawDeg(int v)            { this.poseMaxYawDeg = v; return this; }
        public Builder poseMaxPitchDeg(int v)          { this.poseMaxPitchDeg = v; return this; }
        public Builder minQualifyingFrames(int v)      { this.minQualifyingFrames = v; return this; }
        public Builder frameFreezePHashWindowSize(int v) { this.frameFreezePHashWindowSize = v; return this; }
        public Builder rcaptureFreshnessWindowMs(long v) { this.rcaptureFreshnessWindowMs = v; return this; }
        public Builder configVersion(int v)            { this.configVersion = v; return this; }
        public Builder challengeProfile(String v)      { this.challengeProfile = Objects.requireNonNull(v); return this; }

        /**
         * Validates invariants and builds the config.
         *
         * @throws IllegalArgumentException if attackThreshold >= passThreshold (§5.1)
         */
        public LivenessConfig build() {
            if (attackThreshold >= passThreshold) {
                throw new IllegalArgumentException(
                        "attackThreshold (" + attackThreshold
                                + ") must be < passThreshold (" + passThreshold + ")");
            }
            if (attackPersistenceWindows < 1) {
                throw new IllegalArgumentException("attackPersistenceWindows must be >= 1");
            }
            if (minChallenges < 1) {
                throw new IllegalArgumentException("minChallenges must be >= 1");
            }
            if (maxChallenges < minChallenges) {
                throw new IllegalArgumentException(
                        "maxChallenges must be >= minChallenges");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            return new LivenessConfig(this);
        }
    }
}
