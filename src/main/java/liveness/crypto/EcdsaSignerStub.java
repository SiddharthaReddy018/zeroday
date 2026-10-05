package liveness.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * M1 stub for ECDSA P-256 signing (§6.5).
 * Generates an ephemeral key pair at construction.
 * M2 replaces this with a key-store-backed signer.
 */
public final class EcdsaSignerStub {

    private static final String ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";
    private static final int KEY_SIZE = 256;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public EcdsaSignerStub() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            kpg.initialize(KEY_SIZE);
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey = kp.getPublic();
        } catch (Exception e) {
            throw new AssertionError("ECDSA P-256 not available on this JVM", e);
        }
    }

    /**
     * Signs data using ECDSA P-256.
     *
     * @param data the canonical string to sign
     * @return the DER-encoded signature bytes
     */
    public byte[] sign(String data) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("ECDSA sign failed", e);
        }
    }

    /**
     * Verifies a signature against data.
     *
     * @param data      the canonical string that was signed
     * @param signature the DER-encoded signature bytes
     * @return true if the signature is valid
     */
    public boolean verify(String data, byte[] signature) {
        try {
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException("ECDSA verify failed", e);
        }
    }

    public PublicKey publicKey() { return publicKey; }
}
