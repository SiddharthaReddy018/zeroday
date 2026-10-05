package liveness.crypto;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Liveness attestation (§6.5).
 * <p>
 * Produced ONLY from {@code VERIFYING_FINAL -> SUCCESS}.
 * <p>
 * Canonical string for signing:
 * {@code subjectId|sessionId|verdict|imageSha256|transcriptSha256|deviceTier|captureTimestamp|createdAtEpochMs}
 * <p>
 * {@code imageSha256} is validated non-null before {@code sign()} — intentional NPE if null.
 */
public final class Attestation {

    private final String subjectId;
    private final String sessionId;
    private final String verdict;          // always "PASS"
    private final String imageSha256;
    private final String transcriptSha256;
    private final String deviceTier;
    private final long captureTimestamp;
    private final String modelVersion;
    private final int configVersion;
    private final String mode;
    private final PadResult pad;
    private final long createdAtEpochMs;
    private byte[] signature;

    public Attestation(String subjectId, String sessionId,
                       String imageSha256, String transcriptSha256,
                       String deviceTier, long captureTimestamp,
                       String modelVersion, int configVersion,
                       String mode, PadResult pad) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.verdict = "PASS";
        // intentional: NPE if imageSha256 is null (§6.5)
        this.imageSha256 = Objects.requireNonNull(imageSha256, "imageSha256");
        this.transcriptSha256 = Objects.requireNonNull(transcriptSha256, "transcriptSha256");
        this.deviceTier = Objects.requireNonNull(deviceTier, "deviceTier");
        this.captureTimestamp = captureTimestamp;
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
        this.configVersion = configVersion;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.pad = Objects.requireNonNull(pad, "pad");
        this.createdAtEpochMs = System.currentTimeMillis();
    }

    /**
     * The canonical string used for ECDSA signing.
     */
    public String canonical() {
        return subjectId + "|" + sessionId + "|" + verdict + "|" + imageSha256
                + "|" + transcriptSha256 + "|" + deviceTier + "|" + captureTimestamp
                + "|" + createdAtEpochMs;
    }

    /**
     * Signs this attestation.
     *
     * @return this attestation (for chaining)
     */
    public Attestation sign(EcdsaSignerStub signer) {
        this.signature = signer.sign(canonical());
        return this;
    }

    /**
     * Verifies the signature.
     */
    public boolean verify(EcdsaSignerStub signer) {
        if (signature == null) {
            return false;
        }
        return signer.verify(canonical(), signature);
    }

    // --- Getters ---
    public String subjectId()        { return subjectId; }
    public String sessionId()        { return sessionId; }
    public String verdict()          { return verdict; }
    public String imageSha256()      { return imageSha256; }
    public String transcriptSha256() { return transcriptSha256; }
    public String deviceTier()       { return deviceTier; }
    public long captureTimestamp()    { return captureTimestamp; }
    public String modelVersion()     { return modelVersion; }
    public int configVersion()       { return configVersion; }
    public String mode()             { return mode; }
    public PadResult pad()           { return pad; }
    public long createdAtEpochMs()   { return createdAtEpochMs; }
    public byte[] signature()        { return signature; }

    /**
     * PAD result sub-record (§6.5).
     * Score direction at MOSIP boundary: padDecisionScore = 100 - livenessScore
     * (lower = more bona fide, MOSIP issue #1117).
     */
    public static final class PadResult {
        private final String decision;   // "NO_ATTACK" when verdict=PASS
        private final int score;         // 100 - livenessScore
        private final List<String> mechanism;
        private final List<ChallengeEcho> challengeEcho;

        public PadResult(String decision, int score,
                         List<String> mechanism, List<ChallengeEcho> challengeEcho) {
            this.decision = Objects.requireNonNull(decision);
            this.score = score;
            this.mechanism = Collections.unmodifiableList(mechanism);
            this.challengeEcho = Collections.unmodifiableList(challengeEcho);
        }

        public String decision()            { return decision; }
        public int score()                   { return score; }
        public List<String> mechanism()      { return mechanism; }
        public List<ChallengeEcho> challengeEcho() { return challengeEcho; }
    }

    /** Echo of a challenge action and its observed latency. */
    public static final class ChallengeEcho {
        private final String action;
        private final long latencyMs;

        public ChallengeEcho(String action, long latencyMs) {
            this.action = action;
            this.latencyMs = latencyMs;
        }

        public String action()   { return action; }
        public long latencyMs()  { return latencyMs; }
    }
}
