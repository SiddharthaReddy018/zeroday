package liveness.engine;

import liveness.core.FaceFrame;
import liveness.flow.LivenessSession;
import liveness.flow.LivenessState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Dedicated inference worker thread (§26.1).
 * <p>
 * Pulls frames from a {@link BoundedFrameQueue}, runs them through
 * {@link LivenessSession#processFrame(FaceFrame)}, and notifies the
 * UI via a callback. Heavy inference never touches the UI/Camera thread.
 */
public final class InferenceWorker implements Runnable {

    private final BoundedFrameQueue queue;
    private final LivenessSession session;
    private final Consumer<InferenceResult> callback;
    private final AtomicBoolean running;
    private Thread workerThread;

    /**
     * @param queue    the bounded frame queue to pull from
     * @param session  the liveness session to process frames through
     * @param callback receives results on the worker thread (must dispatch to UI thread)
     */
    public InferenceWorker(BoundedFrameQueue queue,
                           LivenessSession session,
                           Consumer<InferenceResult> callback) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.session = Objects.requireNonNull(session, "session");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the worker on a new daemon thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this, "liveness-inference-worker");
            workerThread.setDaemon(true);
            workerThread.start();
        }
    }

    /**
     * Stops the worker. Blocks until the thread exits (up to 2 seconds).
     */
    public void stop() {
        running.set(false);
        queue.close();
        if (workerThread != null) {
            try {
                workerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns true if the worker is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void run() {
        while (running.get() && !queue.isClosed()) {
            FaceFrame frame = queue.take();
            if (frame == null) {
                continue; // timeout or closed
            }

            try {
                LivenessState state = session.processFrame(frame);
                callback.accept(new InferenceResult(state, frame, null));

                // Stop if terminal state reached
                if (state.isTerminal()) {
                    running.set(false);
                    break;
                }
            } catch (Exception e) {
                callback.accept(new InferenceResult(
                        session.currentState(), frame, e));
            }
        }
    }

    /**
     * Result of processing a frame through the inference pipeline.
     */
    public static final class InferenceResult {
        private final LivenessState state;
        private final FaceFrame frame;
        private final Exception error;

        public InferenceResult(LivenessState state, FaceFrame frame, Exception error) {
            this.state = state;
            this.frame = frame;
            this.error = error;
        }

        public LivenessState state()    { return state; }
        public FaceFrame frame()        { return frame; }
        public Exception error()        { return error; }
        public boolean hasError()       { return error != null; }
        public boolean isTerminal()     { return state != null && state.isTerminal(); }
    }
}
