package liveness.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration holder for the liveness system (Section 7).
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

    // --- Uncalibrated action and quality seeds (HANDOFF-2 R12, AUDIT_TODO C1) ---
    private final double blurMinVariance;
    private final boolean relativeBlurEnabled;
    private final int relativeBlurWindow;
    private final double relativeBlurDropFactor;
    private final double relativeBlurYShiftFactor;
    private final double brightnessMin;
    private final double brightnessMax;
    private final double earBlinkThreshold;
    private final double marSmileThreshold;
    private final double yawTurnThreshold;
    private final double yawLookThreshold;
    private final double pitchLookThreshold;
    private final int minActionFrames;

    // --- Frame sequence monitor ---
    private final int frameFreezePHashWindowSize;
    private final int freezeConsecutiveFrames;
    private final int loopSequenceLengthK;
    private final int loopHammingThreshold;
    private final int freezeHammingThreshold;
    private final int loopMinSegmentMotionBits;
    private final boolean loopReplayHardFail;
    private final boolean forceChallenge;
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
        this.blurMinVariance = b.blurMinVariance;
        this.relativeBlurEnabled = b.relativeBlurEnabled;
        this.relativeBlurWindow = b.relativeBlurWindow;
        this.relativeBlurDropFactor = b.relativeBlurDropFactor;
        this.relativeBlurYShiftFactor = b.relativeBlurYShiftFactor;
        this.brightnessMin = b.brightnessMin;
        this.brightnessMax = b.brightnessMax;
        this.earBlinkThreshold = b.earBlinkThreshold;
        this.marSmileThreshold = b.marSmileThreshold;
        this.yawTurnThreshold = b.yawTurnThreshold;
        this.yawLookThreshold = b.yawLookThreshold;
        this.pitchLookThreshold = b.pitchLookThreshold;
        this.minActionFrames = b.minActionFrames;
        this.frameFreezePHashWindowSize = b.frameFreezePHashWindowSize;
        this.freezeConsecutiveFrames = b.freezeConsecutiveFrames;
        this.loopSequenceLengthK = b.loopSequenceLengthK;
        this.loopHammingThreshold = b.loopHammingThreshold;
        this.freezeHammingThreshold = b.freezeHammingThreshold;
        this.loopMinSegmentMotionBits = b.loopMinSegmentMotionBits;
        this.loopReplayHardFail = b.loopReplayHardFail;
        this.forceChallenge = b.forceChallenge;
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
    public double blurMinVariance() { return blurMinVariance; }
    public boolean relativeBlurEnabled() { return relativeBlurEnabled; }
    public int relativeBlurWindow() { return relativeBlurWindow; }
    public double relativeBlurDropFactor() { return relativeBlurDropFactor; }
    public double relativeBlurYShiftFactor() { return relativeBlurYShiftFactor; }
    public double brightnessMin() { return brightnessMin; }
    public double brightnessMax() { return brightnessMax; }
    public double earBlinkThreshold() { return earBlinkThreshold; }
    public double marSmileThreshold() { return marSmileThreshold; }
    public double yawTurnThreshold() { return yawTurnThreshold; }
    public double yawLookThreshold() { return yawLookThreshold; }
    public double pitchLookThreshold() { return pitchLookThreshold; }
    public int minActionFrames() { return minActionFrames; }
    public int frameFreezePHashWindowSize() { return frameFreezePHashWindowSize; }
    public int loopWindowFrames() { return frameFreezePHashWindowSize; }
    public int freezeConsecutiveFrames() { return freezeConsecutiveFrames; }
    public int loopSequenceLengthK() { return loopSequenceLengthK; }
    public int loopHammingThreshold() { return loopHammingThreshold; }
    public int freezeHammingThreshold() { return freezeHammingThreshold; }
    public int loopMinSegmentMotionBits() { return loopMinSegmentMotionBits; }
    /** When false (default), LOOP_REPLAY is a soft signal: logged and routed to an active challenge. */
    public boolean loopReplayHardFail() { return loopReplayHardFail; }
    /** DEV/demo: when true, a clear passive pass still routes to an active challenge. Default false. */
    public boolean forceChallenge() { return forceChallenge; }
    public long rcaptureFreshnessWindowMs() { return rcaptureFreshnessWindowMs; }
    public int configVersion() { return configVersion; }
    public String challengeProfile() { return challengeProfile; }

    /** Returns defaults for M1 testing. */
    public static LivenessConfig defaults() {
        return new Builder().build();
    }

    /** Alias for defaults(); used by tests and IAD runner to obtain production config. */
    public static LivenessConfig defaultConfig() {
        return defaults();
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
        private double blurMinVariance = 60.0;
        private boolean relativeBlurEnabled = false;
        private int relativeBlurWindow = 30;
        private double relativeBlurDropFactor = 2.0;
        private double relativeBlurYShiftFactor = 1.5;
        private double brightnessMin = 40.0;
        private double brightnessMax = 250.0;
        private double earBlinkThreshold = 0.22;
        private double marSmileThreshold = 0.6;
        private double yawTurnThreshold = 15.0;
        private double yawLookThreshold = 10.0;
        private double pitchLookThreshold = 20.0;
        private int minActionFrames = 1;
        private int frameFreezePHashWindowSize = 15;
        private int freezeConsecutiveFrames = 5;
        private int loopSequenceLengthK = 5;
        private int loopHammingThreshold = 6;
        private int freezeHammingThreshold = 2;
        private int loopMinSegmentMotionBits = 3;
        private boolean loopReplayHardFail = false;
        private boolean forceChallenge = false;
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
        public Builder relativeBlurEnabled(boolean v)       { this.relativeBlurEnabled = v; return this; }
        public Builder relativeBlurWindow(int v)            { this.relativeBlurWindow = v; return this; }
        public Builder relativeBlurDropFactor(double v)     { this.relativeBlurDropFactor = v; return this; }
        public Builder relativeBlurYShiftFactor(double v)   { this.relativeBlurYShiftFactor = v; return this; }
        public Builder blurMinVariance(double v)       { this.blurMinVariance = v; return this; }
        public Builder brightnessMin(double v)         { this.brightnessMin = v; return this; }
        public Builder brightnessMax(double v)         { this.brightnessMax = v; return this; }
        public Builder earBlinkThreshold(double v)     { this.earBlinkThreshold = v; return this; }
        public Builder marSmileThreshold(double v)     { this.marSmileThreshold = v; return this; }
        public Builder yawTurnThreshold(double v)      { this.yawTurnThreshold = v; return this; }
        public Builder yawLookThreshold(double v)      { this.yawLookThreshold = v; return this; }
        public Builder pitchLookThreshold(double v)    { this.pitchLookThreshold = v; return this; }
        public Builder minActionFrames(int v)          { this.minActionFrames = v; return this; }
        public Builder frameFreezePHashWindowSize(int v) {
            this.frameFreezePHashWindowSize = v;
            return this;
        }
        public Builder loopWindowFrames(int v) {
            this.frameFreezePHashWindowSize = v;
            return this;
        }
        public Builder freezeConsecutiveFrames(int v) {
            this.freezeConsecutiveFrames = v;
            return this;
        }
        public Builder loopSequenceLengthK(int v)      { this.loopSequenceLengthK = v; return this; }
        public Builder loopHammingThreshold(int v)     { this.loopHammingThreshold = v; return this; }
        public Builder freezeHammingThreshold(int v)   { this.freezeHammingThreshold = v; return this; }
        public Builder loopMinSegmentMotionBits(int v)  { this.loopMinSegmentMotionBits = v; return this; }
        public Builder loopReplayHardFail(boolean v)    { this.loopReplayHardFail = v; return this; }
        public Builder forceChallenge(boolean v)        { this.forceChallenge = v; return this; }
        public Builder rcaptureFreshnessWindowMs(long v) { this.rcaptureFreshnessWindowMs = v; return this; }
        public Builder configVersion(int v)            { this.configVersion = v; return this; }
        public Builder challengeProfile(String v)      { this.challengeProfile = Objects.requireNonNull(v); return this; }

        /**
         * Validates invariants and builds the config.
         *
         * @throws IllegalArgumentException if attackThreshold >= passThreshold (Section 5.1)
         */
        public LivenessConfig build() {
            if (attackThreshold < 0 || attackThreshold > 100) {
                throw new IllegalArgumentException("attackThreshold must be within [0, 100], got: " + attackThreshold);
            }
            if (passThreshold < 0 || passThreshold > 100) {
                throw new IllegalArgumentException("passThreshold must be within [0, 100], got: " + passThreshold);
            }
            if (attackThreshold >= passThreshold) {
                throw new IllegalArgumentException(
                        "attackThreshold (" + attackThreshold
                                + ") must be < passThreshold (" + passThreshold + ")");
            }
            if (passThreshold - attackThreshold < 10) {
                throw new IllegalArgumentException(
                        "passThreshold (" + passThreshold + ") must exceed attackThreshold ("
                                + attackThreshold + ") by at least minimum gap of 10");
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
            if (freezeConsecutiveFrames < 2) {
                throw new IllegalArgumentException("freezeConsecutiveFrames must be >= 2, got: " + freezeConsecutiveFrames);
            }
            if (loopSequenceLengthK < 5) {
                throw new IllegalArgumentException("loopSequenceLengthK must be >= 5, got: " + loopSequenceLengthK);
            }
            if (loopHammingThreshold < 0 || loopHammingThreshold > 64) {
                throw new IllegalArgumentException("loopHammingThreshold must be between 0 and 64, got: " + loopHammingThreshold);
            }
            if (freezeHammingThreshold < 0 || freezeHammingThreshold > 64) {
                throw new IllegalArgumentException("freezeHammingThreshold must be between 0 and 64, got: " + freezeHammingThreshold);
            }
            if (frameFreezePHashWindowSize < 2 * loopSequenceLengthK + 1) {
                throw new IllegalArgumentException(
                        "frameFreezePHashWindowSize (" + frameFreezePHashWindowSize
                                + ") must be >= 2*loopSequenceLengthK+1 (" + (2 * loopSequenceLengthK + 1) + ")");
            }
            if (freezeConsecutiveFrames > frameFreezePHashWindowSize) {
                throw new IllegalArgumentException(
                        "freezeConsecutiveFrames (" + freezeConsecutiveFrames
                                + ") must be <= frameFreezePHashWindowSize (" + frameFreezePHashWindowSize + ")");
            }
            if (loopMinSegmentMotionBits < 0 || loopMinSegmentMotionBits > 64) {
                throw new IllegalArgumentException("loopMinSegmentMotionBits must be between 0 and 64, got: " + loopMinSegmentMotionBits);
            }
            return new LivenessConfig(this);
        }
    }

}
