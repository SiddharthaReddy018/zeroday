package liveness.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.*;

class AndroidKeyStoreSignerTest {

    @Test
    void standardJavaEcKeyPairProducesValidSignatures() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        AndroidKeyStoreSigner signer = new AndroidKeyStoreSigner("test-key", kp.getPrivate(), kp.getPublic());
        assertEquals("test-key", signer.keyAlias());
        assertEquals(kp.getPublic(), signer.publicKey());

        String data = "canonical-payload-to-sign";
        byte[] sig = signer.sign(data);
        assertNotNull(sig);
        assertTrue(sig.length > 0);

        // Verify using signer method
        assertTrue(signer.verify(data, sig));
        assertFalse(signer.verify("tampered-data", sig));

        // Verify using standard java.security.Signature directly
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(kp.getPublic());
        verifier.update(data.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(sig));
    }

    @Test
    void ensureKeyThrowsKeyStoreExceptionOffDevice() {
        KeyStoreException ex = assertThrows(KeyStoreException.class, () ->
                AndroidKeyStoreSigner.ensureKey("non-existent-alias")
        );
        assertTrue(ex.getMessage().contains("unavailable") || ex.getMessage().contains("desktop JVM")
                || ex.getMessage().contains("AndroidKeyStore"), "Expected clear off-device message: " + ex.getMessage());
    }

    @Test
    void defaultConstructorFailsOffDevice() {
        assertThrows(IllegalStateException.class, AndroidKeyStoreSigner::new);
    }
}
