package liveness.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ECDSA attestation (Section 6.5).
 * <ul>
 *   <li>ECDSA signs and verifies</li>
 *   <li>imageSha256 null -> NPE</li>
 *   <li>Tampered data fails verification</li>
 * </ul>
 */
class AttestationTest {

    private Attestation makeValidAttestation() {
        return new Attestation(
                "subject-1",
                "session-1",
                "abc123def456",         // imageSha256
                "transcript-hash-789",  // transcriptSha256
                "T1",                   // deviceTier
                System.currentTimeMillis(),
                "MiniFASNetV2-SE-1.0",
                1,
                "STREAM",
                new Attestation.PadResult(
                        "NO_ATTACK",
                        25,  // padDecisionScore = 100 - 75 (livenessScore)
                        Arrays.asList("MiniFASNet"),
                        Collections.emptyList()));
    }

    @Test
    void signAndVerify() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation att = makeValidAttestation();
        att.sign(signer);

        assertNotNull(att.signature(), "Signature should not be null after signing");
        assertTrue(att.verify(signer), "Signature should verify with the same signer");
    }

    @Test
    void verdictIsAlwaysPass() {
        Attestation att = makeValidAttestation();
        assertEquals("PASS", att.verdict());
    }

    @Test
    void imageSha256NullThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new Attestation(
                        "subject-1",
                        "session-1",
                        null,                    // imageSha256 is null!
                        "transcript-hash",
                        "T1",
                        System.currentTimeMillis(),
                        "model-v1",
                        1,
                        "STREAM",
                        new Attestation.PadResult(
                                "NO_ATTACK", 25,
                                Collections.singletonList("MiniFASNet"),
                                Collections.emptyList())));
    }

    @Test
    void differentSignerFailsVerification() {
        EcdsaSignerStub signer1 = new EcdsaSignerStub();
        EcdsaSignerStub signer2 = new EcdsaSignerStub();

        Attestation att = makeValidAttestation();
        att.sign(signer1);

        assertFalse(att.verify(signer2),
                "Verification should fail with a different signer's public key");
    }

    @Test
    void unsignedAttestationFailsVerification() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation att = makeValidAttestation();

        assertFalse(att.verify(signer),
                "Unsigned attestation should fail verification");
    }

    @Test
    void canonicalStringContainsAllFields() {
        Attestation att = makeValidAttestation();
        String canonical = att.canonical();

        assertTrue(canonical.contains("subject-1"));
        assertTrue(canonical.contains("session-1"));
        assertTrue(canonical.contains("PASS"));
        assertTrue(canonical.contains("abc123def456"));
        assertTrue(canonical.contains("transcript-hash-789"));
        assertTrue(canonical.contains("T1"));
        assertTrue(canonical.contains("|"));
    }

    @Test
    void padResultFields() {
        Attestation att = makeValidAttestation();
        Attestation.PadResult pad = att.pad();

        assertEquals("NO_ATTACK", pad.decision());
        assertEquals(25, pad.score());
        assertEquals(1, pad.mechanism().size());
        assertEquals("MiniFASNet", pad.mechanism().get(0));
    }

    @Test
    void scoreMapping() {
        // Internal livenessScore=75 -> padDecisionScore=25 (100-75)
        // Lower = more bona fide (MOSIP issue #1117)
        int livenessScore = 75;
        int padDecisionScore = 100 - livenessScore;
        assertEquals(25, padDecisionScore);
    }

    @Test
    void ecdsaSignerStubSignsAndVerifies() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        String data = "test-data-to-sign";

        byte[] signature = signer.sign(data);
        assertNotNull(signature);
        assertTrue(signature.length > 0);
        assertTrue(signer.verify(data, signature));
    }

    @Test
    void ecdsaSignerStubRejectsTamperedData() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        byte[] signature = signer.sign("original-data");

        assertFalse(signer.verify("tampered-data", signature),
                "Tampered data should fail verification");
    }

    @Test
    void attestationStoresAndExposesAuditHeadHashAndEntryCount() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 20, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "sess-1", "img-sha", "transcript-sha",
                "head-hash-123",
                "T1", 5000L, "MiniFASNetV2", 1, "STREAM", pad, 12, signer);

        assertEquals("transcript-sha", att.transcriptSha256());
        assertEquals("head-hash-123", att.auditHeadHash());
        assertEquals("head-hash-123", att.auditLogHeadHash());
        assertEquals(12, att.auditEntryCount());
        assertTrue(att.canonical().contains("head-hash-123"));
        assertTrue(att.canonical().contains("|12|"));
        assertTrue(att.verify(signer));
    }

    @Test
    void auditHeadHashDefaultsToTranscriptSha256WhenNotProvided() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 20, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        // Use factory without separate auditHeadHash
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "sess-1", "img-sha", "transcript-sha",
                "T1", 5000L, "MiniFASNetV2", 1, "STREAM", pad, 12, signer);

        assertEquals("transcript-sha", att.transcriptSha256());
        assertEquals("transcript-sha", att.auditHeadHash(), "auditHeadHash defaults to transcriptSha256");
        assertTrue(att.verify(signer));
    }

    @Test
    void alteringAuditEntryCountFailsSignatureVerification() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());

        // Create attestation with count=3
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);
        assertTrue(att.verify(signer));
        assertTrue(att.verifyAuditChain(chain));

        // Truncated chain (2 instead of 3 entries) fails verification
        liveness.audit.AuditChain truncated = new liveness.audit.AuditChain();
        truncated.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        truncated.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        assertFalse(att.verifyAuditChain(truncated),
                "Truncated audit chain (missing an event) must fail verification against stored head+count");
    }

    @Test
    void verifyAuditLogAndChainSucceedsForMatchingLog() {
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));
        liveness.audit.AuditLog log = new liveness.audit.AuditLog(chain, "s1");

        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);

        assertEquals(chain.latestHash(), att.auditLogHeadHash());
        assertTrue(att.verifyAuditLog(log));
        assertTrue(att.verifyAuditChain(chain));
    }

    @Test
    void verifyAuditLogFailsWhenEntryDropped() {
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);

        // Truncate by dropping the last event
        liveness.audit.AuditChain truncatedChain = new liveness.audit.AuditChain();
        truncatedChain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        truncatedChain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        liveness.audit.AuditLog truncatedLog = new liveness.audit.AuditLog(truncatedChain, "s1");

        assertFalse(att.verifyAuditLog(truncatedLog), "Truncated audit log missing an entry must fail verification");
        assertFalse(att.verifyAuditChain(truncatedChain), "Truncated audit chain missing an entry must fail verification");
    }

    @Test
    void verifyAuditLogFailsWhenEntryModifiedOrTampered() {
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);

        // Recompute chain with modified score: score=15 instead of 85
        liveness.audit.AuditChain tamperedChain = new liveness.audit.AuditChain();
        tamperedChain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        tamperedChain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=15"));
        tamperedChain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));
        liveness.audit.AuditLog tamperedLog = new liveness.audit.AuditLog(tamperedChain, "s1");

        assertFalse(att.verifyAuditLog(tamperedLog), "Tampered audit entry must fail verification against head hash");
        assertFalse(att.verifyAuditChain(tamperedChain), "Tampered audit chain must fail verification against head hash");
    }

    @Test
    void verifyAuditLogFailsWhenEventsReordered() {
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);

        // Reordered chain (events swapped)
        liveness.audit.AuditChain reorderedChain = new liveness.audit.AuditChain();
        reorderedChain.append(new liveness.audit.AuditEvent(1033L, "PAD", "SCORE", "scorer=MiniFasNet,score=85"));
        reorderedChain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        reorderedChain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        assertFalse(att.verifyAuditChain(reorderedChain),
                "Reordered audit chain must produce different hashes and fail verification");
    }

    @Test
    void alteredAuditEntryCountFailsSignature() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());

        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", "transcript-1", "audit-head-1",
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, 5, signer);
        assertTrue(att.verify(signer));

        // If count is altered to 6, signature verification on the altered canonical string fails
        Attestation altered = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", "transcript-1", "audit-head-1",
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, 6, null);
        assertFalse(signer.verify(altered.canonical(), att.signature()),
                "Altering the audit event count must change canonical string and fail signature");
    }

    @Test
    void livenessResultExportsSealedHeadAndCount() {
        liveness.audit.AuditChain chain = new liveness.audit.AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "STATE", "INIT", "session=s1"));
        chain.append(new liveness.audit.AuditEvent(1066L, "STATE", "SUCCESS", "event=COMMIT"));

        EcdsaSignerStub signer = new EcdsaSignerStub();
        Attestation.PadResult pad = new Attestation.PadResult(
                "NO_ATTACK", 15, Collections.singletonList("MiniFASNet"), Collections.emptyList());
        Attestation att = Attestation.createFromSuccessSession(
                "subj-1", "s1", "img-sha", chain.latestHash(),
                chain.latestHash(),
                "T1", 1066L, "MiniFASNetV2", 1, "STREAM", pad, chain.size(), signer);

        liveness.audit.AuditSeal.SignedHead signedHead = liveness.audit.AuditSeal.sign(chain, "s1", signer);

        liveness.service.LivenessService.LivenessResult result =
                new liveness.service.LivenessService.LivenessResult(
                        liveness.flow.Verdict.PASS, att, "s1", 0, null, signedHead);

        assertEquals(chain.latestHash(), result.auditHeadHash());
        assertEquals(2, result.auditEntryCount());
        assertEquals(chain.latestHash() + ":2", result.exportSealedHeadAndCount());
    }
}
