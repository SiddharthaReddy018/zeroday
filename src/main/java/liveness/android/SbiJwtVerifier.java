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

    /** DEVELOPMENT ONLY: accepts any token. Never ship this in a production configuration. Guarded by StubPolicy. */
    public static SbiJwtVerifier disabledForDevelopment() {
        liveness.policy.StubPolicy.check(true, "SbiJwtVerifier.disabledForDevelopment()");
        return new SbiJwtVerifier();
    }

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
        List<X509Certificate> chain = chainFromHeader(header);
        X509Certificate leaf = chain.get(0);
        Instant now = clock.get();
        validateChain(chain, now);

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

        verifyNestedDigitalId(payload);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static List<X509Certificate> chainFromHeader(Map<String, Object> header) throws VerificationException {
        Object x5c = header.get("x5c");
        List<X509Certificate> chain = new ArrayList<>();
        if (x5c instanceof List) {
            for (Object o : (List<Object>) x5c) {
                if (o != null) {
                    chain.add(parseCertificate(Base64.getDecoder().decode(String.valueOf(o))));
                }
            }
        } else if (x5c instanceof String) {
            chain.add(parseCertificate(Base64.getDecoder().decode((String) x5c)));
        }
        if (chain.isEmpty()) {
            throw new VerificationException("NO_CERTIFICATE");
        }
        return chain;
    }

    private void validateChain(List<X509Certificate> chain, Instant now) throws VerificationException {
        Date nowDate = Date.from(now);
        for (X509Certificate cert : chain) {
            try {
                cert.checkValidity(nowDate);
            } catch (Exception e) {
                throw new VerificationException("CERT_EXPIRED_OR_NOT_YET_VALID");
            }
        }

        X509Certificate leaf = chain.get(0);
        boolean[] leafKeyUsage = leaf.getKeyUsage();
        if (leafKeyUsage != null) {
            // Bit 0 = digitalSignature, Bit 1 = nonRepudiation
            if (!leafKeyUsage[0] && !leafKeyUsage[1]) {
                throw new VerificationException("INVALID_KEY_USAGE");
            }
        }

        for (int i = 0; i < chain.size() - 1; i++) {
            X509Certificate child = chain.get(i);
            X509Certificate issuer = chain.get(i + 1);

            // BasicConstraints check: must be CA
            if (issuer.getBasicConstraints() == -1) {
                throw new VerificationException("NON_CA_INTERMEDIATE");
            }
            boolean[] issuerUsage = issuer.getKeyUsage();
            if (issuerUsage != null && !issuerUsage[5]) { // keyCertSign
                throw new VerificationException("NON_CA_INTERMEDIATE");
            }
            try {
                child.verify(issuer.getPublicKey());
            } catch (Exception e) {
                throw new VerificationException("CHAIN_VERIFICATION_FAILED");
            }
        }

        X509Certificate top = chain.get(chain.size() - 1);
        boolean trusted = false;
        for (X509Certificate anchor : anchors) {
            try {
                anchor.checkValidity(nowDate);
                if (top.equals(anchor)) {
                    trusted = true;
                    break;
                }
                if (anchor.getBasicConstraints() == -1) {
                    continue;
                }
                top.verify(anchor.getPublicKey());
                trusted = true;
                break;
            } catch (Exception ignored) {
            }
        }

        if (!trusted) {
            throw new VerificationException("UNTRUSTED_CERT");
        }
    }

    private void verifyNestedDigitalId(String payloadJson) throws VerificationException {
        try {
            Map<String, Object> map = MiniJson.parseObject(payloadJson);
            Object did = map.get("digitalId");
            if (did instanceof String) {
                String didStr = (String) did;
                if (didStr.split("\\.", -1).length == 3) {
                    verify(didStr);
                }
            }
        } catch (VerificationException e) {
            throw new VerificationException("NESTED_DIGITAL_ID_INVALID: " + e.getMessage());
        } catch (Exception ignored) {
        }
    }
}
