package liveness.android;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Per-stage wall-time recorder for the per-frame path (P5, AUDIT_TODO X6).
 * <p>
 * Disabled by default: {@link #start()} then returns 0 and {@link #stop} returns at once, so production cost is a
 * volatile read per hook. {@link PipelineBenchmark} enables it for a measured run only.
 * <p>
 * Storage is preallocated (no allocation while measuring). Samples past the capacity are counted in
 * {@link #overflow(Stage)} and NOT stored, so a truncated series is visible rather than silent.
 * Stages are timed with {@link System#nanoTime()}, which is monotonic; resolution on ART is ~tens of ns, far below
 * the millisecond stages measured here.
 */
public final class StageTimer {

    /** Order is the order of the per-frame path; TOTAL stages contain the ones before them. */
    public enum Stage {
        /** Age of the frame (producer arrival to engine dequeue). Includes queueing delay. Paced mode only. */
        QUEUE_AGE,
        YUV_TO_RGB,
        LUMA_STATS,
        DETECT,
        PHASH,
        /** FaceFrame construction (includes the content digest over the RGB buffer). */
        FACE_FRAME,
        /** Whole captureFrame() after a frame was dequeued (excludes waiting for a frame). */
        CAPTURE_TOTAL,
        INTEGRITY,
        QUALITY,
        PREPROCESS,
        PAD_INFER,
        /** Whole session.processFrame(). Minus INTEGRITY+QUALITY+PREPROCESS+PAD_INFER = audit, state machine, rest. */
        PROCESS_TOTAL
    }

    private static final Stage[] STAGES = Stage.values();
    private static volatile boolean enabled = false;
    private static volatile long[][] samples = new long[STAGES.length][0];
    private static final AtomicIntegerArray counts = new AtomicIntegerArray(STAGES.length);
    private static final AtomicIntegerArray overflows = new AtomicIntegerArray(STAGES.length);

    private StageTimer() {}

    /** Clears all samples and enables recording with room for {@code capacityPerStage} samples per stage. */
    public static synchronized void enable(int capacityPerStage) {
        if (capacityPerStage < 1) throw new IllegalArgumentException("capacityPerStage");
        long[][] s = new long[STAGES.length][capacityPerStage];
        for (int i = 0; i < STAGES.length; i++) {
            counts.set(i, 0);
            overflows.set(i, 0);
        }
        samples = s;
        enabled = true;
    }

    public static void disable() { enabled = false; }

    public static boolean isEnabled() { return enabled; }

    /** @return a start token for {@link #stop}; 0 when disabled. */
    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void stop(Stage stage, long startToken) {
        if (startToken == 0L) return;
        record(stage, System.nanoTime() - startToken);
    }

    /** Records a duration measured elsewhere (e.g. queue age). Ignored when disabled. */
    public static void record(Stage stage, long nanos) {
        if (!enabled) return;
        int i = stage.ordinal();
        long[] a = samples[i];
        int idx = counts.getAndIncrement(i);
        if (idx < a.length) {
            a[idx] = nanos;
        } else {
            counts.decrementAndGet(i);
            overflows.incrementAndGet(i);
        }
    }

    public static int count(Stage stage) { return Math.min(counts.get(stage.ordinal()), samples[stage.ordinal()].length); }

    public static int overflow(Stage stage) { return overflows.get(stage.ordinal()); }

    /** Copy of the recorded durations for a stage, in milliseconds, in recording order. */
    public static double[] samplesMs(Stage stage) {
        int n = count(stage);
        long[] a = samples[stage.ordinal()];
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a[i] / 1_000_000.0;
        return out;
    }
}
