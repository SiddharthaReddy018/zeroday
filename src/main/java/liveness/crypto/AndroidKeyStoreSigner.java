package liveness.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;

/**
 * Android KeyStore-backed ECDSA P-256 signer (§6.5, S10).
 * <p>
 * Key generation and private key operations take place within the Android hardware-backed
 * keystore / TEE / StrongBox.
 * <p>
 * <b>Verifier Public Key Discovery:</b>
 * Verifiers (MOSIP backend or relying parties) obtain and trust the signer's public key by:
 * <ol>
 *   <li>Extracting {@link Attestation#signerPublicKeyB64()}, which is covered under the canonical signature.</li>
 *   <li>Verifying the public key against the MOSIP Device Management System (DMS) device registration registry,
 *       or by verifying the device attestation certificate chain exported during device onboarding.</li>
 * </ol>
 */
public final class AndroidKeyStoreSigner implements Signer {

    public static final String DEFAULT_ALIAS = "mosip_liveness_attestation_key";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final String keyAlias;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public AndroidKeyStoreSigner() {
        this(DEFAULT_ALIAS);
    }

    public AndroidKeyStoreSigner(String keyAlias) {
        this.keyAlias = keyAlias;
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (!keyStore.containsAlias(keyAlias)) {
                // Key generation requires Android runtime classes (KeyGenParameterSpec)
                throw new IllegalStateException("Key alias '" + keyAlias + "' does not exist in AndroidKeyStore. "
                        + "Generate the key in AndroidKeyStore using KeyGenParameterSpec before initializing AndroidKeyStoreSigner.");
            }
            this.privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);
            Certificate cert = keyStore.getCertificate(keyAlias);
            if (cert == null || privateKey == null) {
                throw new IllegalStateException("Failed to retrieve private key or certificate for alias: " + keyAlias);
            }
            this.publicKey = cert.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("AndroidKeyStore unavailable or key lookup failed: " + e.getMessage(), e);
        }
    }

    /** Constructor for wrapping existing KeyStore keys or tests. */
    public AndroidKeyStoreSigner(String keyAlias, PrivateKey privateKey, PublicKey publicKey) {
        this.keyAlias = keyAlias;
        this.privateKey = java.util.Objects.requireNonNull(privateKey, "privateKey");
        this.publicKey = java.util.Objects.requireNonNull(publicKey, "publicKey");
    }

    @Override
    public byte[] sign(String data) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(privateKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("AndroidKeyStore sign failed for alias " + keyAlias, e);
        }
    }

    @Override
    public boolean verify(String data, byte[] signature) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException("AndroidKeyStore verify failed for alias " + keyAlias, e);
        }
    }

    @Override
    public PublicKey publicKey() {
        return publicKey;
    }

    public String keyAlias() {
        return keyAlias;
    }
}
