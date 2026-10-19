package liveness.android;

import liveness.android.EnvironmentIntegrityChecker.Finding;
import liveness.device.DeviceException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static liveness.android.AndroidTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentIntegrityCheckerTest {

    private static EnvironmentIntegrityChecker.Result run(FakeProbe p) {
        return new EnvironmentIntegrityChecker(p).check();
    }

    @Test
    void cleanProductionDevicePasses() {
        EnvironmentIntegrityChecker.Result r = run(new FakeProbe());
        assertFalse(r.isCompromised());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void testKeysBuildIsFlagged() {
        FakeProbe p = new FakeProbe();
        p.tags = "release-keys,test-keys";
        assertTrue(run(p).findings().contains(Finding.ROOT_TEST_KEYS));
    }

    @Test
    void eachCommonSuPathIsFlagged() {
        for (String path : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su"}) {
            FakeProbe p = new FakeProbe();
            p.files.add(path);
            EnvironmentIntegrityChecker.Result r = run(p);
            assertTrue(r.findings().contains(Finding.ROOT_SU_BINARY), path);
            assertTrue(r.isCompromised(), path);
        }
    }

    @Test
    void magiskArtefactsAndRootManagersAreFlagged() {
        FakeProbe p = new FakeProbe();
        p.files.add("/data/adb/magisk");
        assertTrue(run(p).findings().contains(Finding.ROOT_MAGISK));
        FakeProbe q = new FakeProbe();
        q.packages.add("com.topjohnwu.magisk");
        assertTrue(run(q).findings().contains(Finding.ROOT_PACKAGE));
        FakeProbe r = new FakeProbe();
        r.packages.add("eu.chainfire.supersu");
        assertTrue(run(r).findings().contains(Finding.ROOT_PACKAGE));
    }

    @Test
    void adbAndDebuggerAreFlagged() {
        FakeProbe p = new FakeProbe();
        p.adb = true;
        EnvironmentIntegrityChecker.Result r = run(p);
        assertTrue(r.findings().contains(Finding.ADB_ENABLED));
        assertTrue(r.isCompromised());
        FakeProbe q = new FakeProbe();
        q.debugger = true;
        assertTrue(run(q).findings().contains(Finding.DEBUGGER_ATTACHED));
    }

    @Test
    void xposedIndicatorsAreFlagged() {
        FakeProbe p = new FakeProbe();
        p.classes.add("de.robv.android.xposed.XposedBridge");
        assertTrue(run(p).findings().contains(Finding.HOOK_XPOSED));
        FakeProbe q = new FakeProbe();
        q.modules.add("/data/adb/lspd/liblspd.so");
        assertTrue(run(q).findings().contains(Finding.HOOK_XPOSED));
        FakeProbe r = new FakeProbe();
        r.packages.add("org.lsposed.manager");
        assertTrue(run(r).findings().contains(Finding.HOOK_XPOSED));
    }

    @Test
    void fridaIndicatorsAreFlagged() {
        FakeProbe p = new FakeProbe();
        p.modules.add("/data/local/tmp/frida-agent-64.so");
        assertTrue(run(p).findings().contains(Finding.HOOK_FRIDA));
        FakeProbe q = new FakeProbe();
        q.ports.add(27042);
        assertTrue(run(q).findings().contains(Finding.HOOK_FRIDA));
        FakeProbe r = new FakeProbe();
        r.modules.add("/data/app/FRIDA-Gadget.so"); // case-insensitive
        assertTrue(run(r).findings().contains(Finding.HOOK_FRIDA));
    }

    @Test
    void emulatorPropertiesAreFlagged() {
        String[][] cases = {
                {"ro.kernel.qemu", "1"},
                {"ro.hardware", "goldfish"},
                {"ro.hardware", "ranchu"},
                {"ro.product.model", "Android SDK built for x86"},
                {"ro.build.fingerprint", "generic/sdk_gphone64_x86_64/emu:14/..."},
        };
        for (String[] c : cases) {
            FakeProbe p = new FakeProbe();
            p.props.put(c[0], c[1]);
            assertTrue(run(p).findings().contains(Finding.EMULATOR), c[0] + "=" + c[1]);
        }
    }

    @Test
    void realDevicePropertiesAreNotEmulator() {
        FakeProbe p = new FakeProbe();
        p.props.put("ro.hardware", "qcom");
        p.props.put("ro.product.model", "SM-A145F");
        p.props.put("ro.build.fingerprint", "samsung/a14nsxx/a14:13/TP1A/A145FXXU:user/release-keys");
        assertFalse(run(p).findings().contains(Finding.EMULATOR));
    }

    @Test
    void blockingPolicyCanDowngradeDevFindingsButStillReportsThem() {
        FakeProbe p = new FakeProbe();
        p.adb = true;
        EnvironmentIntegrityChecker c = new EnvironmentIntegrityChecker(p, EnumSet.of(Finding.ROOT_SU_BINARY));
        EnvironmentIntegrityChecker.Result r = c.check();
        assertTrue(r.findings().contains(Finding.ADB_ENABLED));
        assertFalse(r.isCompromised());
    }

    @Test
    void multipleFindingsAreAllReported() {
        FakeProbe p = new FakeProbe();
        p.tags = "test-keys";
        p.files.add("/sbin/su");
        p.adb = true;
        EnvironmentIntegrityChecker.Result r = run(p);
        assertTrue(r.blockingFindings().containsAll(
                Arrays.asList(Finding.ROOT_TEST_KEYS, Finding.ROOT_SU_BINARY, Finding.ADB_ENABLED)));
        assertTrue(r.reason().startsWith("INTEGRITY_FAILURE"));
    }

    // ---- the composite adapter fails closed on a compromised host ----

    private static CameraXDeviceAdapter camera() { return new CameraXDeviceAdapter("c", oneFace()); }

    private static SbiIntentDeviceAdapter sbi(FakeLauncher l) {
        CrossStreamBinder b = new CrossStreamBinder(new liveness.engine.SameFaceChecker(0.1), new FirstPixelEmbedder(), 5000);
        return new SbiIntentDeviceAdapter(l, b, () -> null, x -> null, SbiJwtVerifier.disabledForDevelopment(), "Developer", "Registration", 1000);
    }

    @Test
    void compromisedHostNeverOpensCameraOrSbi() {
        FakeProbe p = new FakeProbe();
        p.files.add("/system/xbin/su");
        CameraXDeviceAdapter cam = camera();
        FakeLauncher l = new FakeLauncher();
        AndroidDeviceAdapter a = new AndroidDeviceAdapter(new EnvironmentIntegrityChecker(p), cam, sbi(l));
        DeviceException e = assertThrows(DeviceException.class, a::open);
        assertTrue(e.getMessage().startsWith("INTEGRITY_FAILURE"));
        assertFalse(cam.isOpen());
        assertTrue(l.launched.isEmpty(), "SBI must not be contacted from a compromised host");
        assertFalse(a.isOpen());
    }

    @Test
    void cleanHostOpensBoth() throws Exception {
        CameraXDeviceAdapter cam = camera();
        AndroidDeviceAdapter a = new AndroidDeviceAdapter(
                new EnvironmentIntegrityChecker(new FakeProbe()), cam, sbi(new FakeLauncher()));
        a.open();
        assertTrue(a.isOpen());
        a.close();
        assertFalse(cam.isOpen());
    }

    @Test
    void sbiDiscoveryFailureReleasesCamera() {
        CameraXDeviceAdapter cam = camera();
        FakeLauncher l = new FakeLauncher();
        l.packages = java.util.Collections.emptyList();
        AndroidDeviceAdapter a = new AndroidDeviceAdapter(new EnvironmentIntegrityChecker(new FakeProbe()), cam, sbi(l));
        assertThrows(DeviceException.class, a::open);
        assertFalse(cam.isOpen(), "camera must be released when SBI discovery fails");
    }
}
