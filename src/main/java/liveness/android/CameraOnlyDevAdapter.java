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

    /** Fresh frame on purpose: the service stamps the request time before this call.
     * Waits up to 4s for a frontal face frame (yaw/pitch within 20 deg) so that completed
     * turn challenges don't submit an in-flight profile frame as the final registration image.
     * Extended from 2.5 s: on-device observation (2026-10-30) showed TURN_RIGHT finishing at
     * yaw=-44° and the 2.5 s window expiring before the face returned to frontal.
     */
    @Override
    public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        long deadline = System.currentTimeMillis() + 4000;
        FaceFrame best = null;
        while (System.currentTimeMillis() < deadline) {
            FaceFrame f = camera.captureFrame();
            if (f != null && f.faceCount() == 1) {
                best = f;
                if (Math.abs(f.yawDeg()) <= 20.0 && Math.abs(f.pitchDeg()) <= 20.0) {
                    return f;
                }
            }
            try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return best != null ? best : camera.captureFrame();
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
