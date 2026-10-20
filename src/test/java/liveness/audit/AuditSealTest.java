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
}
