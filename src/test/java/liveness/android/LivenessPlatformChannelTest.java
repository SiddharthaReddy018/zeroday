package liveness.android;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.DeviceAdapter;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;
import liveness.ui.UiMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LivenessPlatformChannelTest {

    private final SchemaValidator schema = new SchemaValidator();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final List<Runnable> deferred = new ArrayList<>();
    private MiniFasNetScorer scorer;
    private LivenessService service;
    private MockDeviceAdapter.Scenario scenario = MockDeviceAdapter.Scenario.BONA_FIDE;

    @BeforeEach
    void setUp() {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
        scorer = new MiniFasNetScorer(null);
        scorer.setStubScore(85);
        SameFaceChecker sf = new SameFaceChecker(0.6);
        sf.setStubResult(true);
        service = new LivenessService(cfg, scorer, sf,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
    }

    private LivenessPlatformChannel channel(boolean runInline) {
        Supplier<DeviceAdapter> f = () -> new MockDeviceAdapter(scenario);
        LivenessPlatformChannel c = new LivenessPlatformChannel(service, f,
                runInline ? Runnable::run : deferred::add, () -> 1_700_000_000_000L);
        c.setEventSink(e -> events.add(roundTrip(e)));
        return c;
    }

    /** Serialize and re-parse exactly as the channel codec would, so we validate the wire form. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> roundTrip(Map<String, Object> e) {
        return (Map<String, Object>) MiniJson.parse(MiniJson.write(e));
    }

    private static final class Res implements LivenessPlatformChannel.MethodResult {
        Object value; String code; String msg; boolean done;
        @Override public void success(Object v) { value = v; done = true; }
        @Override public void error(String c, String m) { code = c; msg = m; done = true; }
    }

    private static Map<String, Object> args(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private Res call(LivenessPlatformChannel c, String method, Map<String, Object> a) {
        Res r = new Res();
        c.handleMethodCall(method, a, r);
        assertTrue(r.done, "every call must answer exactly once");
        return r;
    }

    private Map<String, Object> start() { return args("subjectId", "s-1", "workflow", "RESIDENT_REGISTRATION", "machineUserId", "m-1"); }

    private void assertAllSchemaValid() {
        assertFalse(events.isEmpty());
        for (Map<String, Object> e : events) {
            List<String> errs = schema.validate(e);
            assertTrue(errs.isEmpty(), "schema violations " + errs + " in " + MiniJson.write(e));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> e) { return (Map<String, Object>) e.get("data"); }

    private Map<String, Object> last(String type) {
        for (int i = events.size() - 1; i >= 0; i--) if (type.equals(events.get(i).get("eventType"))) return events.get(i);
        return null;
    }

    // ---------------- full run, wire format ----------------

    @Test
    void successfulSessionEmitsOnlySchemaValidEvents() {
        LivenessPlatformChannel c = channel(true);
        Res r = call(c, "startSession", start());
        assertNull(r.code);
        assertAllSchemaValid();

        String sid = (String) events.get(0).get("sessionId");
        for (Map<String, Object> e : events) assertEquals(sid, e.get("sessionId"), "one session id per session");

        Map<String, Object> done = last("SESSION_COMPLETE");
        assertNotNull(done);
        assertEquals("PASS", data(done).get("verdict"));
        assertEquals("SUCCESS", data(done).get("currentState"));
        @SuppressWarnings("unchecked") Map<String, Object> at = (Map<String, Object>) data(done).get("attestation");
        assertNotNull(at, "attestation is delivered on PASS");
        assertEquals("T1", at.get("deviceTier"));
        assertNotNull(at.get("signature"));
        assertNotNull(at.get("imageSha256"));
    }

    @Test
    void stateChangesAreDeduplicatedAndChained() {
        LivenessPlatformChannel c = channel(true);
        call(c, "startSession", start());
        String prev = null;
        for (Map<String, Object> e : events) {
            if (!"STATE_CHANGE".equals(e.get("eventType"))) continue;
            String cur = (String) data(e).get("currentState");
            assertNotEquals(prev, cur, "no repeated state events");
            if (prev != null) assertEquals(prev, data(e).get("previousState"));
            prev = cur;
        }
        assertNotNull(prev);
    }

    @Test
    void progressStaysWithinZeroToOneAndNeverRepeats() {
        LivenessPlatformChannel c = channel(true);
        call(c, "startSession", start());
        double lastP = -1;
        for (Map<String, Object> e : events) {
            if (!"CAPTURE_PROGRESS".equals(e.get("eventType"))) continue;
            double p = ((Number) data(e).get("captureProgress")).doubleValue();
            assertTrue(p >= 0.0 && p <= 1.0);
            assertNotEquals(lastP, p);
            lastP = p;
        }
        assertTrue(lastP > 0);
    }

    @Test
    void attackVerdictCarriesNoAttestation() {
        scenario = MockDeviceAdapter.Scenario.PRINT;
        scorer.setStubScore(5);
        LivenessPlatformChannel c = channel(true);
        call(c, "startSession", start());
        assertAllSchemaValid();
        Map<String, Object> done = last("SESSION_COMPLETE");
        assertNotNull(done);
        assertNotEquals("PASS", data(done).get("verdict"));
        assertFalse(data(done).containsKey("attestation"), "attestations exist only on SUCCESS");
    }

    @Test
    void deviceFailureMapsToSchemaVerdictAndStatusEvent() {
        scenario = MockDeviceAdapter.Scenario.DISCONNECT;
        LivenessPlatformChannel c = channel(true);
        call(c, "startSession", start());
        assertAllSchemaValid();
        assertEquals("DEVICE_ERROR", data(last("SESSION_COMPLETE")).get("verdict"));
        assertEquals(Boolean.FALSE, data(last("DEVICE_STATUS")).get("deviceConnected"));
    }

    // ---------------- direct callback serialization ----------------

    @Test
    void challengeAndErrorEventsMatchSchema() {
        LivenessPlatformChannel c = channel(true);
        c.onChallengePrompt("TURN_LEFT", 5000);
        c.onChallengePrompt("BLINK", 4000);
        c.onError("CAMERA_PERMISSION_DENIED", "Camera permission is required");
        assertAllSchemaValid();
        Map<String, Object> first = events.get(0);
        assertEquals("CHALLENGE_ISSUED", first.get("eventType"));
        assertEquals("TURN_LEFT", data(first).get("challengeAction"));
        assertEquals(1L, ((Number) data(first).get("challengeIndex")).longValue());
        assertEquals(5000L, ((Number) data(first).get("challengeTimeoutMs")).longValue());
        assertEquals(2L, ((Number) data(events.get(1)).get("challengeIndex")).longValue());
        assertEquals("ERROR", events.get(2).get("eventType"));
        assertEquals("CAMERA_PERMISSION_DENIED", data(events.get(2)).get("errorCode"));
    }

    @Test
    void schemaValidatorActuallyRejectsBadEvents() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("eventType", "PREVIEW_FRAME");
        bad.put("timestamp", 1L);
        assertFalse(schema.validate(bad).isEmpty(), "unknown eventType and missing sessionId must fail");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("currentState", "NOT_A_STATE");
        d.put("captureProgress", 1.5);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("eventType", "STATE_CHANGE");
        e.put("timestamp", 1L);
        e.put("sessionId", "x");
        e.put("data", d);
        assertEquals(2, schema.validate(e).size());
    }

    // ---------------- locale ----------------

    @Test
    void localeSelectsMessagesAndRtl() {
        LivenessPlatformChannel c = channel(true);
        Map<String, Object> a = start();
        a.put("locale", "ar");
        call(c, "startSession", a);
        Map<String, Object> ev = null;
        for (Map<String, Object> e : events) if ("STATE_CHANGE".equals(e.get("eventType"))) { ev = e; break; }
        assertNotNull(ev);
        String state = (String) data(ev).get("currentState");
        assertEquals(UiMessages.get("ar", state), data(ev).get("message"));
        assertEquals(Boolean.TRUE, data(ev).get("rtl"));
    }

    // ---------------- method contract ----------------

    @Test
    void startSessionValidatesArguments() {
        LivenessPlatformChannel c = channel(true);
        assertEquals("BAD_ARGUMENTS", call(c, "startSession", args()).code);
        assertEquals("BAD_ARGUMENTS", call(c, "startSession",
                args("subjectId", "s", "workflow", "NOPE", "machineUserId", "m")).code);
        Map<String, Object> a = start();
        a.put("locale", "xx");
        assertEquals("BAD_ARGUMENTS", call(c, "startSession", a).code);
        assertEquals("UNKNOWN_METHOD", call(c, "explode", args()).code);
        assertTrue(events.isEmpty());
    }

    @Test
    void secondStartWhileActiveIsRejected() {
        LivenessPlatformChannel c = channel(false); // runner defers: session stays "active"
        assertNull(call(c, "startSession", start()).code);
        assertEquals("SESSION_ACTIVE", call(c, "startSession", start()).code);
        assertEquals("SESSION_ACTIVE", call(c, "retrySession", args()).code);
        assertEquals(1, deferred.size());
    }

    @Test
    void cancelWithoutSessionIsAnError() {
        assertEquals("NO_SESSION", call(channel(true), "cancelSession", args()).code);
    }

    @Test
    void cancelStopsDeviceAndSuppressesResultAndAttestation() {
        LivenessPlatformChannel c = channel(false);
        call(c, "startSession", start());
        Res r = call(c, "cancelSession", args());
        assertNull(r.code);
        deferred.get(0).run(); // engine thread now runs against a closed device
        assertNull(last("SESSION_COMPLETE"), "a cancelled session reports no result");
        for (Map<String, Object> e : events) assertFalse(MiniJson.write(e).contains("attestation"));
        Map<String, Object> err = last("ERROR");
        assertEquals("SESSION_CANCELLED", data(err).get("errorCode"));
        assertAllSchemaValid();
        // and a new session can start afterwards
        assertNull(call(c, "startSession", start()).code);
    }

    @Test
    void retryOnlyAfterAFailedSession() {
        LivenessPlatformChannel c = channel(true);
        assertEquals("NOTHING_TO_RETRY", call(c, "retrySession", args()).code);

        scenario = MockDeviceAdapter.Scenario.PRINT;
        scorer.setStubScore(5);
        call(c, "startSession", start());
        events.clear();
        scenario = MockDeviceAdapter.Scenario.BONA_FIDE;
        scorer.setStubScore(85);
        assertNull(call(c, "retrySession", args()).code);
        assertEquals("PASS", data(last("SESSION_COMPLETE")).get("verdict"));

        assertEquals("NOTHING_TO_RETRY", call(c, "retrySession", args()).code, "no retry after success");
    }

    @Test
    void updateConfigAcceptsOnlyPresentationKeys() {
        LivenessPlatformChannel c = channel(true);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("locale", "fr");
        ok.put("previewEnabled", Boolean.TRUE);
        ok.put("previewMaxFps", 15L);
        assertNull(call(c, "updateConfig", ok).code);

        for (String forbidden : new String[]{"passThreshold", "attackThreshold", "maxRetries", "minQualifyingFrames"}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(forbidden, 1L);
            assertEquals("CONFIG_KEY_NOT_ALLOWED", call(c, "updateConfig", m).code, forbidden);
        }
        Map<String, Object> badLocale = new LinkedHashMap<>();
        badLocale.put("locale", "klingon");
        assertEquals("BAD_ARGUMENTS", call(c, "updateConfig", badLocale).code);
        Map<String, Object> badFps = new LinkedHashMap<>();
        badFps.put("previewMaxFps", 500L);
        assertEquals("BAD_ARGUMENTS", call(c, "updateConfig", badFps).code);
        Map<String, Object> badType = new LinkedHashMap<>();
        badType.put("previewEnabled", "yes");
        assertEquals("BAD_ARGUMENTS", call(c, "updateConfig", badType).code);
    }

    @Test
    void updateConfigLocaleAppliesToLaterEvents() {
        LivenessPlatformChannel c = channel(true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locale", "fr");
        call(c, "updateConfig", m);
        call(c, "startSession", start());
        Map<String, Object> ev = null;
        for (Map<String, Object> e : events) if ("STATE_CHANGE".equals(e.get("eventType"))) { ev = e; break; }
        assertEquals(UiMessages.get("fr", (String) data(ev).get("currentState")), data(ev).get("message"));
        assertEquals(Boolean.FALSE, data(ev).get("rtl"));
    }

    // ---------------- preview ----------------

    @Test
    void previewIsOffByDefaultJpegOnlyAndThrottled() throws Exception {
        LivenessPlatformChannel c = channel(true);
        List<byte[]> got = new ArrayList<>();
        c.setPreviewSink(got::add);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        byte[] rawRgb = {10, 20, 30, 40, 50};

        c.onPreviewFrame(jpeg);
        assertTrue(got.isEmpty(), "disabled by default");

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("previewEnabled", Boolean.TRUE);
        cfg.put("previewMaxFps", 2L);
        call(c, "updateConfig", cfg);

        c.onPreviewFrame(rawRgb);
        assertTrue(got.isEmpty(), "raw RGB is never forwarded as a preview");
        c.onPreviewFrame(jpeg);
        c.onPreviewFrame(jpeg);
        assertEquals(1, got.size(), "second frame within 500 ms is throttled");
        Thread.sleep(550);
        c.onPreviewFrame(jpeg);
        assertEquals(2, got.size());
    }
}
