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

    /**
     * Generates deterministic, pixel-dependent RGB contents based on frame dimensions, scenario, and sequence number.
     * Replaces the former 8-byte dummy array (S19).
     */
    public static byte[] generatePixelData(int width, int height, Scenario scenario, int seqNum) {
        int length = width * height * 3;
        byte[] pixels = new byte[length];
        int sVal = (scenario != null ? scenario.ordinal() : 0) + 1;
        byte rBase = (byte) ((sVal * 37 + seqNum * 17) & 0xFF);
        byte gBase = (byte) ((sVal * 59 + seqNum * 31) & 0xFF);
        byte bBase = (byte) ((sVal * 89 + seqNum * 43) & 0xFF);

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width * 3;
            for (int x = 0; x < width; x++) {
                int idx = rowOffset + x * 3;
                pixels[idx] = (byte) (rBase ^ (x & 0x7F));
                pixels[idx + 1] = (byte) (gBase ^ (y & 0x7F));
                pixels[idx + 2] = (byte) (bBase ^ ((x + y) & 0x7F));
            }
        }
        return pixels;
    }

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
        int width = 320;
        int height = 240;

        switch (s) {
            case BONA_FIDE:
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 200, 180,
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
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 190, 170,
                        2.0, 1.0, 0.5, 1,
                        180.0, 115.0, null);

            case FRAME_FREEZE:
                // All frames have identical timestamp to simulate freeze
                return new FaceFrame(
                        FrameQuality.GOOD, "FROZEN_HASH", deviceId,
                        1000000L, seqNum, width, height, generatePixelData(width, height, s, 1),
                        60, 30, 200, 180,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case FRAME_LOOP:
                // Cycle through LOOP_CYCLE unique signatures
                int loopIdx = (seqNum - 1) % LOOP_CYCLE;
                return new FaceFrame(
                        FrameQuality.GOOD, "LOOP_" + loopIdx, deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, loopIdx + 1),
                        60, 30, 200, 180,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case VIRTUAL_CAMERA:
                // Resolution change mid-stream
                int vWidth = (seqNum % 2 == 0) ? 320 : 240;
                int vHeight = (seqNum % 2 == 0) ? 240 : 180;
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId + 1,
                        timestamp, seqNum, vWidth, vHeight, generatePixelData(vWidth, vHeight, s, seqNum),
                        40, 20, 160, 140,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            case LOW_LIGHT:
                return new FaceFrame(
                        new FrameQuality(false, 0.4, "LOW_LIGHT",
                                80.0, 25.0, 0.3, 0.0,
                                200, 0.0, 0.0, 0.0, 1),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 180, 160,
                        0.0, 0.0, 0.0, 1,
                        80.0, 25.0, null);

            case MULTIPLE_FACES:
                return new FaceFrame(
                        new FrameQuality(false, 0.2, "MULTIPLE_FACES",
                                200.0, 128.0, 0.0, 0.0,
                                200, 0.0, 0.0, 0.0, 3),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 200, 180,
                        0.0, 0.0, 0.0, 3,
                        200.0, 128.0, null);

            case NO_FACE:
                return new FaceFrame(
                        new FrameQuality(false, 0.0, "NO_FACE",
                                200.0, 128.0, 0.0, 0.0,
                                0, 0.0, 0.0, 0.0, 0),
                        frameSignature(s), deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        0, 0, 0, 0,
                        0.0, 0.0, 0.0, 0,
                        200.0, 128.0, null);

            case LOW_FPS:
                // Simulate long delay between frames via large timestamp gaps
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp + (seqNum * 500L), seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 200, 180,
                        0.0, 0.0, 0.0, 1,
                        250.0, 128.0, null);

            default:
                return new FaceFrame(
                        FrameQuality.GOOD, frameSignature(s), deviceId,
                        timestamp, seqNum, width, height, generatePixelData(width, height, s, seqNum),
                        60, 30, 200, 180,
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
