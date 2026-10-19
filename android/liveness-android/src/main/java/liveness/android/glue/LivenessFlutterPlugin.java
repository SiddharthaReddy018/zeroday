package liveness.android.glue;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import liveness.android.LivenessPlatformChannel;
import liveness.device.DeviceAdapter;
import liveness.service.LivenessService;

/**
 * Flutter wiring: MethodChannel "mosip.liveness/methods", EventChannel "mosip.liveness/events"
 * (schema events) and "mosip.liveness/preview" (JPEG bytes). The host app calls
 * {@link #configure(LivenessService, Supplier)} once before the engine attaches the plugin.
 * Flutter requires sinks to be used on the main thread, so every emission is posted there.
 */
public final class LivenessFlutterPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {

    private static LivenessService service;
    private static Supplier<DeviceAdapter> deviceFactory;

    public static void configure(LivenessService s, Supplier<DeviceAdapter> f) { service = s; deviceFactory = f; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService engine = Executors.newSingleThreadExecutor();
    private MethodChannel methods;
    private EventChannel events, preview;
    private LivenessPlatformChannel core;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding b) {
        if (service == null || deviceFactory == null) throw new IllegalStateException("call configure() first");
        core = new LivenessPlatformChannel(service, deviceFactory, engine, System::currentTimeMillis);
        methods = new MethodChannel(b.getBinaryMessenger(), "mosip.liveness/methods");
        methods.setMethodCallHandler(this);
        events = new EventChannel(b.getBinaryMessenger(), "mosip.liveness/events");
        events.setStreamHandler(new EventChannel.StreamHandler() {
            @Override public void onListen(Object a, final EventChannel.EventSink sink) {
                core.setEventSink(e -> main.post(() -> sink.success(e)));
            }
            @Override public void onCancel(Object a) { core.setEventSink(null); }
        });
        preview = new EventChannel(b.getBinaryMessenger(), "mosip.liveness/preview");
        preview.setStreamHandler(new EventChannel.StreamHandler() {
            @Override public void onListen(Object a, final EventChannel.EventSink sink) {
                core.setPreviewSink(bytes -> main.post(() -> sink.success(bytes)));
            }
            @Override public void onCancel(Object a) { core.setPreviewSink(null); }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMethodCall(@NonNull MethodCall call, @NonNull final MethodChannel.Result result) {
        Map<String, Object> args = call.arguments instanceof Map ? (Map<String, Object>) call.arguments : null;
        core.handleMethodCall(call.method, args, new LivenessPlatformChannel.MethodResult() {
            @Override public void success(Object v) { main.post(() -> result.success(v)); }
            @Override public void error(String code, String msg) { main.post(() -> result.error(code, msg, null)); }
        });
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding b) {
        methods.setMethodCallHandler(null);
        events.setStreamHandler(null);
        preview.setStreamHandler(null);
        engine.shutdownNow();
    }
}
