package liveness.android;

import liveness.device.DeviceException;
import liveness.util.Sha256;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds SBI intents, launches them via {@link SbiIntentLauncher} and parses responses, following the
 * "Android SBI Specification" section of the MOSIP MDS spec (docs.mosip.io 1.1.5, draft 0.9):
 * <ul>
 *   <li>Discovery: action {@code io.sbi.device}; the discovery response carries {@code callbackId} = appId.</li>
 *   <li>Info: action {@code <appId>.Info}. rCapture: action {@code <appId>.rCapture}, with
 *       FLAG_GRANT_READ_URI_PERMISSION.</li>
 *   <li>Request JSON is sent as a byte[] extra {@code input}; the response arrives as {@code response}
 *       (byte[] or a content:// URI string; the glue resolves it to bytes).</li>
 *   <li>transactionId: 4..50 chars, letters/digits/hyphen only (error 112 otherwise).</li>
 * </ul>
 * Pure Java so it is unit-testable on a JVM. Only drafts of the Android section exist; confirm against the SBI
 * app you integrate with.
 */
public final class SbiIntentBridge {

    public static final String ACTION_DISCOVER = "io.sbi.device";
    public static final String DEFAULT_SPEC_VERSION = "0.9.5";
    private static final Pattern TX_ID = Pattern.compile("[A-Za-z0-9-]{4,50}");

    private SbiIntentBridge() {}

    public static String infoAction(String appId) { return appId + ".Info"; }
    public static String rcaptureAction(String appId) { return appId + ".rCapture"; }

    public static boolean isValidTransactionId(String t) { return t != null && TX_ID.matcher(t).matches(); }

    // ---------------- request building ----------------

    public static SbiIntentRequest buildDiscoveryRequest(String pkg) {
        return new SbiIntentRequest(ACTION_DISCOVER, pkg, utf8("{\"type\":\"Face\"}"), false);
    }

    public static SbiIntentRequest buildInfoRequest(SbiDevice d) {
        return new SbiIntentRequest(infoAction(d.appId), d.packageName, new byte[0], false);
    }

    public static SbiIntentRequest buildCaptureRequest(SbiDevice d, String transactionId, String env, String purpose,
                                                       String specVersion, int requestedScore, long timeoutMs,
                                                       Instant captureTime) {
        if (!isValidTransactionId(transactionId)) {
            throw new IllegalArgumentException("transactionId must match [A-Za-z0-9-]{4,50}");
        }
        Map<String, Object> bio = new LinkedHashMap<>();
        bio.put("type", "Face");
        bio.put("count", 1L);
        bio.put("exception", new ArrayList<Object>());
        bio.put("requestedScore", (long) requestedScore);
        bio.put("deviceId", d.deviceId == null ? "" : d.deviceId);
        bio.put("deviceSubId", "0");
        bio.put("previousHash", Sha256.hex(Sha256.digest(new byte[0])).toUpperCase(Locale.ROOT)); // SHA-256 of "" (spec)
        List<Object> bios = new ArrayList<>();
        bios.add(bio);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("env", env == null ? "Developer" : env);
        body.put("purpose", purpose == null ? "Registration" : purpose);
        body.put("specVersion", specVersion != null ? specVersion : (d.specVersion != null ? d.specVersion : DEFAULT_SPEC_VERSION));
        body.put("timeout", timeoutMs);
        body.put("captureTime", captureTime.truncatedTo(ChronoUnit.SECONDS).toString()); // yyyy-mm-ddTHH:MM:ssZ
        body.put("transactionId", transactionId);
        body.put("bio", bios);
        body.put("customOpts", new LinkedHashMap<String, Object>());
        return new SbiIntentRequest(rcaptureAction(d.appId), d.packageName, utf8(MiniJson.write(body)), true);
    }

    // ---------------- discovery + info ----------------

    /** Discovers a Ready face device across installed SBI apps. */
    public static SbiDevice discover(SbiIntentLauncher launcher, long timeoutMs) throws DeviceException {
        List<String> pkgs = launcher.resolveSbiPackages();
        if (pkgs == null || pkgs.isEmpty()) throw new DeviceException("SBI_NOT_INSTALLED: no app handles " + ACTION_DISCOVER);
        String lastProblem = "no SBI app answered discovery";
        for (String pkg : pkgs) {
            try {
                SbiIntentLauncher.ActivityResult r = launcher.launch(buildDiscoveryRequest(pkg), timeoutMs);
                if (r == null || r.resultCode != SbiIntentLauncher.ActivityResult.RESULT_OK || isEmpty(r.response)) continue;
                for (SbiDevice d : parseDiscovery(pkg, r.response)) {
                    if ("Ready".equalsIgnoreCase(d.status)) return d;
                    lastProblem = "device status " + d.status;
                }
            } catch (SbiIntentLauncher.TimeoutException e) {
                lastProblem = "timeout from " + pkg;
            } catch (DeviceException e) {
                lastProblem = e.getMessage();
            }
        }
        throw new DeviceException("SBI_DISCOVERY_FAILED: " + lastProblem);
    }

    @SuppressWarnings("unchecked")
    static List<SbiDevice> parseDiscovery(String pkg, byte[] response) throws DeviceException {
        List<SbiDevice> out = new ArrayList<>();
        try {
            Object root = MiniJson.parse(new String(response, StandardCharsets.UTF_8));
            if (!(root instanceof List)) throw new DeviceException("SBI_BAD_RESPONSE: discovery is not an array");
            for (Object o : (List<Object>) root) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                if (m.get("error") instanceof Map && !"0".equals(String.valueOf(((Map<?, ?>) m.get("error")).get("errorCode")))) continue;
                String appId = str(m.get("callbackId"));
                if (appId == null || appId.isEmpty()) continue; // spec: cannot be empty on Android
                String spec = null;
                if (m.get("specVersion") instanceof List && !((List<Object>) m.get("specVersion")).isEmpty()) {
                    spec = str(((List<Object>) m.get("specVersion")).get(0));
                }
                out.add(new SbiDevice(pkg, appId, str(m.get("deviceId")), str(m.get("deviceCode")),
                        str(m.get("deviceStatus")), str(m.get("purpose")), spec));
            }
        } catch (MiniJson.JsonException e) {
            throw new DeviceException("SBI_BAD_RESPONSE: " + e.getMessage(), e);
        }
        return out;
    }

    /** Device Info: confirms status Ready and purpose Registration; verifies the deviceInfo JWT when it is signed. */
    @SuppressWarnings("unchecked")
    public static SbiDevice info(SbiIntentLauncher launcher, SbiDevice d, SbiJwtVerifier verifier, long timeoutMs)
            throws DeviceException {
        SbiIntentLauncher.ActivityResult r;
        try {
            r = launcher.launch(buildInfoRequest(d), timeoutMs);
        } catch (SbiIntentLauncher.TimeoutException e) {
            throw new DeviceException("SBI_TIMEOUT: no Info result within " + timeoutMs + " ms", e);
        }
        if (r == null || r.resultCode != SbiIntentLauncher.ActivityResult.RESULT_OK || isEmpty(r.response)) {
            throw new DeviceException("SBI_INFO_FAILED");
        }
        try {
            Object root = MiniJson.parse(new String(r.response, StandardCharsets.UTF_8));
            if (!(root instanceof List) || ((List<Object>) root).isEmpty()) throw new DeviceException("SBI_BAD_RESPONSE: empty Info");
            Map<String, Object> e = (Map<String, Object>) ((List<Object>) root).get(0);
            String info = str(e.get("deviceInfo"));
            if (info == null) throw new DeviceException("SBI_BAD_RESPONSE: no deviceInfo");
            String payload;
            if (info.split("\\.", -1).length == 3) {
                payload = verifier.verify(info);
            } else { // unsigned (device not registered): never acceptable once a verifier is enforcing
                if (!verifier.isDisabled()) throw new DeviceException("SBI_UNSIGNED_DEVICE_INFO");
                payload = new String(Base64.getUrlDecoder().decode(info), StandardCharsets.UTF_8);
            }
            Map<String, Object> m = MiniJson.parseObject(payload);
            SbiDevice updated = d.with(str(m.get("deviceStatus")), str(m.get("purpose")), str(m.get("deviceId")));
            if (!"Ready".equalsIgnoreCase(updated.status)) throw new DeviceException("SBI_DEVICE_NOT_READY: " + updated.status);
            if (!"Registration".equalsIgnoreCase(updated.purpose)) throw new DeviceException("SBI_WRONG_PURPOSE: " + updated.purpose);
            return updated;
        } catch (SbiJwtVerifier.VerificationException e) {
            throw new DeviceException("SBI_SIGNATURE_INVALID: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new DeviceException("SBI_BAD_RESPONSE: " + e.getMessage(), e);
        }
    }

    // ---------------- capture ----------------

    public static SbiCaptureResult capture(SbiIntentLauncher launcher, SbiIntentRequest request,
                                           SbiJwtVerifier verifier, long timeoutMs) throws DeviceException {
        SbiIntentLauncher.ActivityResult r;
        try {
            r = launcher.launch(request, timeoutMs);
        } catch (SbiIntentLauncher.TimeoutException e) {
            throw new DeviceException("SBI_TIMEOUT: no rCapture result within " + timeoutMs + " ms", e);
        }
        if (r == null || r.resultCode != SbiIntentLauncher.ActivityResult.RESULT_OK) {
            throw new DeviceException("SBI_CANCELLED: resultCode=" + (r == null ? "null" : String.valueOf(r.resultCode)));
        }
        return parseCaptureResponse(r.response, verifier);
    }

    @SuppressWarnings("unchecked")
    public static SbiCaptureResult parseCaptureResponse(byte[] response, SbiJwtVerifier verifier) throws DeviceException {
        if (isEmpty(response)) throw new DeviceException("SBI_BAD_RESPONSE: empty response");
        try {
            Map<String, Object> root = MiniJson.parseObject(new String(response, StandardCharsets.UTF_8));
            checkError(root.get("error"));
            Object bios = root.get("biometrics");
            if (!(bios instanceof List) || ((List<Object>) bios).isEmpty()) throw new DeviceException("SBI_BAD_RESPONSE: no biometrics[]");
            Map<String, Object> first = (Map<String, Object>) ((List<Object>) bios).get(0);
            checkError(first.get("error"));
            Object data = first.get("data");
            if (!(data instanceof String)) throw new DeviceException("SBI_BAD_RESPONSE: no data JWT");
            String jwt = (String) data;
            if (jwt.split("\\.", -1).length != 3) throw new DeviceException("SBI_BAD_RESPONSE: data is not a JWT");

            String payloadJson = verifier.verify(jwt); // throws VerificationException on any failure
            Map<String, Object> payload = MiniJson.parseObject(payloadJson);
            if (!"Face".equalsIgnoreCase(str(payload.get("bioType")))) throw new DeviceException("SBI_BAD_RESPONSE: bioType is not Face");
            String bioValue = str(payload.get("bioValue"));
            String txId = str(payload.get("transactionId"));
            String ts = str(payload.get("timestamp"));
            if (bioValue == null || bioValue.isEmpty()) throw new DeviceException("SBI_BAD_RESPONSE: no bioValue");
            if (txId == null) throw new DeviceException("SBI_BAD_RESPONSE: no transactionId");
            if (ts == null) throw new DeviceException("SBI_BAD_RESPONSE: no timestamp");
            return new SbiCaptureResult(txId, Instant.parse(ts).toEpochMilli(), decodeBase64Any(bioValue), jwt, str(payload.get("deviceCode")));
        } catch (SbiJwtVerifier.VerificationException e) {
            throw new DeviceException("SBI_SIGNATURE_INVALID: " + e.getMessage(), e);
        } catch (DeviceException e) {
            throw e;
        } catch (RuntimeException e) { // JsonException, DateTimeParseException, IllegalArgumentException
            throw new DeviceException("SBI_BAD_RESPONSE: " + e.getMessage(), e);
        }
    }

    private static void checkError(Object err) throws DeviceException {
        if (!(err instanceof Map)) return;
        Object code = ((Map<?, ?>) err).get("errorCode");
        String c = code == null ? "0" : String.valueOf(code);
        if (!"0".equals(c)) {
            Object info = ((Map<?, ?>) err).get("errorInfo");
            throw new DeviceException("SBI_ERROR_" + c + ": " + (info == null ? "" : info));
        }
    }

    private static boolean isEmpty(byte[] b) { return b == null || b.length == 0; }
    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static byte[] decodeBase64Any(String s) {
        try {
            return Base64.getUrlDecoder().decode(s); // spec: base64url
        } catch (IllegalArgumentException e) {
            return Base64.getDecoder().decode(s);
        }
    }
}
