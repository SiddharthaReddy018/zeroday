package liveness.engine;

import liveness.core.FaceFrame;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded asynchronous inference pipeline (§26.1).
 * <p>
 * The {@link liveness.device.DeviceAdapter} pushes frames into the queue.
 * A dedicated {@link InferenceWorker} thread pulls the latest frame,
 * dropping stale ones, and processes them at whatever FPS the hardware
 * can sustain. The UI preview remains perfectly fluid; memory does not
 * grow unbounded.
 * <p>
 * Heavy ONNX inference MUST NOT run on the UI/Camera thread.
 */
public final class BoundedFrameQueue {

    /** Default queue capacity — keeps memory bounded. */
    private static final int DEFAULT_CAPACITY = 4;

    private final BlockingQueue<FaceFrame> queue;
    private final AtomicReference<FaceFrame> latestFrame;
    private final AtomicBoolean closed;

    public BoundedFrameQueue() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedFrameQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.latestFrame = new AtomicReference<>();
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Pushes a frame from the camera/device thread.
     * If the queue is full, the oldest frame is dropped (non-blocking).
     *
     * @param frame the captured frame (never null)
     */
    public void push(FaceFrame frame) {
        if (closed.get()) {
            return;
        }
        latestFrame.set(frame);
        // Non-blocking: drop oldest if full
        if (!queue.offer(frame)) {
            queue.poll(); // drop oldest
            queue.offer(frame);
        }
    }

    /**
     * Takes the next frame for inference, blocking until one is available
     * or the queue is closed.
     *
     * @return the next frame, or null if closed
     */
    public FaceFrame take() {
        if (closed.get()) {
            return null;
        }
        try {
            return queue.poll(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Takes the latest frame, skipping any older queued frames.
     * Use this for inference that only needs the most recent data.
     *
     * @return the latest frame, or null if none available
     */
    public FaceFrame takeLatest() {
        // Drain queue to discard stale frames
        queue.clear();
        return latestFrame.getAndSet(null);
    }

    /**
     * Returns the number of frames currently queued.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns true if the queue has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the queue. After this, {@link #push} is a no-op and
     * {@link #take} returns null.
     */
    public void close() {
        closed.set(true);
        queue.clear();
    }
}
