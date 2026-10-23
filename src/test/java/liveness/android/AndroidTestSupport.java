package liveness.android;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared fakes and builders for the Android unit tests (pure JVM, no Android SDK).
 * Public (with the few members used from liveness.desktop tests) so desktop-only tests in another
 * package can reuse the same fixtures.
 */
public final class AndroidTestSupport {

    private AndroidTestSupport() {}

    // ---------- YUV ----------

    /** Textured luma (so the Laplacian variance is high), neutral chroma, tightly packed strides. */
    public static YuvFrame texturedFrame(int w, int h, long tsMs, int rotation, long seed) {
        byte[] y = new byte[w * h];
        Random rnd = new Random(seed);
        for (int i = 0; i < y.length; i++) y[i] = (byte) (64 + rnd.nextInt(128)); // mean ~128
        byte[] u = new byte[(w / 2) * (h / 2)];
        byte[] v = new byte[(w / 2) * (h / 2)];
        Arrays.fill(u, (byte) 128);
        Arrays.fill(v, (byte) 128);
        return new YuvFrame(w, h, y, u, v, w, w / 2, 1, rotation, tsMs);
    }

    /** Uniform gray frame with the given luma and padded row strides (garbage in the padding). */
    static YuvFrame paddedGray(int w, int h, int luma, int rowPad, int rotation, long tsMs) {
        int yStride = w + rowPad;
        byte[] y = new byte[yStride * h];
        Arrays.fill(y, (byte) 0x7F); // padding garbage
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) y[r * yStride + c] = (byte) luma;
        int uvStride = (w / 2) + rowPad;
        byte[] u = new byte[uvStride * (h / 2)];
        byte[] v = new byte[uvStride * (h / 2)];
        Arrays.fill(u, (byte) 128);
        Arrays.fill(v, (byte) 128);
        return new YuvFrame(w, h, y, u, v, yStride, uvStride, 1, rotation, tsMs);
    }

    // ---------- detector ----------

    public static FaceDetector oneFace() {
        return (rgb, w, h) -> new FaceDetector.Observation(1, w / 4, h / 4, w / 2, h / 2, 0.0, 0.0, 0.0, null);
    }

    static FaceDetector noFace() { return (rgb, w, h) -> FaceDetector.Observation.NONE; }

    // ---------- SBI response ----------

    static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Unsigned-looking JWT with a dummy signature; only usable with SbiJwtVerifier.disabledForDevelopment(). */
    static String jwt(Map<String, Object> payload) {
        return b64url("{\"alg\":\"RS256\"}") + "." + b64url(MiniJson.write(payload)) + ".c2ln";
    }

    static Map<String, Object> capturePayload(String txId, String isoTs, byte[] image) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bioType", "Face");
        payload.put("deviceCode", "dev-001");
        payload.put("bioValue", Base64.getUrlEncoder().withoutPadding().encodeToString(image));
        payload.put("transactionId", txId);
        payload.put("timestamp", isoTs);
        return payload;
    }

    static byte[] captureResponseFor(String dataJwt, String errorCode) {
        Map<String, Object> bio = new LinkedHashMap<>();
        bio.put("specVersion", "0.9.5");
        bio.put("data", dataJwt);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("errorCode", errorCode);
        err.put("errorInfo", "x");
        bio.put("error", err);
        List<Object> bios = new ArrayList<>();
        bios.add(bio);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("biometrics", bios);
        return MiniJson.write(root).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] captureResponse(String txId, String isoTs, byte[] image, String errorCode) {
        return captureResponseFor(jwt(capturePayload(txId, isoTs, image)), errorCode);
    }

    static byte[] discoveryResponse(String appId, String status) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("deviceId", "dev-id-1");
        d.put("deviceStatus", status);
        d.put("certification", "L0");
        d.put("serviceVersion", "1.0");
        d.put("callbackId", appId);
        d.put("deviceCode", "dev-001");
        d.put("specVersion", new ArrayList<Object>(Arrays.asList("0.9.5")));
        d.put("purpose", "Registration");
        List<Object> l = new ArrayList<>();
        l.add(d);
        return MiniJson.write(l).getBytes(StandardCharsets.UTF_8);
    }

    /** Info response with an (unsigned-payload-only) deviceInfo JWT; use with the disabled verifier. */
    static byte[] infoResponse(String status, String purpose) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("deviceStatus", status);
        info.put("deviceId", "dev-id-1");
        info.put("purpose", purpose);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("deviceInfo", jwt(info));
        List<Object> l = new ArrayList<>();
        l.add(e);
        return MiniJson.write(l).getBytes(StandardCharsets.UTF_8);
    }

    // ---------- fakes ----------

    static final String APP_ID = "com.sbi.face";

    /** Scriptable SBI launcher following the Android SBI spec (discovery, appId.Info, appId.rCapture). */
    public static final class FakeLauncher implements SbiIntentLauncher {
        List<String> packages = Collections.singletonList("com.sbi.face");
        /** Handler for rCapture; null => timeout. */
        public java.util.function.Function<SbiIntentRequest, ActivityResult> captureHandler;
        java.util.Set<String> discoveryTimeouts = new java.util.HashSet<>();
        String deviceStatus = "Ready";
        String infoStatus = "Ready";
        String infoPurpose = "Registration";
        int discoveryResultCode = ActivityResult.RESULT_OK;
        final List<SbiIntentRequest> launched = new ArrayList<>();

        @Override public List<String> resolveSbiPackages() { return packages; }

        @Override
        public ActivityResult launch(SbiIntentRequest r, long timeoutMs) throws TimeoutException {
            launched.add(r);
            if (SbiIntentBridge.ACTION_DISCOVER.equals(r.action())) {
                if (discoveryTimeouts.contains(r.targetPackage())) throw new TimeoutException("discovery timeout");
                return new ActivityResult(discoveryResultCode, discoveryResponse(APP_ID, deviceStatus));
            }
            if (r.action().equals(SbiIntentBridge.infoAction(APP_ID))) {
                return new ActivityResult(ActivityResult.RESULT_OK, infoResponse(infoStatus, infoPurpose));
            }
            if (captureHandler == null) throw new TimeoutException("capture timeout");
            return captureHandler.apply(r);
        }
    }

    /** Embedding = first three RGB bytes; makes "same face" a controllable property of the test images. */
    static final class FirstPixelEmbedder implements FaceEmbedder {
        @Override public float[] embed(byte[] rgb, int w, int h) {
            return new float[]{(rgb[0] & 0xFF) + 1f, (rgb[1] & 0xFF) + 1f, (rgb[2] & 0xFF) + 1f};
        }
    }

    /** Scriptable environment. Defaults describe a clean production device. */
    public static final class FakeProbe implements EnvProbe {
        String tags = "release-keys";
        Map<String, String> props = new LinkedHashMap<>();
        java.util.Set<String> files = new java.util.HashSet<>();
        java.util.Set<String> packages = new java.util.HashSet<>();
        java.util.Set<String> classes = new java.util.HashSet<>();
        java.util.Set<Integer> ports = new java.util.HashSet<>();
        List<String> modules = new ArrayList<>(Arrays.asList("/system/lib64/libc.so", "/data/app/x/lib/libonnxruntime.so"));
        boolean adb = false;
        boolean debugger = false;

        @Override public String buildTags() { return tags; }
        @Override public String systemProperty(String k) { return props.get(k); }
        @Override public boolean fileExists(String p) { return files.contains(p); }
        @Override public boolean isPackageInstalled(String p) { return packages.contains(p); }
        @Override public boolean isAdbEnabled() { return adb; }
        @Override public boolean isDebuggerAttached() { return debugger; }
        @Override public List<String> loadedModules() { return modules; }
        @Override public boolean classExists(String c) { return classes.contains(c); }
        @Override public boolean isLocalPortOpen(int p) { return ports.contains(p); }
    }
}
