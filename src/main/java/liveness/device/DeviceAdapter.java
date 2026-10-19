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

    /**
     * Captures the final accepted image (tier T1). {@code boundTransactionId} is the
     * nonce-bound transaction id the device must echo back (S6.4). Default: same as
     * {@link #captureFrame()} so existing adapters are unaffected. Android overrides this
     * because live frames (CameraX) and the final image (SBI rCapture) come from different sources.
     */
    default FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        return captureFrame();
    }

    /**
     * Transaction id the device returned for the last {@link #captureFinal(String)}, or null
     * if the adapter does not report one (the service then falls back to the id it sent).
     */
    default String lastReturnedTransactionId() { return null; }

    /**
     * Expected spacing between frames the engine will receive, in ms. The sequence monitor
     * flags gaps above 2x this value. Default ~30 fps; an adapter whose consumer is slower than
     * the sensor (e.g. Android inference on low-end devices) must report its real cadence.
     */
    default long expectedFrameIntervalMs() { return 33L; }

    /**
     * Capture mode recorded in the attestation (e.g. "STREAM" for MDS streaming; Android reports
     * "HOST_CAMERA+SBI_RCAPTURE_INTENT" because liveness frames and the final image come from different sources).
     */
    default String captureMode() { return "STREAM"; }

    /** Closes/disconnects the device. Idempotent. */
    void close();

    /** True if the device is currently open. */
    default boolean isOpen() { return false; }
}
