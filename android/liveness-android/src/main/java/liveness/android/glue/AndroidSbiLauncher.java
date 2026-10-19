package liveness.android.glue;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import liveness.android.SbiIntentLauncher;
import liveness.android.SbiIntentRequest;

/**
 * Launches SBI intents with the ActivityResult API and blocks the calling (engine) thread for the answer.
 * Per the MOSIP Android SBI spec: the request JSON is a byte[] extra "input"; the response is extra "response",
 * either bytes (discovery / info) or a "content://" URI string (capture / rCapture) that must be read once.
 * Construct in Activity.onCreate (registration must happen before STARTED). Never call launch() on the main thread.
 * <p>
 * Security: an activity result does not authenticate the sender. Requests are explicit (setPackage); trust in the
 * content comes from SbiJwtVerifier (signature + trust anchors), the wire transactionId echo and the cross-stream binding.
 * Only content:// URIs are followed, and the read is capped at 8 MB.
 */
public final class AndroidSbiLauncher implements SbiIntentLauncher {

    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final ComponentActivity activity;
    private final ActivityResultLauncher<Intent> launcher;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile CompletableFuture<ActivityResult> pending;

    public AndroidSbiLauncher(ComponentActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    CompletableFuture<ActivityResult> f = pending;
                    if (f == null) return;
                    // Resolution may do I/O (content URI); keep it off the main thread.
                    final Intent data = result.getData();
                    final int code = result.getResultCode();
                    new Thread(() -> f.complete(new ActivityResult(code, resolveResponse(data)))).start();
                });
    }

    private byte[] resolveResponse(Intent data) {
        if (data == null || data.getExtras() == null) return null;
        Object v = data.getExtras().get("response");
        if (v instanceof byte[]) return (byte[]) v;
        if (!(v instanceof String)) return null;
        String s = (String) v;
        if (!s.startsWith("content://")) return s.getBytes(StandardCharsets.UTF_8); // inline JSON
        try (InputStream in = activity.getContentResolver().openInputStream(Uri.parse(s))) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_RESPONSE_BYTES) return null; // refuse oversize
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> resolveSbiPackages() {
        List<String> out = new ArrayList<>();
        PackageManager pm = activity.getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent("io.sbi.device"), 0)) {
            out.add(ri.activityInfo.packageName);
        }
        return out;
    }

    @Override
    public synchronized ActivityResult launch(SbiIntentRequest request, long timeoutMs) throws TimeoutException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("launch() blocks; call it from the engine thread");
        }
        final Intent i = new Intent(request.action());
        if (request.targetPackage() != null) i.setPackage(request.targetPackage());
        i.setType("application/json");
        i.putExtra("input", request.input());
        if (request.grantReadUri()) i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        final CompletableFuture<ActivityResult> f = new CompletableFuture<>();
        pending = f;
        main.post(() -> {
            try {
                launcher.launch(i);
            } catch (RuntimeException e) {          // e.g. ActivityNotFoundException
                f.complete(new ActivityResult(0, null));
            }
        });
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("no SBI result within " + timeoutMs + " ms");
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TimeoutException("SBI wait aborted: " + e.getMessage());
        } finally {
            pending = null;
        }
    }
}
