package liveness.android;

import liveness.core.FaceFrame;
import liveness.core.FrameQuality;
import liveness.device.DeviceException;
import liveness.engine.SameFaceChecker;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static liveness.android.AndroidTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SbiIntentBridgeTest {

    private static final String TX = "sess-1:nonce-abc";
    private static final SbiJwtVerifier DEV = SbiJwtVerifier.disabledForDevelopment();
    private static final SbiDevice DEVICE = new SbiDevice("com.sbi.face", APP_ID, "dev-id-1", "dev-001", "Ready", "Registration", "0.9.5");

    private static SbiIntentLauncher.ActivityResult ok(byte[] json) {
        return new SbiIntentLauncher.ActivityResult(SbiIntentLauncher.ActivityResult.RESULT_OK, json);
    }

    private static byte[] rgb4x4(int r, int g, int b) {
        byte[] a = new byte[4 * 4 * 3];
        for (int i = 0; i < a.length; i += 3) { a[i] = (byte) r; a[i + 1] = (byte) g; a[i + 2] = (byte) b; }
        return a;
    }

    private static FaceFrame hostFrame(long ts, int r, int g, int b) {
        return new FaceFrame(FrameQuality.GOOD, "camerax:c", 1, ts, 1, 4, 4, rgb4x4(r, g, b),
                0, 0, 4, 4, 0, 0, 0, 1, 200.0, 128.0, null, 7L);
    }

    private static String txOf(SbiIntentRequest req) {
        return (String) MiniJson.parseObject(req.inputString()).get("transactionId");
    }

    // ---------------- request building (per MOSIP Android SBI spec) ----------------

    @Test
    void discoveryRequestUsesIoSbiDeviceActionAndTypeFace() {
        SbiIntentRequest r = SbiIntentBridge.buildDiscoveryRequest("com.sbi.face");
        assertEquals("io.sbi.device", r.action());
        assertEquals("{\"type\":\"Face\"}", r.inputString());
        assertFalse(r.grantReadUri());
    }

    @Test
    void infoAndCaptureActionsArePrefixedWithTheAppId() {
        assertEquals("com.sbi.face.Info", SbiIntentBridge.buildInfoRequest(DEVICE).action());
        SbiIntentRequest r = SbiIntentBridge.buildCaptureRequest(DEVICE, "T0123456789", "Staging", "Registration",
                null, 40, 15000L, Instant.parse("2026-10-19T10:00:00.789Z"));
        assertEquals("com.sbi.face.rCapture", r.action());
        assertEquals("com.sbi.face", r.targetPackage());
        assertTrue(r.grantReadUri(), "spec: FLAG_GRANT_READ_URI_PERMISSION for rCapture");
    }

    @Test
    void captureRequestBodyFollowsTheSpec() {
        SbiIntentRequest r = SbiIntentBridge.buildCaptureRequest(DEVICE, "T0123456789", "Staging", "Registration",
                null, 40, 15000L, Instant.parse("2026-10-19T10:00:00.789Z"));
        Map<String, Object> in = MiniJson.parseObject(r.inputString());
        assertEquals("T0123456789", in.get("transactionId"));
        assertEquals("Staging", in.get("env"));
        assertEquals("Registration", in.get("purpose"));
        assertEquals("0.9.5", in.get("specVersion"));
        assertEquals(15000L, in.get("timeout"));
        assertEquals("2026-10-19T10:00:00Z", in.get("captureTime"), "ISO yyyy-mm-ddTHH:MM:ssZ, no fraction");
        Map<?, ?> bio = (Map<?, ?>) ((java.util.List<?>) in.get("bio")).get(0);
        assertEquals("Face", bio.get("type"));
        assertEquals(1L, bio.get("count"));
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855", bio.get("previousHash"),
                "first capture: SHA-256 of the empty string, upper-case hex");
        assertEquals("dev-id-1", bio.get("deviceId"));
    }

    @Test
    void transactionIdMustSatisfyTheSpecPattern() {
        for (String bad : new String[]{null, "", "abc", "has:colon-123", "has space 123", "x".repeat(51), "sess-1:nonce-abc"}) {
            assertThrows(IllegalArgumentException.class, () -> SbiIntentBridge.buildCaptureRequest(
                    DEVICE, bad, "e", "p", null, 40, 1000, Instant.now()), "id: " + bad);
        }
        assertTrue(SbiIntentBridge.isValidTransactionId("abcd"));
        assertTrue(SbiIntentBridge.isValidTransactionId("A-b-1"));
        assertTrue(SbiIntentBridge.isValidTransactionId("x".repeat(50)));
    }

    @Test
    void wireTransactionIdIsSpecCompliantDeterministicAndNonceDependent() {
        String a = SbiIntentDeviceAdapter.wireTransactionId("sess-1:nonce-abc");
        assertTrue(SbiIntentBridge.isValidTransactionId(a), a);
        assertEquals(a, SbiIntentDeviceAdapter.wireTransactionId("sess-1:nonce-abc"));
        assertNotEquals(a, SbiIntentDeviceAdapter.wireTransactionId("sess-1:nonce-abd"));
        // The real engine id (UUID nonce) is far longer than 50 chars and contains ':'.
        String real = "0b1d3e1e-6a0e-4f4d-9a39-0e2a7b6f1c11:5f0d6a4e-0d5b-4d57-8c4f-6e1d2f0a9b77";
        assertFalse(SbiIntentBridge.isValidTransactionId(real));
        assertTrue(SbiIntentBridge.isValidTransactionId(SbiIntentDeviceAdapter.wireTransactionId(real)));
    }

    // ---------------- discovery / info ----------------

    @Test
    void discoveryFailsWithoutSbiApp() {
        FakeLauncher l = new FakeLauncher();
        l.packages = Collections.emptyList();
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.discover(l, 500));
        assertTrue(e.getMessage().startsWith("SBI_NOT_INSTALLED"));
    }

    @Test
    void discoveryReturnsReadyDeviceWithAppIdFromCallbackId() throws Exception {
        FakeLauncher l = new FakeLauncher();
        SbiDevice d = SbiIntentBridge.discover(l, 500);
        assertEquals(APP_ID, d.appId);
        assertEquals("com.sbi.face", d.packageName);
        assertEquals("0.9.5", d.specVersion);
    }

    @Test
    void discoverySkipsUnresponsivePackages() throws Exception {
        FakeLauncher l = new FakeLauncher();
        l.packages = java.util.Arrays.asList("com.dead", "com.sbi.face");
        l.discoveryTimeouts.add("com.dead");
        assertEquals("com.sbi.face", SbiIntentBridge.discover(l, 500).packageName);
    }

    @Test
    void busyOrNotReadyDeviceIsNotSelected() {
        FakeLauncher l = new FakeLauncher();
        l.deviceStatus = "Busy";
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.discover(l, 500));
        assertTrue(e.getMessage().contains("Busy"));
    }

    @Test
    void discoveryEntryWithoutCallbackIdIsIgnored() throws Exception {
        assertTrue(SbiIntentBridge.parseDiscovery("p",
                "[{\"deviceStatus\":\"Ready\",\"callbackId\":\"\"}]".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    void infoRequiresReadyAndRegistrationPurpose() throws Exception {
        FakeLauncher l = new FakeLauncher();
        assertEquals("Registration", SbiIntentBridge.info(l, DEVICE, DEV, 500).purpose);
        l.infoPurpose = "Auth";
        assertTrue(assertThrows(DeviceException.class, () -> SbiIntentBridge.info(l, DEVICE, DEV, 500))
                .getMessage().startsWith("SBI_WRONG_PURPOSE"));
        l.infoPurpose = "Registration";
        l.infoStatus = "Not Ready";
        assertTrue(assertThrows(DeviceException.class, () -> SbiIntentBridge.info(l, DEVICE, DEV, 500))
                .getMessage().startsWith("SBI_DEVICE_NOT_READY"));
    }

    // ---------------- response parsing ----------------

    @Test
    void parsesValidCaptureResponseWithBase64UrlBioValue() throws Exception {
        byte[] img = {(byte) 0xFB, (byte) 0xFF, 3, 4, 5}; // bytes that differ between base64 and base64url
        SbiCaptureResult r = SbiIntentBridge.parseCaptureResponse(
                captureResponse(TX, "2026-10-19T10:00:05Z", img, "0"), DEV);
        assertEquals(TX, r.transactionId());
        assertArrayEquals(img, r.image());
        assertEquals(Instant.parse("2026-10-19T10:00:05Z").toEpochMilli(), r.timestampMs());
        assertEquals("dev-001", r.deviceCode());
    }

    @Test
    void sbiErrorCodesBecomeDeviceExceptions() {
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.parseCaptureResponse(
                captureResponse(TX, "2026-10-19T10:00:05Z", new byte[]{1}, "112"), DEV));
        assertTrue(e.getMessage().startsWith("SBI_ERROR_112"));
    }

    @Test
    void malformedResponsesAreRejectedNotCrashing() {
        String[] bad = {
                "", "not json", "{}", "{\"biometrics\":[]}",
                "{\"biometrics\":[{\"data\":\"a.b\"}]}",
                "{\"biometrics\":[{\"data\":\"" + jwt(new java.util.LinkedHashMap<String, Object>()) + "\"}]}",
        };
        for (String b : bad) {
            DeviceException e = assertThrows(DeviceException.class,
                    () -> SbiIntentBridge.parseCaptureResponse(b.getBytes(StandardCharsets.UTF_8), DEV), "input: " + b);
            assertTrue(e.getMessage().startsWith("SBI_BAD_RESPONSE"), e.getMessage());
        }
        assertThrows(DeviceException.class, () -> SbiIntentBridge.parseCaptureResponse(null, DEV));
    }

    @Test
    void nonFaceBioTypeAndBadTimestampAreRejected() {
        Map<String, Object> p = capturePayload(TX, "2026-10-19T10:00:05Z", new byte[]{1});
        p.put("bioType", "Finger");
        assertTrue(assertThrows(DeviceException.class, () -> SbiIntentBridge.parseCaptureResponse(
                captureResponseFor(jwt(p), "0"), DEV)).getMessage().startsWith("SBI_BAD_RESPONSE"));
        assertTrue(assertThrows(DeviceException.class, () -> SbiIntentBridge.parseCaptureResponse(
                captureResponse(TX, "yesterday", new byte[]{1}, "0"), DEV)).getMessage().startsWith("SBI_BAD_RESPONSE"));
    }

    @Test
    void enforcingVerifierRejectsUnsignedTokens() throws Exception {
        SbiJwtVerifier strict = new SbiJwtVerifier(Collections.singletonList(
                SbiJwtVerifier.parseCertificate(java.util.Base64.getDecoder().decode(SbiJwtVerifierTest.ROOT_CERT))),
                () -> Instant.parse("2027-01-01T00:00:00Z"));
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.parseCaptureResponse(
                captureResponse(TX, "2026-10-19T10:00:05Z", new byte[]{1}, "0"), strict));
        assertTrue(e.getMessage().startsWith("SBI_SIGNATURE_INVALID"), e.getMessage());
    }

    // ---------------- launching / timeout ----------------

    @Test
    void timeoutBecomesSbiTimeout() {
        FakeLauncher l = new FakeLauncher(); // captureHandler null => timeout
        SbiIntentRequest req = SbiIntentBridge.buildCaptureRequest(DEVICE, "T0123456789", "e", "p", null, 40, 1000, Instant.now());
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.capture(l, req, DEV, 1000));
        assertTrue(e.getMessage().startsWith("SBI_TIMEOUT"));
        assertInstanceOf(SbiIntentLauncher.TimeoutException.class, e.getCause());
    }

    @Test
    void userCancelBecomesSbiCancelled() {
        FakeLauncher l = new FakeLauncher();
        l.captureHandler = r -> new SbiIntentLauncher.ActivityResult(0, null);
        SbiIntentRequest req = SbiIntentBridge.buildCaptureRequest(DEVICE, "T0123456789", "e", "p", null, 40, 1000, Instant.now());
        DeviceException e = assertThrows(DeviceException.class, () -> SbiIntentBridge.capture(l, req, DEV, 1000));
        assertTrue(e.getMessage().startsWith("SBI_CANCELLED"));
    }

    // ---------------- adapter: capture + cross-stream binding ----------------

    private SbiIntentDeviceAdapter adapter(FakeLauncher l, FaceFrame host, double sameFaceThreshold, long maxGapMs)
            throws DeviceException {
        CrossStreamBinder binder = new CrossStreamBinder(new SameFaceChecker(sameFaceThreshold),
                new FirstPixelEmbedder(), maxGapMs);
        SbiIntentDeviceAdapter a = new SbiIntentDeviceAdapter(l, binder, () -> host,
                bytes -> new SbiIntentDeviceAdapter.RgbDecoder.Decoded(bytes, 4, 4), DEV, "Developer", "Registration", 5000);
        a.open();
        return a;
    }

    /** SBI that echoes the wire transactionId it was sent (or an override). */
    private static FakeLauncher echoing(final byte[] image, final long tsMs, final String txOverride) {
        FakeLauncher l = new FakeLauncher();
        l.captureHandler = req -> ok(captureResponse(txOverride != null ? txOverride : txOf(req),
                Instant.ofEpochMilli(tsMs).toString().replaceAll("\\.\\d+Z$", "Z"), image, "0"));
        return l;
    }

    @Test
    void finalCaptureSucceedsWhenNonceGapAndFaceAllMatch() throws Exception {
        long now = System.currentTimeMillis();
        byte[] img = rgb4x4(200, 20, 20);
        SbiIntentDeviceAdapter a = adapter(echoing(img, now, null), hostFrame(now - 800, 200, 20, 20), 0.1, 5000);
        FaceFrame f = a.captureFinal(TX);
        assertArrayEquals(img, f.pixelData(), "the attested bytes are the SBI image (T1), not the host frame");
        assertEquals(TX, a.lastReturnedTransactionId(), "engine sees its own bound id when the wire id is echoed");
        assertTrue(f.signature().startsWith("sbi:"));
        assertEquals("HOST_CAMERA+SBI_RCAPTURE_INTENT", a.captureMode());
    }

    @Test
    void requestSentToSbiCarriesTheSpecCompliantWireId() throws Exception {
        long now = System.currentTimeMillis();
        FakeLauncher l = echoing(rgb4x4(1, 2, 3), now, null);
        adapter(l, hostFrame(now, 1, 2, 3), 0.1, 5000).captureFinal(TX);
        SbiIntentRequest sent = l.launched.get(l.launched.size() - 1);
        assertEquals("com.sbi.face.rCapture", sent.action());
        assertEquals(SbiIntentDeviceAdapter.wireTransactionId(TX), txOf(sent));
        assertTrue(SbiIntentBridge.isValidTransactionId(txOf(sent)));
    }

    @Test
    void swappedTransactionIdIsRejected() throws Exception {
        long now = System.currentTimeMillis();
        SbiIntentDeviceAdapter a = adapter(echoing(rgb4x4(200, 20, 20), now, "other-session-1234"),
                hostFrame(now, 200, 20, 20), 0.1, 5000);
        DeviceException e = assertThrows(DeviceException.class, () -> a.captureFinal(TX));
        assertEquals("BINDING_FAILURE: NONCE_MISMATCH", e.getMessage());
        assertNull(a.lastReturnedTransactionId(), "a rejected capture must not expose a returned id");
    }

    @Test
    void wireIdOfADifferentSessionIsRejected() throws Exception {
        long now = System.currentTimeMillis();
        String otherWire = SbiIntentDeviceAdapter.wireTransactionId("sess-1:other-nonce");
        SbiIntentDeviceAdapter a = adapter(echoing(rgb4x4(200, 20, 20), now, otherWire),
                hostFrame(now, 200, 20, 20), 0.1, 5000);
        assertEquals("BINDING_FAILURE: NONCE_MISMATCH", assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage());
    }

    @Test
    void staleHostFrameExceedsTimeGap() throws Exception {
        long now = System.currentTimeMillis();
        SbiIntentDeviceAdapter a = adapter(echoing(rgb4x4(200, 20, 20), now, null),
                hostFrame(now - 60_000, 200, 20, 20), 0.1, 5000);
        assertEquals("BINDING_FAILURE: TIME_GAP_EXCEEDED", assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage());
    }

    @Test
    void differentPersonInFinalCaptureIsRejected() throws Exception {
        long now = System.currentTimeMillis();
        SbiIntentDeviceAdapter a = adapter(echoing(rgb4x4(20, 200, 20), now, null), hostFrame(now, 200, 20, 20), 0.1, 5000);
        assertEquals("BINDING_FAILURE: DIFFERENT_FACE", assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage());
    }

    @Test
    void missingHostFrameFailsClosed() throws Exception {
        long now = System.currentTimeMillis();
        SbiIntentDeviceAdapter a = adapter(echoing(rgb4x4(200, 20, 20), now, null), null, 0.1, 5000);
        assertEquals("BINDING_FAILURE: NO_HOST_FRAME", assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage());
    }

    @Test
    void undecodableImageFailsClosed() throws Exception {
        long now = System.currentTimeMillis();
        CrossStreamBinder binder = new CrossStreamBinder(new SameFaceChecker(0.1), new FirstPixelEmbedder(), 5000);
        SbiIntentDeviceAdapter a = new SbiIntentDeviceAdapter(echoing(rgb4x4(1, 2, 3), now, null), binder,
                () -> hostFrame(now, 1, 2, 3), bytes -> { throw new IllegalStateException("codec"); },
                DEV, "Developer", "Registration", 5000);
        a.open();
        assertEquals("BINDING_FAILURE: EMBEDDING_FAILED", assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage());
    }

    @Test
    void transactionIdWithoutNonceIsRefusedBeforeLaunching() throws Exception {
        long now = System.currentTimeMillis();
        FakeLauncher l = echoing(rgb4x4(1, 2, 3), now, null);
        SbiIntentDeviceAdapter a = adapter(l, hostFrame(now, 1, 2, 3), 0.1, 5000);
        int before = l.launched.size();
        assertThrows(DeviceException.class, () -> a.captureFinal("no-nonce-here"));
        assertEquals(before, l.launched.size(), "SBI must not be launched without a nonce-bound id");
    }

    @Test
    void openRunsDiscoveryThenInfoAndStreamIsUnsupported() throws Exception {
        long now = System.currentTimeMillis();
        FakeLauncher l = echoing(rgb4x4(1, 2, 3), now, null);
        SbiIntentDeviceAdapter a = adapter(l, hostFrame(now, 1, 2, 3), 0.1, 5000);
        assertEquals("io.sbi.device", l.launched.get(0).action());
        assertEquals("com.sbi.face.Info", l.launched.get(1).action());
        assertTrue(assertThrows(DeviceException.class, a::captureFrame).getMessage().startsWith("SBI_STREAM_UNSUPPORTED"));
        a.close();
        assertFalse(a.isOpen());
        assertThrows(DeviceException.class, () -> a.captureFinal(TX));
    }

    @Test
    void sbiTimeoutDuringFinalCapturePropagates() throws Exception {
        long now = System.currentTimeMillis();
        SbiIntentDeviceAdapter a = adapter(new FakeLauncher(), hostFrame(now, 1, 2, 3), 0.1, 5000);
        assertTrue(assertThrows(DeviceException.class, () -> a.captureFinal(TX)).getMessage().startsWith("SBI_TIMEOUT"));
    }
}
