package liveness.device;

import liveness.core.FaceFrame;

/**
 * Abstraction over frame sources: MDS/SBI device, host webcam,
 * CameraX, MockMDS. Implementations are M2+ except MockDeviceAdapter.
 */
public interface DeviceAdapter {
    /** Opens/connects the device. */
    void open() throws DeviceException;

    /** Captures one frame. */
    FaceFrame captureFrame() throws DeviceException;

    /** Closes/disconnects the device. Idempotent. */
    void close();

    /** True if the device is currently open. */
    default boolean isOpen() { return false; }
}
