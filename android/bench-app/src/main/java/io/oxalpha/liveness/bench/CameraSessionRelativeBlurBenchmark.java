package io.oxalpha.liveness.bench;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import liveness.android.CameraXDeviceAdapter;
import liveness.android.CompositeFaceDetector;
import liveness.android.FaceDetector;
import liveness.android.YuNetFaceDetector;
import liveness.android.YuvFrame;
import liveness.android.glue.MlKitFaceDetector;
import liveness.challenge.ChallengeGenerator;
import liveness.core.FaceFrame;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.DeviceException;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.LivenessSession;
import liveness.flow.LivenessState;
import liveness.flow.RetryRateLimiter;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;

/**
 * BENCH ONLY. Real LivenessSession (real PAD model, real composite detector, live front camera) with
 * relativeBlurEnabled(true). No verdict can fire (minQualifyingFrames huge). blurMinVariance is lowered to 10.0 so
 * live frames can reach the relative hook: QualityGate is NOT representative. Records the session state per frame and
 * counts the relative-gate reasons in the audit trail. Candidate constants, not fitted. Flap state and flip time are
 * user-stated, not in the JSON.
 */
final class CameraSessionRelativeBlurBenchmark {
    static final int TOTAL = 330;
    static final String PAD = "MiniFASNetV2-SE-int8.onnx";
    static final double BLUR_OVERRIDE = 10.0;

    private CameraSessionRelativeBlurBenchmark() {}

    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry reg = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return reg; }
    }

    private static byte[] copy(ByteBuffer b) {
        ByteBuffer d = b.duplicate();
        d.rewind();
        byte[] out = new byte[d.remaining()];
        d.get(out);
        return out;
    }

    private static int count(String s, String needle) {
        int c = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { c++; i += needle.length(); }
        return c;
    }

    static String run(Context ctx, String label) throws Exception {
        final Context app = ctx.getApplicationContext();
        final File yunetModel = BenchRunner.copyAsset(app, "face_detection_yunet.onnx");
        final File padModel = BenchRunner.copyAsset(app, PAD);
        final LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(Integer.MAX_VALUE / 2)
                .blurMinVariance(BLUR_OVERRIDE)
                .relativeBlurEnabled(true)
                .build();

        Map<String, Object> out = BenchRunner.envelope(app, "camera_session_relative_blur:" + label);
        out.put("before", DeviceInfo.dynamicInfo(app));

        final StringBuilder states = new StringBuilder();
        final StringBuilder norms = new StringBuilder();
        final Map<String, Integer> stateCounts = new LinkedHashMap<>();
        String error = null;
        String endedState = null;
        int captured = 0;
        int dropped = 0;
        String trail = "";

        final Handler main = new Handler(Looper.getMainLooper());
        final Owner owner = new Owner();
        final ProcessCameraProvider[] provider = {null};
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final String[] camError = {null};
        final long t0 = System.currentTimeMillis();

        try (YuNetFaceDetector yunet = new YuNetFaceDetector(yunetModel.toPath());
             MlKitFaceDetector mlkit = new MlKitFaceDetector();
             MiniFasNetScorer scorer = new MiniFasNetScorer(padModel.toPath())) {
            if (scorer.isStubMode()) throw new IllegalStateException("refusing to run with the stub scorer");
            final FaceDetector detector = new CompositeFaceDetector(yunet, mlkit);
            final CameraXDeviceAdapter adapter = new CameraXDeviceAdapter("front-camera", detector);
            LivenessService service = new LivenessService(cfg, scorer, new SameFaceChecker(0.48),
                    new ChallengeGenerator(), new RetryRateLimiter(5, 60_000), new EcdsaSignerStub());
            LivenessSession session = service.startBenchmarkSession("bench-subject", adapter, "bench-machine",
                    LivenessService.WorkflowType.RESIDENT_REGISTRATION);
            session.start();
            LivenessState st = session.openDevice();
            if (st.isTerminal()) throw new IllegalStateException("session did not open: " + st);

            final ListenableFuture<ProcessCameraProvider> fut = ProcessCameraProvider.getInstance(app);
            fut.addListener(() -> {
                try {
                    provider[0] = fut.get();
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build();
                    analysis.setAnalyzer(exec, image -> {
                        try {
                            ImageProxy.PlaneProxy[] p = image.getPlanes();
                            if (p.length < 3) return;
                            YuvFrame f = new YuvFrame(image.getWidth(), image.getHeight(),
                                    copy(p[0].getBuffer()), copy(p[1].getBuffer()), copy(p[2].getBuffer()),
                                    p[0].getRowStride(), p[1].getRowStride(), p[1].getPixelStride(),
                                    image.getImageInfo().getRotationDegrees(), System.currentTimeMillis());
                            adapter.onYuvFrame(f);
                        } catch (RuntimeException e) {
                            camError[0] = e.toString();
                        } finally {
                            image.close();
                        }
                    });
                    owner.reg.setCurrentState(Lifecycle.State.RESUMED);
                    provider[0].unbindAll();
                    provider[0].bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
                } catch (Exception e) {
                    camError[0] = e.toString();
                }
            }, main::post);

            for (int i = 0; i < TOTAL; i++) {
                FaceFrame ff;
                LivenessState s;
                try {
                    ff = adapter.captureFrame();
                    captured++;
                    s = session.processFrame(ff);
                } catch (DeviceException e) {
                    error = e.toString();
                    break;
                }
                String name = s.name();
                stateCounts.merge(name, 1, Integer::sum);
                char c = "POSITIONING".equals(name) ? 'P' : "PASSIVE".equals(name) ? 'A' : 'X';
                states.append(c);
                if (i > 0) norms.append(',');
                norms.append(String.format(Locale.ROOT, "%.4f", ff.fullFrameNorm()));
                if (s.isTerminal()) {
                    endedState = name;
                    break;
                }
            }
            dropped = adapter.droppedFrames();
            adapter.close();
            trail = session.exportAuditTrail();

            final CountDownLatch cleaned = new CountDownLatch(1);
            main.post(() -> {
                try {
                    if (provider[0] != null) provider[0].unbindAll();
                    owner.reg.setCurrentState(Lifecycle.State.DESTROYED);
                } catch (Exception ignored) {
                    // best effort
                } finally {
                    cleaned.countDown();
                }
            });
            cleaned.await(5, TimeUnit.SECONDS);
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }

        out.put("after", DeviceInfo.dynamicInfo(app));
        out.put("condition_label", label);
        out.put("what", "REAL LivenessSession + real PAD + real composite detector + live front camera, relativeBlurEnabled(true); "
                + "no verdict can fire (minQualifyingFrames huge); QualityGate blur threshold lowered, NOT representative; candidate constants");
        out.put("blur_gate_override", BLUR_OVERRIDE);
        out.put("relative_blur_window", cfg.relativeBlurWindow());
        out.put("relative_blur_drop_factor", cfg.relativeBlurDropFactor());
        out.put("relative_blur_y_shift_factor", cfg.relativeBlurYShiftFactor());
        out.put("requested_frames", TOTAL);
        out.put("captured_frames", captured);
        out.put("adapter_dropped_frames", dropped);
        out.put("wall_clock_ms", System.currentTimeMillis() - t0);
        out.put("ended_terminal_state", endedState);
        out.put("error", error == null && camError[0] == null ? null : (String.valueOf(error) + " | " + camError[0]));
        out.put("state_counts", new LinkedHashMap<String, Object>(stateCounts));
        out.put("state_string_P_positioning_A_passive_X_other", states.toString());
        out.put("full_norm_per_frame_csv", norms.toString());
        out.put("trail_count_blur_relative", count(trail, "guide.quality.blur_relative"));
        out.put("trail_count_light_unsteady", count(trail, "guide.quality.light_unsteady"));
        out.put("trail_count_relative_blur_warmup", count(trail, "relative_blur_warmup"));
        out.put("trail_count_quality_dropped", count(trail, "QUALITY_DROPPED"));
        out.put("trail_count_quality_ok", count(trail, "QUALITY_OK"));
        Log.i(BenchRunner.TAG, "session relative gate saving " + label);
        return BenchRunner.save(app, "camera_session_relative_blur_" + label, out);
    }
}
