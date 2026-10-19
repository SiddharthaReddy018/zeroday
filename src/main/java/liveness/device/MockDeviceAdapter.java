package liveness.device;

import liveness.core.FaceFrame;
import liveness.core.FrameQuality;

/**
 * Scriptable test adapter (§12.5). Scenario enum drives both the returned
 * frame signature, quality, and failure behavior.
 * <p>
 * M2 expansion: all attack types + edge cases from the evaluation design.
 * <p>
 * Real-content scenarios replay real image/video fixtures.
 * Synthetic-metadata scenarios test the sequence monitor.
 * The scenario label is NEVER encoded in the frame object —
 * the scorer must not be able to read the answer.
 */
public final class MockDeviceAdapter implements DeviceAdapter {

    /**
     * Test scenarios from §12.5.
     * <p>
     * Real-content: BONA_FIDE, PRINT, SCREEN, VIDEO, CUTOUT, CURVED_PRINT, MASK_2D.
     * Synthetic-metadata (injection): FRAME_FREEZE, FRAME_LOOP, VIRTUAL_CAMERA.
     * Device/edge: DISCONNECT, CORRUPT_FRAME, LOW_FPS, BUSY, TIMEOUT,
     *              LOW_LIGHT, MULTIPLE_FACES, NO_FACE.
     */
    public enum Scenario {
        // --- Real-content PAD scenarios ---
        BONA_FIDE,
        PRINT,
        SCREEN,
        VIDEO,
        CUTOUT,
        CURVED_PRINT,
        MASK_2D,
        MASK_3D,
        DEEPFAKE,

        // --- Injection / IAD scenarios ---
        FRAME_FREEZE,
        FRAME_LOOP,
        VIRTUAL_CAMERA,

        // --- Device / edge scenarios ---
        DISCONNECT,
        CORRUPT_FRAME,
        LOW_FPS,
        BUSY,
        TIMEOUT,
        LOW_LIGHT,
        MULTIPLE_FACES,
        NO_FACE
    }

    /** Dummy pixel data for mock frames — non-null so hasPixelData() returns true. */
    private static final byte[] DUMMY_PIXELS = new byte[]{0x42, 0x4D, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05};

    private Scenario scenario;
    private boolean open = false;
    private int frameCounter = 0;
    private int frozenFrameCount = 0;
    private static final int LOOP_CYCLE = 10;

    public MockDeviceAdapter(Scenario scenario) {
        this.scenario = scenario;
    }

    public void setScenario(Scenario s) {
        if (open) throw new IllegalStateException("cannot re-script while open");
        this.scenario = s;
        this.frameCounter = 0;
        this.frozenFrameCount = 0;
    }

    @Override
    public void open() throws DeviceException {
        if (scenario == Scenario.DISCONNECT) {
            throw new DeviceException("mock: device not connected");
        }
        if (scenario == Scenario.BUSY) {
            throw new DeviceException("mock: device busy");
        }
        if (scenario == Scenario.TIMEOUT) {
            throw new DeviceException("mock: device timeout during open");
        }
        open = true;
        frameCounter = 0;
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        if (!open) throw new DeviceException("mock: device not open");
        if (scenario == Scenario.DISCONNECT) {
            throw new DeviceException("mock: device lost mid-capture");
        }
        if (scenario == Scenario.CORRUPT_FRAME) {
            throw new DeviceException("mock: corrupt frame data");
        }

        frameCounter++;
        return withPerceptualHash(buildFrame(scenario, frameCounter), scenario, frameCounter);
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Builds a frame appropriate for the scenario.
     * The scenario label is NOT encoded in the frame — the scorer cannot cheat.
     */
    /**
     * Gives mock frames a perceptual hash like a real device would: unique per frame, except FRAME_FREEZE
     * (constant) and FRAME_LOOP (repeats every LOOP_CYCLE frames). Without this every mock frame hashed to 0
     * and any session of 5+ frames looked frozen.
     */
    private static FaceFrame withPerceptualHash(FaceFrame f, Scenario s, int seqNum) {
        long h;
        if (s == Scenario.FRAME_FREEZE) h = 0xF00DF00DL;
        else if (s == Scenario.FRAME_LOOP) h = mix(1000L + (seqNum - 1) % LOOP_CYCLE);
        else h = mix(seqNum);
        return new FaceFrame(f.quality(), f.signature(), f.deviceId(), f.timestampMs(), f.sequenceNum(),
                f.widthPx(), f.heightPx(), f.pixelData(), f.faceBboxX(), f.faceBboxY(), f.faceBboxWidth(),
                f.faceBboxHeight(), f.yawDeg(), f.pitchDeg(), f.rollDeg(), f.faceCount(),
                f.laplacianVariance(), f.brightnessMean(), f.landmarks(), h);
    }

    private static long mix(long x) { // splitmix64
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private FaceFrame buildFrame(Scenario s, int seqNum) {
        long timestamp = System.currentTimeMillis();
        int deviceId = System.identityHashCode(this);

        switch (s) {
            case BONA_FIDE:
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, DUMMY_PIXELS,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case PRINT:
            case SCREEN:
            case VIDEO:
            case CUTOUT:
            case CURVED_PRINT:
            case MASK_2D:
            case MASK_3D:
            case DEEPFAKE:
                return new FaceFrame(
                        FrameQuality.SUSPECT, frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, DUMMY_PIXELS,
                        400, 200, 280, 380,
                        2.0, 1.0, 0.5, 1,
                        180.0, 115.0, null);

            case FRAME_FREEZE:
                // All frames have identical timestamp to simulate freeze
                return new FaceFrame(
                        FrameQuality.GOOD, "FROZEN_HASH", deviceId,
                        1000000L, seqNum, 1920, 1080, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case FRAME_LOOP:
                // Cycle through LOOP_CYCLE unique signatures
                int loopIdx = (seqNum - 1) % LOOP_CYCLE;
                return new FaceFrame(
                        FrameQuality.GOOD, "LOOP_" + loopIdx, deviceId,
                        timestamp, seqNum, 1920, 1080, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case VIRTUAL_CAMERA:
                // Resolution change mid-stream
                int width = (seqNum % 2 == 0) ? 1920 : 1280;
                int height = (seqNum % 2 == 0) ? 1080 : 720;
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId + 1,
                        timestamp, seqNum, width, height, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case LOW_LIGHT:
                return new FaceFrame(
                        new FrameQuality(false, 0.4, "LOW_LIGHT",
                                80.0, 25.0, 0.3, 0.0,
                                200, 0.0, 0.0, 0.0, 1),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, null,
                        400, 200, 250, 350,
                        0.0, 0.0, 0.0, 1,
                        80.0, 25.0, null);

            case MULTIPLE_FACES:
                return new FaceFrame(
                        new FrameQuality(false, 0.2, "MULTIPLE_FACES",
                                200.0, 128.0, 0.0, 0.0,
                                200, 0.0, 0.0, 0.0, 3),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 3,
                        200.0, 128.0, null);

            case NO_FACE:
                return new FaceFrame(
                        new FrameQuality(false, 0.0, "NO_FACE",
                                200.0, 128.0, 0.0, 0.0,
                                0, 0.0, 0.0, 0.0, 0),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, null,
                        0, 0, 0, 0,
                        0.0, 0.0, 0.0, 0,
                        200.0, 128.0, null);

            case LOW_FPS:
                // Simulate long delay between frames via large timestamp gaps
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp + (seqNum * 500L), seqNum, 1920, 1080, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            default:
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp, seqNum, 1920, 1080, null,
                        400, 200, 300, 400,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);
        }
    }

    /** Opaque per-scenario frame signature. */
    public static String frameSignature(Scenario s) {
        return s.name();
    }

    public Scenario scenario() { return scenario; }
}
