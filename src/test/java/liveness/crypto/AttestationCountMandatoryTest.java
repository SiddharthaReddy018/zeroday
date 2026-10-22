package liveness.crypto;

import liveness.audit.AuditChain;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The audit event count is part of the signed attestation and must always be present
 * (Part 1 patch, ADR-016). A negative count is rejected; the legacy no-count overloads
 * fail closed against any non-empty audit chain instead of silently skipping the check.
 */
class AttestationCountMandatoryTest {

    private static Attestation.PadResult pad() {
        return new Attestation.PadResult("NO_ATTACK", 25, Arrays.asList("MiniFASNet"), Collections.emptyList());
    }

    @Test
    void negativeCountIsRejected() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        assertThrows(IllegalArgumentException.class, () -> Attestation.createFromSuccessSession(
                "s", "sess", "imgsha", "transcript", "T1", 1L, "m-1", 1, "STREAM", pad(), -1, signer));
    }

    @Test
    void legacyOverloadWithoutCountFailsClosedAgainstNonEmptyChain() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        AuditChain chain = new AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "PAD", "SCORE", "score=80"));
        Attestation att = Attestation.createFromSuccessSession(
                "s", "sess", "imgsha", chain.latestHash(), "T1", 1L, "m-1", 1, "STREAM", pad(), signer);
        assertFalse(att.verifyAuditChain(chain), "unknown count must not verify against a real chain");
    }

    @Test
    void explicitCountVerifiesAndAlteringItBreaksSignature() {
        EcdsaSignerStub signer = new EcdsaSignerStub();
        AuditChain chain = new AuditChain();
        chain.append(new liveness.audit.AuditEvent(1000L, "PAD", "SCORE", "score=80"));
        Attestation att = Attestation.createFromSuccessSession(
                "s", "sess", "imgsha", chain.latestHash(), chain.latestHash(),
                "T1", 1L, "m-1", 1, "STREAM", pad(), chain.size(), signer);
        assertTrue(att.verifyAuditChain(chain));
        assertTrue(att.verify(signer));
        assertTrue(att.canonical().contains("|" + chain.size() + "|"), "count must be in the signed string");
    }
}
