package liveness.android;

import liveness.core.FaceFrame;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;

import java.util.Objects;

/**
 * DEVELOPMENT ONLY. Camera-only DeviceAdapter for demos on a phone with no SBI app.
 * <ul>
 *   <li>open(): integrity check first (fail closed), then CameraX. No SBI discovery.</li>
 *   <li>captureFinal(): a fresh host-camera frame, NOT an SBI rCapture. No cross-stream binding.</li>
 *   <li>captureMode() reports HOST_CAMERA_DEV_ONLY so the attestation shows SBI was skipped.</li>
 * </ul>
 * Never wire this into LivenessAndroidBootstrap or any production path.
 */
public final class CameraOnlyDevAdapter implements DeviceAdapter {

    private final EnvironmentIntegrityChecker integrity;
    private final CameraXDeviceAdapter camera;

    public CameraOnlyDevAdapter(EnvironmentIntegrityChecker integrity, CameraXDeviceAdapter camera) {
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.camera = Objects.requireNonNull(camera, "camera");
    }

    @Override
    public void open() throws DeviceException {
        EnvironmentIntegrityChecker.Result r = integrity.check();
        if (r.isCompromised()) throw new DeviceException(r.reason());
        camera.open();
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException { return camera.captureFrame(); }

    /** Fresh frame on purpose: the service stamps the request time before this call. */
    @Override
    public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        return camera.captureFrame();
    }

    @Override
    public long expectedFrameIntervalMs() { return camera.expectedFrameIntervalMs(); }

    @Override
    public String captureMode() { return "HOST_CAMERA_DEV_ONLY"; }

    @Override
    public void close() { camera.close(); }

    @Override
    public boolean isOpen() { return camera.isOpen(); }

    public CameraXDeviceAdapter camera() { return camera; }
}
