package liveness.android.glue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import liveness.android.EnvProbe;

/** EnvProbe backed by the real device. Package checks need the <queries> entries in the manifest. */
public final class AndroidEnvProbe implements EnvProbe {

    private final Context ctx;

    public AndroidEnvProbe(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    @Override public String buildTags() { return Build.TAGS; }

    @Override
    public String systemProperty(String key) {
        // Public Build fields where they exist (no hidden-API use).
        switch (key) {
            case "ro.hardware": return Build.HARDWARE;
            case "ro.product.model": return Build.MODEL;
            case "ro.build.fingerprint": return Build.FINGERPRINT;
            default: break;
        }
        try { // best effort; may be blocked by hidden-API restrictions
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class).invoke(null, key);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public boolean fileExists(String path) { return new File(path).exists(); }

    @Override
    public boolean isPackageInstalled(String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean isAdbEnabled() {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    @Override
    public boolean isDebuggerAttached() {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("TracerPid:")) return !"0".equals(l.substring(10).trim());
            }
        } catch (Exception ignored) { }
        return false;
    }

    @Override
    public List<String> loadedModules() {
        Set<String> mods = new LinkedHashSet<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf('/');
                if (i >= 0) mods.add(l.substring(i));
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(mods);
    }

    @Override
    public boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isLocalPortOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 100);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
