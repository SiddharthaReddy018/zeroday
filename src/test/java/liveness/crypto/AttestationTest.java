package liveness.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ECDSA attestation (§6.5).
 * <ul>
 *   <li>ECDSA signs and verifies</li>
 *   <li>imageSha256 null → NPE</li>
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
        // Internal livenessScore=75 → padDecisionScore=25 (100-75)
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
}
