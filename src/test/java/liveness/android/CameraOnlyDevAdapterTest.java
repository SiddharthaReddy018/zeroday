package liveness.android;

import liveness.core.FaceFrame;
import liveness.device.DeviceException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static liveness.android.AndroidTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraOnlyDevAdapterTest {

    private static final class Probe implements EnvProbe {
        private final boolean adb;
        Probe(boolean adb) { this.adb = adb; }
        public String buildTags() { return "release-keys"; }
        public String systemProperty(String key) { return null; }
        public boolean fileExists(String path) { return false; }
        public boolean isPackageInstalled(String packageName) { return false; }
        public boolean isAdbEnabled() { return adb; }
        public boolean isDebuggerAttached() { return false; }
        public List<String> loadedModules() { return Collections.emptyList(); }
        public boolean classExists(String className) { return false; }
        public boolean isLocalPortOpen(int port) { return false; }
    }

    private CameraOnlyDevAdapter adapter() throws Exception {
        CameraXDeviceAdapter cam = new CameraXDeviceAdapter("cam0", oneFace(), 3, 500);
        return new CameraOnlyDevAdapter(EnvironmentIntegrityChecker.forDevelopment(new Probe(true), true), cam);
    }

    @Test
    void reportsDevOnlyCaptureModeAndCameraCadence() throws Exception {
        CameraOnlyDevAdapter a = adapter();
        assertEquals("HOST_CAMERA_DEV_ONLY", a.captureMode());
        assertEquals(a.camera().expectedFrameIntervalMs(), a.expectedFrameIntervalMs());
        assertNull(a.lastReturnedTransactionId());
    }

    @Test
    void opensCapturesAndClosesWithoutSbi() throws Exception {
        CameraOnlyDevAdapter a = adapter();
        a.open();
        assertTrue(a.isOpen());
        assertTrue(a.camera().onYuvFrame(texturedFrame(128, 96, 1000L, 0, 1)));
        FaceFrame f = a.captureFinal("base:nonce");
        assertEquals(128, f.widthPx());
        a.close();
        assertFalse(a.isOpen());
    }

    @Test
    void captureBeforeOpenFails() throws Exception {
        CameraOnlyDevAdapter a = adapter();
        assertThrows(DeviceException.class, () -> a.captureFinal("base:nonce"));
    }
}
