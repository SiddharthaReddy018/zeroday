package liveness.audit;

import liveness.crypto.EcdsaSignerStub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditSealTest {

    private AuditChain chain(int n) {
        AuditChain c = new AuditChain();
        for (int i = 0; i < n; i++) c.append(new AuditEvent(1000L + i, "T", "E" + i, "f=" + i));
        return c;
    }

    @Test
    void signedHeadVerifiesAndDetectsTruncationAndExtension() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        AuditChain c = chain(4);
        AuditSeal.SignedHead h = AuditSeal.sign(c, "sess", s);
        assertTrue(AuditSeal.verify(h, c, s));
        assertFalse(AuditSeal.verify(h, chain(3), s), "truncated log must fail");
        AuditChain longer = chain(4);
        longer.append(new AuditEvent(9999L, "T", "EXTRA", ""));
        assertFalse(AuditSeal.verify(h, longer, s), "extended log must fail");
    }

    @Test
    void rewrittenLogWithRecomputedHashesStillFailsAgainstSignedHead() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        AuditSeal.SignedHead h = AuditSeal.sign(chain(3), "sess", s);
        AuditChain forged = new AuditChain();   // attacker rebuilds a consistent but different log
        for (int i = 0; i < 3; i++) forged.append(new AuditEvent(1000L + i, "T", "FORGED" + i, ""));
        assertTrue(forged.verify());
        assertFalse(AuditSeal.verify(h, forged, s));
    }

    @Test
    void signatureFromAnotherKeyIsRejected() {
        AuditChain c = chain(2);
        AuditSeal.SignedHead h = AuditSeal.sign(c, "sess", new EcdsaSignerStub());
        assertFalse(AuditSeal.verify(h, c, new EcdsaSignerStub()));
    }

    @Test
    void unsealedChainCannotBeExported() {
        AuditChain c = chain(3);
        assertThrows(IllegalStateException.class, c::exportTrail);
    }

    @Test
    void exportAndImportSealedTrailVerifiesAgainstSignedHead() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        AuditChain c = chain(5);
        c.seal();
        AuditSeal.SignedHead h = AuditSeal.sign(c, "sess-export", s);

        String exported = c.exportTrail();
        assertTrue(exported.contains("AUDIT_TRAIL_V1"));
        assertTrue(exported.contains("SEALED_SIZE:5"));
        assertTrue(exported.contains(h.headHash()));

        AuditChain imported = AuditChain.importTrail(exported);
        assertTrue(imported.verifySealed());
        assertTrue(AuditSeal.verify(h, imported, s));
    }

    @Test
    void truncatedExportedTrailFailsSignedHeadVerification() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        AuditChain c = chain(5);
        c.seal();
        AuditSeal.SignedHead h = AuditSeal.sign(c, "sess-trunc", s);

        String exported = c.exportTrail();
        // Drop the last entry line
        String[] lines = exported.split("\n");
        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < lines.length - 1; i++) {
            truncated.append(lines[i]).append("\n");
        }

        AuditChain importedTruncated = AuditChain.importTrail(truncated.toString());
        // Truncated log has only 4 entries instead of 5, and its latestHash differs
        assertFalse(AuditSeal.verify(h, importedTruncated, s),
                "Truncated audit trail must fail SignedHead verification");
    }
}
