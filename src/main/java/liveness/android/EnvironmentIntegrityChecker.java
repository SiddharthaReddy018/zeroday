package liveness.android;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Host environment integrity check (threat model 2.2, CEN/TS 18099 injection surface).
 * Heuristic and bypassable by a determined attacker (root hiding, Frida gadget renaming):
 * it raises the cost of injection, it does not prove integrity. Pair with SBI device trust.
 */
public final class EnvironmentIntegrityChecker {

    public enum Finding {
        ROOT_TEST_KEYS, ROOT_SU_BINARY, ROOT_MAGISK, ROOT_PACKAGE,
        ADB_ENABLED, DEBUGGER_ATTACHED,
        HOOK_XPOSED, HOOK_FRIDA,
        EMULATOR
    }

    /** Findings that block a session by default (all of them). */
    public static Set<Finding> defaultBlocking() { return EnumSet.allOf(Finding.class); }

    public static final class Result {
        private final Set<Finding> findings;
        private final Set<Finding> blocking;

        Result(Set<Finding> findings, Set<Finding> blocking) {
            this.findings = Collections.unmodifiableSet(findings);
            this.blocking = Collections.unmodifiableSet(blocking);
        }

        public Set<Finding> findings() { return findings; }
        public Set<Finding> blockingFindings() { return blocking; }
        public boolean isCompromised() { return !blocking.isEmpty(); }

        /** Reason string for DeviceException / audit; contains finding names only. */
        public String reason() { return "INTEGRITY_FAILURE: " + blocking; }
    }

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
            "/vendor/bin/su", "/su/bin/su", "/data/local/bin/su", "/data/local/xbin/su"};
    private static final String[] MAGISK_PATHS = {
            "/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules", "/system/bin/magisk"};
    private static final String[] ROOT_PACKAGES = {
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "com.noshufou.android.su", "com.thirdparty.superuser", "com.yellowes.su",
            "me.weishu.kernelsu", "io.github.vvb2060.magisk"};
    private static final String[] XPOSED_CLASSES = {
            "de.robv.android.xposed.XposedBridge", "org.lsposed.lspd.core.Main"};
    private static final String[] XPOSED_PACKAGES = {
            "de.robv.android.xposed.installer", "org.lsposed.manager", "io.va.exposed"};
    private static final int FRIDA_DEFAULT_PORT = 27042;

    private final EnvProbe probe;
    private final Set<Finding> blocking;

    public EnvironmentIntegrityChecker(EnvProbe probe) {
        this(probe, defaultBlocking());
    }

    public EnvironmentIntegrityChecker(EnvProbe probe, Set<Finding> blocking) {
        this.probe = probe;
        this.blocking = blocking;
    }

    public Result check() {
        Set<Finding> f = new LinkedHashSet<>();

        String tags = probe.buildTags();
        if (tags != null && tags.contains("test-keys")) f.add(Finding.ROOT_TEST_KEYS);

        for (String p : SU_PATHS) if (probe.fileExists(p)) { f.add(Finding.ROOT_SU_BINARY); break; }
        for (String p : MAGISK_PATHS) if (probe.fileExists(p)) { f.add(Finding.ROOT_MAGISK); break; }
        for (String pkg : ROOT_PACKAGES) if (probe.isPackageInstalled(pkg)) { f.add(Finding.ROOT_PACKAGE); break; }

        if (probe.isAdbEnabled()) f.add(Finding.ADB_ENABLED);
        if (probe.isDebuggerAttached()) f.add(Finding.DEBUGGER_ATTACHED);

        for (String c : XPOSED_CLASSES) if (probe.classExists(c)) { f.add(Finding.HOOK_XPOSED); break; }
        for (String pkg : XPOSED_PACKAGES) if (probe.isPackageInstalled(pkg)) { f.add(Finding.HOOK_XPOSED); break; }
        for (String m : probe.loadedModules()) {
            String l = m == null ? "" : m.toLowerCase();
            // Tightened (ADR-010 / 4.8): avoid matching standalone "gmain" which occurs in legitimate GLib
            if (l.contains("frida") || l.contains("gum-js-loop") || (l.contains("gmain") && l.contains("gum"))) {
                f.add(Finding.HOOK_FRIDA);
            }
            if (l.contains("xposed") || l.contains("lspd") || l.contains("edxp")) f.add(Finding.HOOK_XPOSED);
        }
        if (probe.isLocalPortOpen(FRIDA_DEFAULT_PORT)) f.add(Finding.HOOK_FRIDA);

        if (isEmulator()) f.add(Finding.EMULATOR);

        Set<Finding> b = new LinkedHashSet<>(f);
        b.retainAll(blocking);
        return new Result(f, b);
    }

    private boolean isEmulator() {
        String qemu = probe.systemProperty("ro.kernel.qemu");
        if ("1".equals(qemu)) return true;
        String hw = lower(probe.systemProperty("ro.hardware"));
        if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox")) return true;
        String product = lower(probe.systemProperty("ro.product.model"));
        if (product.contains("emulator") || product.contains("android sdk built for")) return true;
        String fp = lower(probe.systemProperty("ro.build.fingerprint"));
        return fp.startsWith("generic") || fp.contains("generic_x86") || fp.contains("/sdk_gphone");
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(); }
}
