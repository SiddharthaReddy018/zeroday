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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class UiEventContractTest {

    private static final String[] FORBIDDEN_WORDS = {"ATTACK", "PRINT", "REPLAY", "MASK", "INJECTION"};

    private SchemaValidator validator;
    private final List<Map<String, Object>> emittedEvents = new ArrayList<>();
    private MiniFasNetScorer scorer;
    private SameFaceChecker faceChecker;
    private LivenessService service;
    private MockDeviceAdapter.Scenario scenario;

    @BeforeEach
    void setUp() {
        validator = new SchemaValidator();
        emittedEvents.clear();

        LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(2)
                .activeEnabled(false)
                .maxRetries(1)
                .build();
        scorer = new MiniFasNetScorer(null);
        faceChecker = new SameFaceChecker(0.6);
        service = new LivenessService(cfg, scorer, faceChecker,
                new ChallengeGenerator(Collections.emptyList(), Collections.emptyList()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
    }

    private LivenessPlatformChannel createChannel() {
        Supplier<DeviceAdapter> deviceFactory = () -> new MockDeviceAdapter(scenario);
        LivenessPlatformChannel channel = new LivenessPlatformChannel(service, deviceFactory,
                Runnable::run, () -> 1_700_000_000_000L);
        channel.setEventSink(e -> emittedEvents.add(roundTrip(e)));
        return channel;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> roundTrip(Map<String, Object> e) {
        return (Map<String, Object>) MiniJson.parse(MiniJson.write(e));
    }

    @SuppressWarnings("unchecked")
    private static void assertNoAttackLeak(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data == null) return;
        for (String key : java.util.Arrays.asList("currentState", "previousState", "verdict", "messageKey", "message", "errorCode", "challengeReason")) {
            Object val = data.get(key);
            if (val != null) {
                String str = String.valueOf(val).toUpperCase(Locale.ROOT);
                for (String word : FORBIDDEN_WORDS) {
                    assertFalse(str.contains(word),
                            "Field '" + key + "' contains forbidden attack keyword '" + word + "': " + str);
                }
            }
        }
    }

    @Test
    void schemaRejectsAdditionalProperties() {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("eventType", "STATE_CHANGE");
        ev.put("timestamp", 1700000000000L);
        ev.put("sessionId", "s-1");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentState", "POSITIONING");
        data.put("unknownProp", "evil");
        ev.put("data", data);

        List<String> errs = validator.validate(ev);
        assertFalse(errs.isEmpty(), "Schema must reject unrecognized properties in data");
        assertTrue(errs.get(0).contains("additional property not allowed"), errs.get(0));
    }

    @Test
    void bonaFideSessionEmitsStrictlyValidEvents() {
        scenario = MockDeviceAdapter.Scenario.BONA_FIDE;
        scorer.setStubScore(90);
        faceChecker.setStubResult(true);

        LivenessPlatformChannel channel = createChannel();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("subjectId", "sub-1");
        args.put("workflow", "RESIDENT_REGISTRATION");
        args.put("machineUserId", "user-1");

        channel.handleMethodCall("startSession", args, new LivenessPlatformChannel.MethodResult() {
            @Override public void success(Object value) {}
            @Override public void error(String code, String message) { fail("startSession failed: " + code); }
        });

        assertFalse(emittedEvents.isEmpty());
        for (Map<String, Object> ev : emittedEvents) {
            List<String> errs = validator.validate(ev);
            assertTrue(errs.isEmpty(), "Schema violations in " + MiniJson.write(ev) + ": " + errs);
            assertNoAttackLeak(ev);
        }

        // Verify attestation is present and schema-valid on PASS
        Map<String, Object> complete = emittedEvents.stream()
                .filter(e -> "SESSION_COMPLETE".equals(e.get("eventType")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> completeData = (Map<String, Object>) complete.get("data");
        assertEquals("PASS", completeData.get("verdict"));
        assertEquals("SUCCESS", completeData.get("currentState"));
        assertNotNull(completeData.get("attestation"));
    }

    @Test
    void attackSessionNeverLeaksAttackDetailsInEvents() {
        scenario = MockDeviceAdapter.Scenario.PRINT;
        scorer.setStubScore(5); // Low score triggering attack
        faceChecker.setStubResult(true);

        LivenessPlatformChannel channel = createChannel();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("subjectId", "sub-1");
        args.put("workflow", "RESIDENT_REGISTRATION");
        args.put("machineUserId", "user-1");

        channel.handleMethodCall("startSession", args, new LivenessPlatformChannel.MethodResult() {
            @Override public void success(Object value) {}
            @Override public void error(String code, String message) { fail("startSession failed: " + code); }
        });

        assertFalse(emittedEvents.isEmpty());
        for (Map<String, Object> ev : emittedEvents) {
            List<String> errs = validator.validate(ev);
            assertTrue(errs.isEmpty(), "Schema violations in attack event " + MiniJson.write(ev) + ": " + errs);
            assertNoAttackLeak(ev);
        }

        Map<String, Object> complete = emittedEvents.stream()
                .filter(e -> "SESSION_COMPLETE".equals(e.get("eventType")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> completeData = (Map<String, Object>) complete.get("data");
        assertEquals("VERIFICATION_FAILED", completeData.get("verdict"));
        assertEquals("VERIFICATION_FAILED", completeData.get("currentState"));
        assertNull(completeData.get("attestation"), "Attestation must not be delivered on failed session");
    }

    @Test
    void directErrorEmissionSanitizesSensitiveDiagnosis() {
        LivenessPlatformChannel channel = createChannel();
        channel.onError("REPLAY_ATTACK_DETECTED", "Attack pattern detected in frames");

        assertEquals(1, emittedEvents.size());
        Map<String, Object> ev = emittedEvents.get(0);
        List<String> errs = validator.validate(ev);
        assertTrue(errs.isEmpty(), "Schema violations: " + errs);
        assertNoAttackLeak(ev);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ev.get("data");
        assertEquals("VERIFICATION_FAILED", data.get("errorCode"));
    }
}
