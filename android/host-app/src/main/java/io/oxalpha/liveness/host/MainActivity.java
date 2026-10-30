package io.oxalpha.liveness.host;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import liveness.android.AndroidDeviceAdapter;
import liveness.android.CameraOnlyDevAdapter;
import liveness.android.CameraXDeviceAdapter;
import liveness.android.CompositeFaceDetector;
import liveness.android.CrossStreamBinder;
import liveness.android.EnvironmentIntegrityChecker;
import liveness.android.SFaceEmbedder;
import liveness.android.SbiIntentDeviceAdapter;
import liveness.android.SbiJwtVerifier;
import liveness.android.YuNetFaceDetector;
import liveness.android.glue.AndroidEnvProbe;
import liveness.android.glue.AndroidRgbDecoder;
import liveness.android.glue.AndroidSbiLauncher;
import liveness.android.glue.CameraXController;
import liveness.android.glue.MlKitFaceDetector;
import liveness.challenge.ChallengeGenerator;
import liveness.crypto.AndroidKeyStoreSigner;
import liveness.device.DeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessCallback;
import liveness.service.LivenessService;

/**
 * DEVELOPMENT ONLY host. Host camera only, no SBI, no Flutter, no JWT trust anchors.
 * Demonstrates the engine on live frames. It is not the MOSIP SBI flow.
 */
public class MainActivity extends ComponentActivity implements LivenessCallback {

    // SHA-256 pins, verified locally with shasum on the staged files.
    private static final String PAD_SHA = "fde20585635cae62ed1d41796f76b6f8bc4b92cd91ec1cf0f1bc6485d2d587a9";
    private static final String SFACE_SHA = "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79";

    private PreviewView previewView;
    private TextView banner, status, prompt, result;
    private Button start;
    private LivenessService service;
    private CameraXDeviceAdapter camera;
    private CompositeFaceDetector detector;
    private final CameraXController controller = new CameraXController();
    private volatile boolean running;
    private AndroidSbiLauncher sbiLauncher;
    private SFaceEmbedder sfaceEmbedder;
    private SameFaceChecker sameChecker;
    private volatile boolean sbiAvailable;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sbiLauncher = new AndroidSbiLauncher(this);

        FrameLayout root = new FrameLayout(this);
        previewView = new PreviewView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(24, 48, 24, 24);
        banner = text("Detecting SBI / camera configuration...", 14);
        banner.setBackgroundColor(Color.parseColor("#CC8800"));
        status = text("Starting...", 16);
        prompt = text("", 28);
        result = text("", 22);
        start = new Button(this);
        start.setText("Start liveness");
        start.setEnabled(false);
        start.setOnClickListener(v -> runSession());
        col.addView(banner);
        col.addView(status);
        col.addView(prompt);
        col.addView(result);
        col.addView(start);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(col, lp);
        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initEngine();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) initEngine();
        else status.setText("Camera permission denied.");
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        return t;
    }

    private void initEngine() {
        status.setText("Loading models...");
        new Thread(() -> {
            try {
                File yunet = copyAsset("face_detection_yunet.onnx");
                byte[] pad = readAsset("MiniFASNetV2-SE-int8.onnx");
                byte[] sface = readAsset("face_recognition_sface.onnx");

                detector = new CompositeFaceDetector(new YuNetFaceDetector(yunet.toPath()), new MlKitFaceDetector());
                AndroidKeyStoreSigner.ensureKey(AndroidKeyStoreSigner.DEFAULT_ALIAS);
                AndroidKeyStoreSigner signer = new AndroidKeyStoreSigner(AndroidKeyStoreSigner.DEFAULT_ALIAS);
                sfaceEmbedder = new SFaceEmbedder(sface, SFACE_SHA, detector);
                MiniFasNetScorer scorer = new MiniFasNetScorer(pad, PAD_SHA);
                sameChecker = new SameFaceChecker(SFaceEmbedder.CALIBRATED_THRESHOLD);
                // Issued pool restricted to BLINK, TURN_LEFT, TURN_RIGHT.
                ChallengeGenerator gen = new ChallengeGenerator(
                        java.util.Arrays.asList("BLINK", "TURN_LEFT", "TURN_RIGHT"),
                        Collections.emptyList());
                // DEV: int8 model scores real faces 0-31; lower attackThreshold so a live face
                // isn't immediately BLOCKED_ATTACK. attackPersistenceWindows raised to 5 to
                // require a sustained run of sub-threshold frames before blocking.
                // passThreshold kept at 70 (unchanged) — challenge still required for scores 3–69.
                // DEV: int8 model scores real faces 0-31; lower attackThreshold so a live face
                // isn't immediately BLOCKED_ATTACK. attackPersistenceWindows raised to 5 to
                // require a sustained run of sub-threshold frames before blocking.
                // passThreshold kept at 70 (unchanged) — challenge still required for scores 3-69.
                // RetryRateLimiter: maxRetries=50 for dev session (in-memory, resets on app kill).
                service = new LivenessService(LivenessConfig.builder()
                        .forceChallenge(true)
                        .attackThreshold(3)
                        .attackPersistenceWindows(5)
                        .build(), scorer, sameChecker, gen,
                        new RetryRateLimiter(5, 60_000), signer);
                service.setFaceEmbedder(sfaceEmbedder);

                List<String> sbiPkgs = sbiLauncher.resolveSbiPackages();
                sbiAvailable = !sbiPkgs.isEmpty();

                runOnUiThread(() -> {
                    camera = new CameraXDeviceAdapter("front-camera", detector);
                    controller.bind(getApplicationContext(), this, previewView, camera);
                    if (sbiAvailable) {
                        banner.setText("SBI MODE: " + sbiPkgs.get(0) + " (MOSIP Intent Flow)");
                        banner.setBackgroundColor(Color.parseColor("#006600"));
                    } else {
                        banner.setText("DEV MODE: host camera only (no external SBI app).");
                        banner.setBackgroundColor(Color.parseColor("#CC8800"));
                    }
                    status.setText("Ready. Face the front camera.");
                    start.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("INIT FAILED: " + e));
            }
        }).start();
    }

    private void runSession() {
        if (running) return;
        running = true;
        start.setEnabled(false);
        result.setText("");
        new Thread(() -> {
            try {
                EnvironmentIntegrityChecker integrity = EnvironmentIntegrityChecker.forDevelopment(
                        new AndroidEnvProbe(getApplicationContext()), true);
                DeviceAdapter device;
                if (sbiAvailable) {
                    CrossStreamBinder binder = new CrossStreamBinder(sameChecker, sfaceEmbedder, 10_000);
                    SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(
                            sbiLauncher, binder, camera::latestFrame, new AndroidRgbDecoder(),
                            SbiJwtVerifier.disabledForDevelopment(), "MOSIP_Registration_Client", "Registration", 5000);
                    device = new AndroidDeviceAdapter(integrity, camera, sbi);
                } else {
                    device = new CameraOnlyDevAdapter(integrity, camera);
                }
                service.performLiveness("dev-subject", device, "dev-operator",
                        LivenessService.WorkflowType.RESIDENT_REGISTRATION, this);
            } catch (RuntimeException e) {
                onError("ENGINE_FAILURE", String.valueOf(e));
            } finally {
                running = false;
                runOnUiThread(() -> start.setEnabled(true));
            }
        }).start();
    }

    private File copyAsset(String name) throws Exception {
        File out = new File(getFilesDir(), name);
        try (InputStream in = getAssets().open(name); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    @Override public void onStateChanged(String state, String message) {
        android.util.Log.i("MainActivity", "onStateChanged: " + state + " (msg=" + message + ")");
        System.err.println("CALLBACK onStateChanged: " + state + " - " + message);
        runOnUiThread(() -> status.setText(state + (message == null ? "" : ": " + message)));
    }
    @Override public void onPreviewFrame(byte[] jpegBytes) { }
    @Override public void onChallengePrompt(String action, long timeoutMs) {
        android.util.Log.i("MainActivity", "onChallengePrompt: " + action + " (" + timeoutMs + " ms)");
        System.err.println("CALLBACK onChallengePrompt: " + action + " (" + timeoutMs + " ms)");
        runOnUiThread(() -> prompt.setText(action + " (" + (timeoutMs / 1000) + " s)"));
    }
    @Override public void onProgress(int framesProcessed, int framesNeeded) {
        runOnUiThread(() -> status.setText("Frames " + framesProcessed + " / " + framesNeeded));
    }
    @Override public void onResult(LivenessService.LivenessResult r) {
        android.util.Log.i("MainActivity", "onResult: verdict=" + r.verdict() + ", failureReason=" + r.failureReason()
                + ", retries=" + r.retryCount());
        System.err.println("CALLBACK onResult: verdict=" + r.verdict() + ", failureReason=" + r.failureReason());
        runOnUiThread(() -> {
            prompt.setText("");
            result.setText("VERDICT: " + r.verdict() + (r.failureReason() == null ? "" : "\n" + r.failureReason())
                    + "\nretries: " + r.retryCount());
        });
    }
    @Override public void onError(String errorCode, String message) {
        android.util.Log.e("MainActivity", "onError: " + errorCode + " - " + message);
        System.err.println("CALLBACK onError: " + errorCode + " - " + message);
        runOnUiThread(() -> {
            result.setText("ERROR " + errorCode + ": " + message);
            // ENGINE_FAILURE can be caused by Android revoking the one-time camera permission
            // mid-session. Re-check the permission and rebind the camera so the user can
            // tap Start again without force-stopping the app.
            if ("ENGINE_FAILURE".equals(errorCode)) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    // Permission still held — camera may have closed; rebind.
                    controller.unbind();
                    camera = new CameraXDeviceAdapter("front-camera", detector);
                    controller.bind(getApplicationContext(), this, previewView, camera);
                    start.setEnabled(true);
                    status.setText("Camera reconnected. Tap Start.");
                } else {
                    // Permission was revoked — re-request it.
                    status.setText("Camera permission lost. Re-requesting...");
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA}, 1);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If the camera permission was granted while the app was paused (e.g. user tapped
        // "Allow" in the OS dialog), re-initialise the engine so the user doesn't have to
        // restart the app.
        if (service == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initEngine();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        controller.unbind();
    }
}
