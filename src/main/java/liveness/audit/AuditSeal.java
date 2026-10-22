package liveness.audit;

import liveness.crypto.Signer;

import java.util.Base64;
import java.util.Objects;

/**
 * Signed audit-chain head (ADR-010). Attestations only exist for successful sessions, so blocked, failed and
 * device-error sessions would otherwise leave an unanchored, rewritable log. This signs
 * {@code AUDITHEAD|sessionId|size|headHash|signerPublicKey} for ANY terminal outcome. Anyone holding the
 * signer's trusted public key can detect a rewritten, truncated or extended log. It does not protect against an
 * attacker who also controls the signing key: keep that key in the platform key store in production.
 */
public final class AuditSeal {
    private AuditSeal() {}

    public static final class SignedHead {
        private final String sessionId;
        private final int size;
        private final String headHash;
        private final String signerPublicKeyB64;
        private final byte[] signature;

        SignedHead(String sessionId, int size, String headHash, String pub, byte[] sig) {
            this.sessionId = sessionId; this.size = size; this.headHash = headHash;
            this.signerPublicKeyB64 = pub; this.signature = sig;
        }
        public String sessionId() { return sessionId; }
        public int size() { return size; }
        public String headHash() { return headHash; }
        public String signerPublicKeyB64() { return signerPublicKeyB64; }
        public byte[] signature() { return signature.clone(); }
        public String canonical() { return AuditSeal.canonicalOf(sessionId, size, headHash, signerPublicKeyB64); }
    }

    private static String canonicalOf(String sid, int size, String head, String pub) {
        return "AUDITHEAD|" + sid + "|" + size + "|" + head + "|" + pub;
    }

    public static SignedHead sign(AuditChain chain, String sessionId, Signer signer) {
        Objects.requireNonNull(chain, "chain");
        String pub = Base64.getEncoder().encodeToString(signer.publicKey().getEncoded());
        String head = chain.latestHash();
        int size = chain.size();
        return new SignedHead(sessionId, size, head, pub, signer.sign(canonicalOf(sessionId, size, head, pub)));
    }

    /** True iff the signature is valid AND the given chain verifies and still matches the signed size and head. */
    public static boolean verify(SignedHead h, AuditChain chain, Signer signer) {
        return h != null && chain != null
                && chain.verify()
                && chain.size() == h.size()
                && chain.latestHash().equals(h.headHash())
                && signer.verify(h.canonical(), h.signature());
    }
}
