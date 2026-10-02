package io.oxalpha.liveness.device;

import io.oxalpha.liveness.core.FaceFrame;
import io.oxalpha.liveness.core.FrameQuality;

/**
 * Scriptable test adapter. Scenario enum drives both the returned frame
 * signature and failure behavior. STUB: none for M1 — this is the
 * canonical test device; real camera adapters are M2+.
 */
public final class MockDeviceAdapter implements DeviceAdapter {

    public enum Scenario { BONA_FIDE, PRINT, SCREEN, VIDEO, DISCONNECT }

    private Scenario scenario;
    private boolean open = false;

    public MockDeviceAdapter(Scenario scenario) {
        this.scenario = scenario;
    }

    public void setScenario(Scenario s) {
        if (open) throw new IllegalStateException("cannot re-script while open");
        this.scenario = s;
    }

    @Override
    public void open() throws DeviceException {
        if (scenario == Scenario.DISCONNECT) {
            throw new DeviceException("mock: device not connected");
        }
        open = true;
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        if (!open) throw new DeviceException("mock: device not open");
        if (scenario == Scenario.DISCONNECT) {
            throw new DeviceException("mock: device lost mid-capture");
        }
        FrameQuality q = scenario == Scenario.BONA_FIDE
                ? FrameQuality.GOOD : FrameQuality.SUSPECT;
        return new FaceFrame(q, frameSignature(scenario), System.identityHashCode(this));
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /** Opaque per-scenario frame signature the ScoreEngine reads. */
    public static String frameSignature(Scenario s) {
        return s.name();
    }
}
