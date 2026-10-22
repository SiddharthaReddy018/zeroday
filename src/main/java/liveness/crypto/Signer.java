package liveness.crypto;

import java.security.PublicKey;

/**
 * Interface for cryptographic signing and verification of attestations (§6.5, S10).
 * <p>
 * Supported implementations:
 * <ul>
 *   <li>{@link EcdsaSignerStub}: Ephemeral in-memory ECDSA P-256 for unit tests and local development.</li>
 *   <li>{@link AndroidKeyStoreSigner}: Hardware-backed Android KeyStore ECDSA P-256 for production Android tablets.</li>
 * </ul>
 */
public interface Signer {

    /**
     * Signs data using the private key.
     *
     * @param data canonical string to sign
     * @return DER-encoded signature bytes
     */
    byte[] sign(String data);

    /**
     * Verifies a signature against data using the public key.
     *
     * @param data canonical string that was signed
     * @param signature DER-encoded signature bytes
     * @return true if valid
     */
    boolean verify(String data, byte[] signature);

    /**
     * The public key used for verification.
     */
    PublicKey publicKey();
}
