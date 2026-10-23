package liveness.android;

import liveness.capture.FrameFingerprint;
import liveness.core.FaceFrame;
import liveness.core.FrameQuality;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host-camera (tier T3) frame source for Android CameraX.
 * <p>
 * Pure Java: the Android glue ({@code CameraXAnalyzer implements ImageAnalysis.Analyzer})
 * copies the three YUV_420_888 planes into a {@link YuvFrame}, calls
 * {@link #onYuvFrame(YuvFrame)} and closes the ImageProxy in a finally block.
 * This class therefore never holds a camera buffer, so nothing can leak or stall
 * the camera pipeline.
 * <p>
 * Threading: {@code onYuvFrame} runs on the CameraX analyzer executor and never blocks
 * (bounded queue, drop-oldest). {@code captureFrame} runs on the engine thread and does
 * the YUV-to-RGB conversion + metrics, keeping the analyzer fast.
 * <p>
 * The {@code signature} of every frame is a STABLE per-camera identity string:
 * the session feeds it to DeviceIdentityValidator, and a changing value would be
 * flagged DEVICE_IDENTITY_CHANGED.
 * <p>
 * Final accepted images never come from here (T1 rCapture only).
 */
public final class CameraXDeviceAdapter implements DeviceAdapter {

    public static final int DEFAULT_QUEUE_CAPACITY = 3;
    public static final long DEFAULT_FRAME_TIMEOUT_MS = 2000L;
    /** Cadence of frames reaching the engine (inference-bound on low-end phones), not the sensor rate. */
    public static final long DEFAULT_EXPECTED_INTERVAL_MS = 200L;

    private final String cameraId;
    private final FaceDetector detector;
    private final BlockingQueue<YuvFrame> queue;
    private final long frameTimeoutMs;
    private final long expectedIntervalMs;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicInteger sequence = new AtomicInteger(0);
    private final AtomicInteger dropped = new AtomicInteger(0);
    private volatile FaceFrame latest;

    public CameraXDeviceAdapter(String cameraId, FaceDetector detector) {
        this(cameraId, detector, DEFAULT_QUEUE_CAPACITY, DEFAULT_FRAME_TIMEOUT_MS);
    }

    public CameraXDeviceAdapter(String cameraId, FaceDetector detector,
                                int queueCapacity, long frameTimeoutMs) {
        this(cameraId, detector, queueCapacity, frameTimeoutMs, DEFAULT_EXPECTED_INTERVAL_MS);
    }

    public CameraXDeviceAdapter(String cameraId, FaceDetector detector,
                                int queueCapacity, long frameTimeoutMs, long expectedIntervalMs) {
        this.cameraId = Objects.requireNonNull(cameraId, "cameraId");
        this.detector = Objects.requireNonNull(detector, "detector");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity");
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.frameTimeoutMs = frameTimeoutMs;
        this.expectedIntervalMs = expectedIntervalMs;
    }

    @Override
    public void open() throws DeviceException {
        queue.clear();
        sequence.set(0);
        dropped.set(0);
        latest = null;
        open.set(true);
    }

    /** Called from the CameraX analyzer thread. Never blocks; returns false if closed. */
    public boolean onYuvFrame(YuvFrame f) {
        if (!open.get() || f == null) return false;
        while (!queue.offer(f)) {
            if (queue.poll() != null) dropped.incrementAndGet();
        }
        return true;
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        if (!open.get()) throw new DeviceException("camerax: adapter not open");
        YuvFrame f = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(frameTimeoutMs);
        try {
            // Poll in short slices so close() wakes a blocked consumer promptly.
            while (open.get()) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) break;
                f = queue.poll(Math.min(remainingMs, 25L), TimeUnit.MILLISECONDS);
                if (f != null) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeviceException("camerax: interrupted waiting for frame", e);
        }
        if (!open.get()) throw new DeviceException("camerax: closed while waiting");
        if (f == null) throw new DeviceException("camerax: no frame within " + frameTimeoutMs + " ms");

        final long tCapture = StageTimer.start();
        StageTimer.record(StageTimer.Stage.QUEUE_AGE, System.nanoTime() - f.arrivalNanos);
        try {
            long t = StageTimer.start();
            byte[] rgb = YuvConverter.toRgb(f);
            StageTimer.stop(StageTimer.Stage.YUV_TO_RGB, t);
            int w = YuvConverter.outputWidth(f);
            int h = YuvConverter.outputHeight(f);
            t = StageTimer.start();
            double[] luma = lumaStats(f);
            StageTimer.stop(StageTimer.Stage.LUMA_STATS, t);
            t = StageTimer.start();
            FaceDetector.Observation o = detector.detect(rgb, w, h);
            StageTimer.stop(StageTimer.Stage.DETECT, t);
            if (o == null) o = FaceDetector.Observation.NONE;

            FrameQuality q = o.faceCount == 1 ? FrameQuality.GOOD
                    : new FrameQuality(false, 0.0, o.faceCount == 0 ? "NO_FACE" : "MULTIPLE_FACES",
                    luma[0], luma[1], 0.0, 0.0, Math.max(o.bboxW, o.bboxH),
                    o.yawDeg, o.pitchDeg, o.rollDeg, o.faceCount);

            t = StageTimer.start();
            long pHash = perceptualHash(f);
            StageTimer.stop(StageTimer.Stage.PHASH, t);
            t = StageTimer.start();
            FaceFrame out = new FaceFrame(
                    q, "camerax:" + cameraId, cameraId.hashCode(),
                    f.timestampMs, sequence.incrementAndGet(),
                    w, h, rgb,
                    o.bboxX, o.bboxY, o.bboxW, o.bboxH,
                    o.yawDeg, o.pitchDeg, o.rollDeg, o.faceCount,
                    luma[0], luma[1], o.landmarks, pHash);
            StageTimer.stop(StageTimer.Stage.FACE_FRAME, t);
            latest = out;
            StageTimer.stop(StageTimer.Stage.CAPTURE_TOTAL, tCapture);
            return out;
        } catch (RuntimeException e) {
            throw new DeviceException("camerax: frame conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        open.set(false);
        queue.clear();
    }

    @Override
    public boolean isOpen() { return open.get(); }

    @Override
    public long expectedFrameIntervalMs() { return expectedIntervalMs; }

    /** Most recent frame handed to the engine (null before the first). */
    public FaceFrame latestFrame() { return latest; }

    public int droppedFrames() { return dropped.get(); }

    /** 64-bit pHash of the luma plane (sampled to 64x64) for freeze/loop detection. */
    static long perceptualHash(YuvFrame f) {
        final int g = 64;
        byte[] grid = new byte[g * g];
        for (int r = 0; r < g; r++) {
            int sr = Math.min(f.height - 1, r * f.height / g);
            for (int c = 0; c < g; c++) {
                int sc = Math.min(f.width - 1, c * f.width / g);
                grid[r * g + c] = f.y[sr * f.yRowStride + sc];
            }
        }
        return FrameFingerprint.computePHash(grid, g, g);
    }

    /** Returns {laplacianVariance, brightnessMean} computed on the Y plane (step 2 subsample). */
    static double[] lumaStats(YuvFrame f) {
        int w = f.width, h = f.height;
        double sum = 0;
        long n = 0;
        for (int r = 0; r < h; r += 2) {
            for (int c = 0; c < w; c += 2) {
                sum += f.y[r * f.yRowStride + c] & 0xFF;
                n++;
            }
        }
        double mean = n == 0 ? 0 : sum / n;

        double lsum = 0, lsq = 0;
        long ln = 0;
        for (int r = 2; r < h - 2; r += 2) {
            for (int c = 2; c < w - 2; c += 2) {
                int i = r * f.yRowStride + c;
                int lap = 4 * (f.y[i] & 0xFF)
                        - (f.y[i - 1] & 0xFF) - (f.y[i + 1] & 0xFF)
                        - (f.y[i - f.yRowStride] & 0xFF) - (f.y[i + f.yRowStride] & 0xFF);
                lsum += lap;
                lsq += (double) lap * lap;
                ln++;
            }
        }
        double var = 0;
        if (ln > 0) {
            double m = lsum / ln;
            var = lsq / ln - m * m;
        }
        return new double[]{var, mean};
    }
}
