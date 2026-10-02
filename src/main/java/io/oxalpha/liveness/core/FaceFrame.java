package io.oxalpha.liveness.core;

import java.util.Objects;

/**
 * Immutable value holding one captured face frame's metadata.
 * The actual pixel data is NOT stored here — only a quality hint,
 * a device-opaque signature (for mock scenarios), and a device ID.
 * STUB: M2 adds raw pixel reference, landmarks, OFIQ measures.
 */
public final class FaceFrame {

    private final FrameQuality quality;
    private final String signature;
    private final int deviceId;

    public FaceFrame(FrameQuality quality, String signature, int deviceId) {
        this.quality = Objects.requireNonNull(quality, "quality");
        this.signature = Objects.requireNonNull(signature, "signature");
        this.deviceId = deviceId;
    }

    public FrameQuality quality() { return quality; }
    public String signature()     { return signature; }
    public int deviceId()         { return deviceId; }

    @Override
    public String toString() {
        return "FaceFrame{" + quality + ", sig=" + signature + ", dev=" + deviceId + "}";
    }
}
