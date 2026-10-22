package liveness.crypto;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Liveness attestation (§6.5).
 * <p>
 * Produced ONLY from {@code VERIFYING_FINAL -> SUCCESS}.
 * <p>
 * Canonical string for signing (fields escaped, pipe-delimited):
 * {@code subjectId|sessionId|verdict|imageSha256|transcriptSha256|deviceTier|captureTimestamp|createdAtEpochMs|
 * modelVersion|configVersion|mode|padDecision|padScore|mechanisms|challengeEchoes|signerPublicKey}
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
    private final int auditEntryCount;
    private byte[] signature;
    private String signerPublicKeyB64;

    Attestation(String subjectId, String sessionId,
                String imageSha256, String transcriptSha256,
                String deviceTier, long captureTimestamp,
                String modelVersion, int configVersion,
                String mode, PadResult pad) {
        this(subjectId, sessionId, imageSha256, transcriptSha256,
                deviceTier, captureTimestamp, modelVersion, configVersion, mode, pad, -1);
    }

    Attestation(String subjectId, String sessionId,
                String imageSha256, String transcriptSha256,
                String deviceTier, long captureTimestamp,
                String modelVersion, int configVersion,
                String mode, PadResult pad, int auditEntryCount) {
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
        this.auditEntryCount = auditEntryCount;
    }

    /**
     * Factory reachable only from the session success commit path (§6.5, S10).
     */
    public static Attestation createFromSuccessSession(
            String subjectId, String sessionId,
            String imageSha256, String transcriptSha256,
            String deviceTier, long captureTimestamp,
            String modelVersion, int configVersion,
            String mode, PadResult pad, Signer signer) {
        return createFromSuccessSession(subjectId, sessionId, imageSha256, transcriptSha256,
                deviceTier, captureTimestamp, modelVersion, configVersion, mode, pad, -1, signer);
    }

    public static Attestation createFromSuccessSession(
            String subjectId, String sessionId,
            String imageSha256, String transcriptSha256,
            String deviceTier, long captureTimestamp,
            String modelVersion, int configVersion,
            String mode, PadResult pad, int auditEntryCount, Signer signer) {
        Attestation a = new Attestation(
                subjectId, sessionId, imageSha256, transcriptSha256,
                deviceTier, captureTimestamp, modelVersion, configVersion, mode, pad, auditEntryCount);
        if (signer != null) {
            a.sign(signer);
        }
        return a;
    }

    /**
     * The canonical string used for ECDSA signing.
     */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        field(sb, subjectId); field(sb, sessionId); field(sb, verdict); field(sb, imageSha256);
        field(sb, transcriptSha256);
        if (auditEntryCount >= 0) {
            field(sb, String.valueOf(auditEntryCount));
        }
        field(sb, deviceTier); field(sb, String.valueOf(captureTimestamp));
        field(sb, String.valueOf(createdAtEpochMs));
        // Fields added so that model, config, mode and the PAD result are tamper-evident too.
        field(sb, modelVersion); field(sb, String.valueOf(configVersion)); field(sb, mode);
        field(sb, pad.decision()); field(sb, String.valueOf(pad.score()));
        field(sb, String.join(",", pad.mechanism()));
        StringBuilder echo = new StringBuilder();
        for (ChallengeEcho e : pad.challengeEcho()) {
            if (echo.length() > 0) echo.append(',');
            echo.append(e.action()).append(':').append(e.latencyMs());
        }
        field(sb, echo.toString());
        field(sb, signerPublicKeyB64 == null ? "" : signerPublicKeyB64);
        sb.setLength(sb.length() - 1); // drop trailing delimiter
        return sb.toString();
    }

    /** Appends an escaped field + '|'. '\\' and '|' are escaped so fields cannot shift the delimiter layout. */
    private static void field(StringBuilder sb, String v) {
        String x = v == null ? "" : v;
        sb.append(x.replace("\\", "\\\\").replace("|", "\\|")).append('|');
    }

    /** Base64 X.509 SubjectPublicKeyInfo of the signing key; set by sign() and covered by the signature. */
    public String signerPublicKeyB64() { return signerPublicKeyB64; }

    /**
     * Signs this attestation using a Signer.
     *
     * @return this attestation (for chaining)
     */
    public Attestation sign(Signer signer) {
        Objects.requireNonNull(signer, "signer");
        this.signerPublicKeyB64 = java.util.Base64.getEncoder().encodeToString(signer.publicKey().getEncoded());
        this.signature = signer.sign(canonical());
        return this;
    }

    /**
     * Verifies the signature using a Signer.
     */
    public boolean verify(Signer signer) {
        if (signature == null || signer == null) {
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
    public String auditHeadHash()    { return transcriptSha256; }
    public int auditEntryCount()     { return auditEntryCount; }
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
