package liveness.android;

import liveness.core.FaceFrame;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;

import java.util.Objects;

/**
 * The single DeviceAdapter handed to LivenessService on Android.
 * <ul>
 *   <li>open(): integrity check first (fail closed), then CameraX, then SBI discovery.</li>
 *   <li>captureFrame(): host CameraX frames (T3) for POSITIONING/PASSIVE/CHALLENGE.</li>
 *   <li>captureFinal(): SBI rCapture (T1) with cross-stream binding.</li>
 * </ul>
 */
public final class AndroidDeviceAdapter implements DeviceAdapter {

    private final EnvironmentIntegrityChecker integrity;
    private final CameraXDeviceAdapter camera;
    private final SbiIntentDeviceAdapter sbi;

    public AndroidDeviceAdapter(EnvironmentIntegrityChecker integrity,
                                CameraXDeviceAdapter camera, SbiIntentDeviceAdapter sbi) {
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.sbi = Objects.requireNonNull(sbi, "sbi");
    }

    @Override
    public void open() throws DeviceException {
        EnvironmentIntegrityChecker.Result r = integrity.check();
        if (r.isCompromised()) throw new DeviceException(r.reason());
        camera.open();
        try {
            sbi.open();
        } catch (DeviceException e) {
            camera.close();
            throw e;
        }
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException { return camera.captureFrame(); }

    @Override
    public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        return sbi.captureFinal(boundTransactionId);
    }

    @Override
    public String lastReturnedTransactionId() { return sbi.lastReturnedTransactionId(); }

    @Override
    public String captureMode() { return sbi.captureMode(); }

    @Override
    public void close() {
        camera.close();
        sbi.close();
    }

    @Override
    public boolean isOpen() { return camera.isOpen() && sbi.isOpen(); }

    public CameraXDeviceAdapter camera() { return camera; }
}
