package liveness.android;

import liveness.crypto.Attestation;
import liveness.device.DeviceAdapter;
import liveness.flow.Verdict;
import liveness.service.LivenessCallback;
import liveness.service.LivenessService;
import liveness.ui.UiMessages;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Bridge between the native LivenessService and the Flutter UI (pure Java; the Flutter
 * MethodChannel/EventChannel wiring lives in android/ LivenessFlutterPlugin).
 * <p>
 * Incoming methods: startSession, cancelSession, retrySession, updateConfig.
 * Outgoing events are Maps shaped by ui-contract/events-schema.json:
 * STATE_CHANGE, CHALLENGE_ISSUED, CAPTURE_PROGRESS, SESSION_COMPLETE, DEVICE_STATUS, ERROR.
 * <p>
 * Name mapping from the M4 brief to the shared schema (the schema is authoritative, since
 * Desktop and Android share it):
 * STATE_CHANGED -> STATE_CHANGE, CHALLENGE_PROMPT -> CHALLENGE_ISSUED,
 * PROGRESS -> CAPTURE_PROGRESS, RESULT -> SESSION_COMPLETE, ERROR -> ERROR.
 * PREVIEW_FRAME is not a schema event; JPEG preview bytes go to a separate PreviewSink.
 * <p>
 * The UI never controls policy: updateConfig only accepts presentation keys
 * (locale, previewEnabled, previewMaxFps). Thresholds, retries and bands are not UI-settable.
 */
public final class LivenessPlatformChannel implements LivenessCallback {

    public interface EventSink { void success(Map<String, Object> event); }
    public interface PreviewSink { void success(byte[] jpeg); }
    public interface MethodResult {
        void success(Object value);
        void error(String code, String message);
    }

    private final LivenessService service;
    private final Supplier<DeviceAdapter> deviceFactory;
    private final Executor executor;
    private final LongSupplier clock;

    private final Object lock = new Object();
    private volatile EventSink events;
    private volatile PreviewSink preview;

    private String locale = "en";
    private boolean previewEnabled = false;
    private int previewMaxFps = 10;
    private long lastPreviewNanos = 0;

    private boolean active = false;
    private boolean cancelled = false;
    private DeviceAdapter activeDevice;
    private String sessionId = "idle";
    private String lastState = null;
    private int lastProgressPct = -1;
    private int challengeCount = 0;
    private SessionParams lastParams;
    private boolean lastWasSuccess = false;

    private static final class SessionParams {
        final String subjectId, machineUserId;
        final LivenessService.WorkflowType workflow;
        SessionParams(String s, String m, LivenessService.WorkflowType w) {
            subjectId = s; machineUserId = m; workflow = w;
        }
    }

    public LivenessPlatformChannel(LivenessService service, Supplier<DeviceAdapter> deviceFactory,
                                   Executor executor, LongSupplier clock) {
        this.service = service;
        this.deviceFactory = deviceFactory;
        this.executor = executor;
        this.clock = clock;
    }

    public void setEventSink(EventSink sink) { this.events = sink; }
    public void setPreviewSink(PreviewSink sink) { this.preview = sink; }

    // ===================== incoming method calls =====================

    public void handleMethodCall(String method, Map<String, Object> args, MethodResult result) {
        if (args == null) args = new LinkedHashMap<>();
        switch (method) {
            case "startSession":  startSession(args, result); break;
            case "cancelSession": cancelSession(result); break;
            case "retrySession":  retrySession(result); break;
            case "updateConfig":  updateConfig(args, result); break;
            default: result.error("UNKNOWN_METHOD", method);
        }
    }

    private void startSession(Map<String, Object> a, MethodResult result) {
        String subject = asString(a.get("subjectId"));
        String wf = asString(a.get("workflow"));
        String mu = asString(a.get("machineUserId"));
        if (subject == null || subject.isEmpty() || wf == null || mu == null || mu.isEmpty()) {
            result.error("BAD_ARGUMENTS", "subjectId, workflow, machineUserId are required");
            return;
        }
        LivenessService.WorkflowType type;
        try {
            type = LivenessService.WorkflowType.valueOf(wf);
        } catch (IllegalArgumentException e) {
            result.error("BAD_ARGUMENTS", "unknown workflow");
            return;
        }
        String loc = asString(a.get("locale"));
        synchronized (lock) {
            if (loc != null) {
                if (!isSupportedLocale(loc)) { result.error("BAD_ARGUMENTS", "unsupported locale"); return; }
                locale = loc;
            }
        }
        launch(new SessionParams(subject, mu, type), result);
    }

    private void retrySession(MethodResult result) {
        SessionParams p;
        synchronized (lock) {
            if (active) { result.error("SESSION_ACTIVE", "a session is running"); return; }
            if (lastParams == null || lastWasSuccess) {
                result.error("NOTHING_TO_RETRY", "no failed session to retry");
                return;
            }
            p = lastParams;
        }
        launch(p, result);
    }

    private void launch(final SessionParams p, MethodResult result) {
        final DeviceAdapter device;
        synchronized (lock) {
            if (active) { result.error("SESSION_ACTIVE", "a session is running"); return; }
            device = deviceFactory.get();
            active = true;
            cancelled = false;
            activeDevice = device;
            sessionId = "ui-" + UUID.randomUUID();
            lastState = null;
            lastProgressPct = -1;
            challengeCount = 0;
            lastParams = p;
            lastWasSuccess = false;
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("sessionId", currentSessionId());
        result.success(ack);

        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    service.performLiveness(p.subjectId, device, p.machineUserId, p.workflow,
                            LivenessPlatformChannel.this);
                } catch (RuntimeException e) {
                    emitError("ENGINE_FAILURE", "Verification could not be completed.");
                } finally {
                    try { device.close(); } catch (RuntimeException ignored) { }
                    synchronized (lock) { active = false; activeDevice = null; }
                }
            }
        });
    }

    private void cancelSession(MethodResult result) {
        DeviceAdapter d;
        synchronized (lock) {
            if (!active) { result.error("NO_SESSION", "no active session"); return; }
            cancelled = true;
            d = activeDevice;
        }
        if (d != null) d.close(); // engine loop sees DeviceException -> terminal; result is suppressed
        emitError("SESSION_CANCELLED", UiMessages.get(locale, "DEVICE_ERROR"));
        result.success(null);
    }

    private void updateConfig(Map<String, Object> cfg, MethodResult result) {
        synchronized (lock) {
            for (String k : cfg.keySet()) {
                if (!"locale".equals(k) && !"previewEnabled".equals(k) && !"previewMaxFps".equals(k)) {
                    result.error("CONFIG_KEY_NOT_ALLOWED", k);
                    return;
                }
            }
            Object l = cfg.get("locale");
            if (l != null) {
                if (!(l instanceof String) || !isSupportedLocale((String) l)) {
                    result.error("BAD_ARGUMENTS", "unsupported locale");
                    return;
                }
            }
            Object fps = cfg.get("previewMaxFps");
            if (fps != null && (!(fps instanceof Number)
                    || ((Number) fps).intValue() < 1 || ((Number) fps).intValue() > 30)) {
                result.error("BAD_ARGUMENTS", "previewMaxFps must be 1..30");
                return;
            }
            Object pe = cfg.get("previewEnabled");
            if (pe != null && !(pe instanceof Boolean)) {
                result.error("BAD_ARGUMENTS", "previewEnabled must be boolean");
                return;
            }
            if (l != null) locale = (String) l;
            if (fps != null) previewMaxFps = ((Number) fps).intValue();
            if (pe != null) previewEnabled = (Boolean) pe;
        }
        result.success(null);
    }

    // ===================== LivenessCallback (engine -> UI) =====================

    @Override
    public void onStateChanged(String state, String message) {
        String prev;
        String loc;
        synchronized (lock) {
            if (cancelled || state.equals(lastState)) return;
            prev = lastState;
            lastState = state;
            loc = locale;
        }
        String safeState = sanitizeState(state);
        Map<String, Object> d = new LinkedHashMap<>();
        if (prev != null) d.put("previousState", sanitizeState(prev));
        d.put("currentState", safeState);
        d.put("messageKey", safeState);
        d.put("message", UiMessages.get(loc, safeState));
        d.put("rtl", UiMessages.isRtl(loc));
        emit("STATE_CHANGE", d);
    }

    @Override
    public void onPreviewFrame(byte[] jpegBytes) {
        PreviewSink sink = preview;
        if (sink == null || jpegBytes == null || jpegBytes.length < 4) return;
        // Only JPEG goes out; raw RGB (CameraX frames) is rendered natively by the camera preview view.
        if ((jpegBytes[0] & 0xFF) != 0xFF || (jpegBytes[1] & 0xFF) != 0xD8) return;
        synchronized (lock) {
            if (!previewEnabled || cancelled) return;
            long now = System.nanoTime();
            if (now - lastPreviewNanos < 1_000_000_000L / previewMaxFps) return;
            lastPreviewNanos = now;
        }
        sink.success(jpegBytes);
    }

    @Override
    public void onChallengePrompt(String action, long timeoutMs) {
        Map<String, Object> d = new LinkedHashMap<>();
        String loc;
        int idx;
        synchronized (lock) {
            if (cancelled) return;
            loc = locale;
            idx = ++challengeCount;
        }
        d.put("challengeAction", action);
        d.put("challengeIndex", (long) idx);
        d.put("challengeTimeoutMs", timeoutMs);
        d.put("messageKey", "CHALLENGE." + action);
        d.put("message", UiMessages.challengeMessage(loc, action));
        emit("CHALLENGE_ISSUED", d);
    }

    @Override
    public void onProgress(int framesProcessed, int framesNeeded) {
        double p = framesNeeded <= 0 ? 0.0 : Math.min(1.0, Math.max(0.0, (double) framesProcessed / framesNeeded));
        int pct = (int) Math.round(p * 100);
        synchronized (lock) {
            if (cancelled || pct == lastProgressPct) return;
            lastProgressPct = pct;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("captureProgress", p);
        d.put("framesProcessed", (long) framesProcessed);
        d.put("framesNeeded", (long) framesNeeded);
        emit("CAPTURE_PROGRESS", d);
    }

    @Override
    public void onResult(LivenessService.LivenessResult r) {
        String loc;
        synchronized (lock) {
            if (cancelled) return;      // a cancelled session reports nothing and exposes no attestation
            loc = locale;
            lastWasSuccess = r.isSuccess();
        }
        Map<String, Object> d = new LinkedHashMap<>();
        Verdict v = r.verdict();
        d.put("verdict", verdictName(v));
        String rawState = r.failureReason() != null ? r.failureReason() : (r.isSuccess() ? "SUCCESS" : "VERIFICATION_FAILED");
        String state = r.isSuccess() ? "SUCCESS" : sanitizeState(rawState);
        if (v == Verdict.ATTACK) {
            state = "VERIFICATION_FAILED";
        }
        d.put("currentState", state);
        d.put("messageKey", state);
        d.put("message", UiMessages.get(loc, state));
        d.put("retryCount", (long) r.retryCount());
        d.put("engineSessionId", r.sessionId());
        if (r.isSuccess()) {                // attestation exists ONLY on SUCCESS
            Attestation a = r.attestation();
            Map<String, Object> at = new LinkedHashMap<>();
            at.put("imageSha256", a.imageSha256());
            at.put("transcriptSha256", a.transcriptSha256());
            at.put("deviceTier", a.deviceTier());
            at.put("captureTimestamp", a.captureTimestamp());
            at.put("modelVersion", a.modelVersion());
            at.put("padScore", (long) a.pad().score());
            at.put("canonical", a.canonical());
            if (a.signature() != null) at.put("signature", Base64.getEncoder().encodeToString(a.signature()));
            d.put("attestation", at);
        }
        emit("SESSION_COMPLETE", d);
        if (r.verdict() == Verdict.DEVICE_FAILURE) {
            Map<String, Object> ds = new LinkedHashMap<>();
            ds.put("deviceConnected", Boolean.FALSE);
            emit("DEVICE_STATUS", ds);
        }
    }

    @Override
    public void onError(String errorCode, String message) {
        String safeCode = errorCode;
        String safeMsg = message;
        if (errorCode != null && isSensitiveError(errorCode)) {
            safeCode = "VERIFICATION_FAILED";
            safeMsg = UiMessages.get(locale, "VERIFICATION_FAILED");
        }
        emitError(safeCode, safeMsg);
    }

    // ===================== helpers =====================

    private void emitError(String code, String message) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("errorCode", code);
        d.put("message", message);
        emit("ERROR", d);
    }

    private void emit(String type, Map<String, Object> data) {
        EventSink sink = events;
        if (sink == null) return;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("eventType", type);
        e.put("timestamp", clock.getAsLong());
        e.put("sessionId", currentSessionId());
        e.put("data", data);
        sink.success(e);
    }

    private String currentSessionId() { synchronized (lock) { return sessionId; } }

    private static String verdictName(Verdict v) {
        if (v == Verdict.ATTACK) return "VERIFICATION_FAILED";
        return v == Verdict.DEVICE_FAILURE ? "DEVICE_ERROR" : v.name(); // schema enum uses DEVICE_ERROR
    }

    private static String sanitizeState(String state) {
        if (state == null) return "VERIFICATION_FAILED";
        String u = state.toUpperCase(java.util.Locale.ROOT);
        if (u.contains("ATTACK") || u.contains("REPLAY") || u.contains("PRINT")
                || u.contains("MASK") || u.contains("INJECTION")) {
            return "VERIFICATION_FAILED";
        }
        return state;
    }

    private static boolean isSensitiveError(String s) {
        String u = s.toUpperCase(java.util.Locale.ROOT);
        return u.contains("ATTACK") || u.contains("REPLAY") || u.contains("PRINT")
                || u.contains("MASK") || u.contains("INJECTION");
    }

    private static boolean isSupportedLocale(String l) {
        for (String s : UiMessages.supportedLocales()) if (s.equals(l)) return true;
        return false;
    }

    private static String asString(Object o) { return o instanceof String ? (String) o : null; }
}
