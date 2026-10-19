package liveness.android;

import java.util.List;

/** Read-only view of the device environment. Android impl in android/ (AndroidEnvProbe); faked in tests. */
public interface EnvProbe {
    /** android.os.Build.TAGS */
    String buildTags();
    /** System property (e.g. ro.hardware, ro.kernel.qemu); null if absent. */
    String systemProperty(String key);
    boolean fileExists(String path);
    boolean isPackageInstalled(String packageName);
    /** Settings.Global.ADB_ENABLED == 1 */
    boolean isAdbEnabled();
    /** Debug.isDebuggerConnected() or TracerPid != 0 */
    boolean isDebuggerAttached();
    /** Loaded native libs / mapped files (from /proc/self/maps), lower-casing is done by the checker. */
    List<String> loadedModules();
    /** True if a class with this name can be loaded (e.g. de.robv.android.xposed.XposedBridge). */
    boolean classExists(String className);
    /** Local TCP port open check (frida-server default 27042). */
    boolean isLocalPortOpen(int port);
}
