package liveness.android;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Verifies MOSIP SBI JWTs (MDS spec: RFC 7515, header alg=RS256 + x5c, payload base64url).
 * <p>
 * Trust: the signature is checked against the certificate in the header's x5c[0], and that certificate must
 * either BE one of the trusted anchors or be signed by one (device provider / MOSIP-issued roots supplied by the
 * adopter). Validity dates are checked against the injected clock. Multi-level chains are not followed: if the
 * adopter uses intermediates, add them as anchors. Certificate revocation is not checked.
 * <p>
 * {@link #disabledForDevelopment()} skips all of this and exists only so dev builds can run against mock SBIs.
 */
public final class SbiJwtVerifier {

    /** Thrown for any verification failure; message is a stable reason code. */
    public static final class VerificationException extends Exception {
        public VerificationException(String m) { super(m); }
    }

    private final List<X509Certificate> anchors;
    private final boolean disabled;
    private final java.util.function.Supplier<Instant> clock;

    public SbiJwtVerifier(List<X509Certificate> trustAnchors, java.util.function.Supplier<Instant> clock) {
        if (trustAnchors == null || trustAnchors.isEmpty()) {
            throw new IllegalArgumentException("at least one trust anchor is required");
        }
        this.anchors = Collections.unmodifiableList(new ArrayList<>(trustAnchors));
        this.disabled = false;
        this.clock = clock;
    }

    private SbiJwtVerifier() {
        this.anchors = Collections.emptyList();
        this.disabled = true;
        this.clock = Instant::now;
    }

    /** DEVELOPMENT ONLY: accepts any token. Never ship this in a production configuration. */
    public static SbiJwtVerifier disabledForDevelopment() { return new SbiJwtVerifier(); }

    public boolean isDisabled() { return disabled; }

    /**
     * Creates a verifier from PEM-encoded certificates string using system clock.
     * Useful for loading trust anchors directly from configuration or asset files.
     */
    public static SbiJwtVerifier fromPem(String pem) throws VerificationException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            java.util.Collection<? extends java.security.cert.Certificate> certs =
                    cf.generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            List<X509Certificate> x509List = new ArrayList<>();
            for (java.security.cert.Certificate c : certs) {
                if (c instanceof X509Certificate) {
                    x509List.add((X509Certificate) c);
                }
            }
            if (x509List.isEmpty()) {
                throw new VerificationException("NO_CERTIFICATE_IN_PEM");
            }
            return new SbiJwtVerifier(x509List, Instant::now);
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new VerificationException("BAD_CERTIFICATE: " + e.getMessage());
        }
    }

    public static X509Certificate parseCertificate(byte[] der) throws VerificationException {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (Exception e) {
            throw new VerificationException("BAD_CERTIFICATE");
        }
    }

    /** Verifies the compact JWT and returns the payload JSON. */
    public String verify(String jwt) throws VerificationException {
        if (jwt == null) throw new VerificationException("MALFORMED");
        String[] p = jwt.split("\\.", -1);
        if (p.length != 3) throw new VerificationException("MALFORMED");
        String payload;
        Map<String, Object> header;
        try {
            payload = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
            header = MiniJson.parseObject(new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new VerificationException("MALFORMED");
        }
        if (disabled) return payload;

        if (!"RS256".equals(header.get("alg"))) throw new VerificationException("UNSUPPORTED_ALG");
        X509Certificate leaf = leafFromHeader(header);
        Instant now = clock.get();
        try {
            leaf.checkValidity(Date.from(now));
        } catch (Exception e) {
            throw new VerificationException("CERT_EXPIRED_OR_NOT_YET_VALID");
        }
        if (!isTrusted(leaf, now)) throw new VerificationException("UNTRUSTED_CERT");
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(leaf.getPublicKey());
            s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(p[2]))) throw new VerificationException("BAD_SIGNATURE");
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new VerificationException("BAD_SIGNATURE");
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static X509Certificate leafFromHeader(Map<String, Object> header) throws VerificationException {
        Object x5c = header.get("x5c");
        String first;
        if (x5c instanceof List && !((List<Object>) x5c).isEmpty()) first = String.valueOf(((List<Object>) x5c).get(0));
        else if (x5c instanceof String) first = (String) x5c;
        else throw new VerificationException("NO_CERTIFICATE");
        try {
            return parseCertificate(Base64.getDecoder().decode(first));
        } catch (IllegalArgumentException e) {
            throw new VerificationException("BAD_CERTIFICATE");
        }
    }

    private boolean isTrusted(X509Certificate leaf, Instant now) {
        for (X509Certificate a : anchors) {
            try {
                if (a.equals(leaf)) return true;
                a.checkValidity(Date.from(now));
                PublicKey k = a.getPublicKey();
                leaf.verify(k);
                return true;
            } catch (Exception ignored) {
                // try next anchor
            }
        }
        return false;
    }
}
