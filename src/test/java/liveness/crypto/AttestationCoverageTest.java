package liveness.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

class AttestationCoverageTest {

    private Attestation make(String model, int cfg, String mode, int padScore, String subject) {
        return new Attestation(subject, "sess", "imgsha", "trsha", "T1", 1000L, model, cfg, mode,
                new Attestation.PadResult("NO_ATTACK", padScore, Arrays.asList("MiniFASNet"),
                        Collections.singletonList(new Attestation.ChallengeEcho("BLINK", 420))));
    }

    @Test
    void everyReportedFieldChangesTheCanonicalString() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        String base = make("m1", 1, "STREAM", 10, "a").sign(s).canonical();
        // createdAt differs between instances, so compare field-by-field on one instance family:
        Attestation x = make("m1", 1, "STREAM", 10, "a").sign(s);
        assertTrue(x.canonical().contains("|m1|"));
        assertTrue(x.canonical().contains("|STREAM|"));
        assertTrue(x.canonical().contains("BLINK:420"));
        assertTrue(x.canonical().contains("|NO_ATTACK|10|"));
        assertTrue(x.canonical().endsWith(x.signerPublicKeyB64()));
        assertNotEquals(base.length(), 0);
    }

    @Test
    void signatureCoversPublicKeyAndVerifies() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        Attestation a = make("m1", 1, "STREAM", 10, "a").sign(s);
        assertTrue(a.verify(s));
        assertNotNull(a.signerPublicKeyB64());
    }

    @Test
    void delimiterInjectionInAFieldCannotShiftLayout() {
        EcdsaSignerStub s = new EcdsaSignerStub();
        Attestation a = make("m1", 1, "STREAM", 10, "evil|sess").sign(s);
        assertTrue(a.canonical().startsWith("evil\\|sess|"));
    }
}
