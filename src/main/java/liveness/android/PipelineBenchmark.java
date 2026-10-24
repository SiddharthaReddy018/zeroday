package liveness.android;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.Signer;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.LivenessSession;
import liveness.flow.LivenessState;
import liveness.flow.RetryRateLimiter;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-frame latency of the real engine path, stage by stage (P5, AUDIT_TODO X6): YUV-to-RGB, luma stats, pHash,
 * FaceFrame (digest), integrity monitor, quality gate, preprocessing, PAD inference, and the rest of processFrame.
 * <p>
 * Pure Java; the same code runs on the desktop JVM and the phone. Two modes:
 * <ul>
 *   <li>LOCKSTEP: one frame in, one frame out, back to back. Gives the compute cost per frame with no queueing.</li>
 *   <li>PACED: a producer thread delivers frames at a fixed fps into the real bounded drop-oldest queue while the
 *       engine thread consumes. Shows what a slow engine does to the camera path: queue age, dropped frames, and
 *       how many consumed-timestamp gaps exceed 2x the expected interval (the FrameSequenceMonitor TIMESTAMP_GAP
 *       rule, which fails the session).</li>
 * </ul>
 * NOT measured, and the report says so: the face detector (a fixed box is used; a real YuNet/MediaPipe call adds
 * its own cost on top of every number here), SFace embedding, the camera/ImageProxy copy, UI rendering, and any
 * signing. Frames are synthetic noise, so this is latency only, never accuracy.
 */
public final class PipelineBenchmark {

    public enum Mode { LOCKSTEP, PACED }

    public static final long INPUT_SEED = 11L;
    public static final int FRAME_POOL = 48;
    public static final int BLOCKS = 5;
    public static final double TARGET_P95_MS = 200.0;

    private PipelineBenchmark() {}

    public static Map<String, Object> run(Path padModel, Signer signer, FaceEmbedder embedder, Mode mode,
                                          int width, int height, int rotation, int fps,
                                          int warmupFrames, int frames) throws Exception {
        return runWith(padModel, signer, embedder, mode, null, null, null, null,
                width, height, rotation, fps, warmupFrames, frames);
    }

    /**
     * Same path as {@link #run}, optionally with a REAL detector and a caller-built frame pool. Both must be given
     * together. The pool must hold one geometry (a size change raises RESOLUTION_CHANGE) and frames whose content
     * digests differ from their neighbours (byte-identical consecutive frames raise FROZEN).
     * width/height/rotation are used for the synthetic pool only.
     */
    public static Map<String, Object> runWith(Path padModel, Signer signer, FaceEmbedder embedder, Mode mode,
                                              FaceDetector detector, String detectorLabel,
                                              YuvFrame[] framePool, String frameLabel,
                                              int width, int height, int rotation, int fps,
                                              int warmupFrames, int frames) throws Exception {
        if (frames < BLOCKS) throw new IllegalArgumentException("frames must be >= " + BLOCKS);
        if ((detector == null) != (framePool == null)) {
            throw new IllegalArgumentException("detector and framePool must be given together");
        }
        if (detector != null && (detectorLabel == null || frameLabel == null || framePool.length == 0)) {
            throw new IllegalArgumentException("detectorLabel, frameLabel and a non-empty framePool are required");
        }
        if (fps < 1) throw new IllegalArgumentException("fps");
        final long expectedIntervalMs = CameraXDeviceAdapter.DEFAULT_EXPECTED_INTERVAL_MS;
        try (MiniFasNetScorer scorer = new MiniFasNetScorer(padModel)) {
            if (scorer.isStubMode()) throw new IllegalStateException("refusing to benchmark the stub scorer");
            // No decision may fire: a verdict would end the session and stop PAD scoring mid-run.
            int total = warmupFrames + frames;
            LivenessConfig config = LivenessConfig.builder().minQualifyingFrames(Integer.MAX_VALUE / 2).build();
            LivenessService service = new LivenessService(config, scorer,
                    new SameFaceChecker(0.48), new ChallengeGenerator(), new RetryRateLimiter(5, 60_000), signer);
            service.setFaceEmbedder(embedder);

            FaceDetector fixedBox = (rgb, w, h) ->
                    new FaceDetector.Observation(1, w / 4, h / 4, w / 2, h / 2, 0.0, 0.0, 0.0, null);
            CameraXDeviceAdapter camera = new CameraXDeviceAdapter("bench-cam", detector != null ? detector : fixedBox,
                    CameraXDeviceAdapter.DEFAULT_QUEUE_CAPACITY, CameraXDeviceAdapter.DEFAULT_FRAME_TIMEOUT_MS,
                    expectedIntervalMs);
            LivenessSession session = service.startBenchmarkSession("bench-subject", camera, "bench-machine", LivenessService.WorkflowType.RESIDENT_REGISTRATION);
            session.start();
            LivenessState st = session.openDevice();
            if (st.isTerminal()) throw new IllegalStateException("session did not open: " + st);

            YuvFrame[] pool = framePool != null ? framePool : syntheticPool(width, height, rotation);

            List<Double> serialMs = new ArrayList<>();
            List<Double> e2eMs = new ArrayList<>();
            List<Long> consumedTs = new ArrayList<>();
            ConcurrentHashMap<Long, Long> arrival = new ConcurrentHashMap<>();
            final AtomicBoolean stop = new AtomicBoolean(false);
            Thread producer = null;
            final long baseMs = System.currentTimeMillis();
            final long periodNs = 1_000_000_000L / fps;

            if (mode == Mode.PACED) {
                producer = new Thread(() -> {
                    long next = System.nanoTime();
                    for (int i = 0; i < total + 20 && !stop.get(); i++) {
                        long ts = System.currentTimeMillis() - baseMs + 1_000_000L;
                        YuvFrame src = pool[i % pool.length];
                        YuvFrame f = new YuvFrame(src.width, src.height, src.y, src.u, src.v, src.yRowStride,
                                src.uvRowStride, src.uvPixelStride, src.rotationDegrees, ts);
                        arrival.put(ts, f.arrivalNanos);
                        camera.onYuvFrame(f);
                        next += periodNs;
                        long sleep = next - System.nanoTime();
                        if (sleep > 0) {
                            try { Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L)); }
                            catch (InterruptedException e) { return; }
                        } else {
                            next = System.nanoTime();   // producer itself late; do not burst to catch up
                        }
                    }
                }, "bench-producer");
                producer.setDaemon(true);
                producer.start();
            }

            long lockstepTs = 1_000_000L;
            int seq = 0;
            try {
                for (int i = 0; i < total; i++) {
                    if (i == warmupFrames) StageTimer.enable(frames + 16);
                    if (mode == Mode.LOCKSTEP) {
                        YuvFrame src = pool[seq++ % pool.length];
                        lockstepTs += 33;
                        camera.onYuvFrame(new YuvFrame(src.width, src.height, src.y, src.u, src.v, src.yRowStride,
                                src.uvRowStride, src.uvPixelStride, src.rotationDegrees, lockstepTs));
                    }
                    long t0 = System.nanoTime();
                    liveness.core.FaceFrame ff = camera.captureFrame();
                    LivenessState s = session.processFrame(ff);
                    long t1 = System.nanoTime();
                    if (s.isTerminal()) throw new IllegalStateException("session ended early at frame " + i + ": " + s
                            + " (numbers would not describe the full path)");
                    if (i >= warmupFrames) {
                        serialMs.add((t1 - t0) / 1e6);
                        consumedTs.add(ff.timestampMs());
                        if (mode == Mode.PACED) {
                            Long a = arrival.get(ff.timestampMs());
                            if (a != null) e2eMs.add((t1 - a) / 1e6);
                        }
                    }
                }
            } finally {
                stop.set(true);
                StageTimer.disable();
                camera.close();
                if (producer != null) producer.interrupt();
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bench", "pipeline_" + mode.name().toLowerCase(Locale.ROOT));
            out.put("pad_model", padModel.getFileName().toString());
            out.put("pad_model_version", scorer.modelVersion());
            out.put("runtime", MiniFasNetScorer.RUNTIME_DESCRIPTION);
            out.put("frame", frameLabel != null ? frameLabel
                    : width + "x" + height + " YUV_420_888, rotation " + rotation + ", synthetic noise, pool " + FRAME_POOL);
            out.put("detector", detectorLabel != null ? detectorLabel
                    : "FIXED BOX (no real detector; detector cost NOT included)");
            out.put("not_measured", detector != null ? "SFace embedding, camera/ImageProxy copy, UI, signing"
                    : "face detector, SFace embedding, camera/ImageProxy copy, UI, signing");
            out.put("cores", Runtime.getRuntime().availableProcessors());
            out.put("warmup_frames", warmupFrames);
            out.put("frames", frames);
            if (mode == Mode.PACED) out.put("producer_fps", fps);

            // Sanity: every measured frame must have gone through the PAD model, else the numbers are for a shorter path.
            int padCount = StageTimer.count(StageTimer.Stage.PAD_INFER);
            int procCount = StageTimer.count(StageTimer.Stage.PROCESS_TOTAL);
            out.put("pad_infer_calls", padCount);
            if (padCount != frames || procCount != frames) {
                throw new IllegalStateException("sanity failed: PAD_INFER=" + padCount + " PROCESS_TOTAL=" + procCount
                        + " expected " + frames + "; frames were rejected before scoring (quality/integrity) - result invalid");
            }

            Map<String, Object> stages = new LinkedHashMap<>();
            for (StageTimer.Stage sg : StageTimer.Stage.values()) {
                if (StageTimer.count(sg) == 0) continue;
                Map<String, Object> m = PadBenchmark.statsMap(LatencyBenchmark.summarize(StageTimer.samplesMs(sg)));
                m.put("overflow", StageTimer.overflow(sg));
                stages.put(sg.name(), m);
            }
            out.put("stages", stages);

            // Remainder of processFrame (audit log, state machine, spatial aggregation), aligned per frame.
            double[] proc = StageTimer.samplesMs(StageTimer.Stage.PROCESS_TOTAL);
            double[] a = StageTimer.samplesMs(StageTimer.Stage.INTEGRITY);
            double[] b = StageTimer.samplesMs(StageTimer.Stage.QUALITY);
            double[] c = StageTimer.samplesMs(StageTimer.Stage.PREPROCESS);
            double[] d = StageTimer.samplesMs(StageTimer.Stage.PAD_INFER);
            double[] rest = new double[proc.length];
            for (int i = 0; i < proc.length; i++) rest[i] = proc[i] - a[i] - b[i] - c[i] - d[i];
            out.put("process_other", PadBenchmark.statsMap(LatencyBenchmark.summarize(rest)));

            double[] serial = toArray(serialMs);
            LatencyBenchmark.Stats ss = LatencyBenchmark.summarize(serial);
            out.put("per_frame_serial", PadBenchmark.statsMap(ss));
            List<Object> bl = new ArrayList<>();
            for (double x : LatencyBenchmark.blockP50(serial, BLOCKS)) bl.add(x);
            out.put("per_frame_block_p50_ms", bl);
            out.put("per_frame_p95_le_200ms", ss.meetsP95(TARGET_P95_MS));
            out.put("max_sustainable_fps_by_mean", ss.meanMs > 0 ? 1000.0 / ss.meanMs : null);

            // TIMESTAMP_GAP coupling: gaps between consecutive frames the engine actually received.
            long limit = 2 * expectedIntervalMs;
            int violations = 0;
            long maxGap = 0;
            for (int i = 1; i < consumedTs.size(); i++) {
                long g = consumedTs.get(i) - consumedTs.get(i - 1);
                maxGap = Math.max(maxGap, g);
                if (g > limit) violations++;
            }
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("rule_limit_ms", limit);
            gap.put("max_gap_ms", maxGap);
            gap.put("violations", violations);
            gap.put("note", mode == Mode.LOCKSTEP ? "synthetic 33 ms timestamps; not informative in lockstep"
                    : "real-clock timestamps; a violation would fail the session in production");
            out.put("timestamp_gap", gap);
            out.put("dropped_by_queue", camera.droppedFrames());
            if (mode == Mode.PACED && !e2eMs.isEmpty()) {
                out.put("e2e_arrival_to_processed", PadBenchmark.statsMap(LatencyBenchmark.summarize(toArray(e2eMs))));
            }
            return out;
        }
    }

    private static YuvFrame[] syntheticPool(int width, int height, int rotation) {
        YuvFrame[] pool = new YuvFrame[FRAME_POOL];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = BenchInput.syntheticYuv(width, height, rotation, 0L, INPUT_SEED * 1000 + i);
        }
        return pool;
    }

    private static double[] toArray(List<Double> l) {
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }
}
