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
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import liveness.android.BlurWindowGate;
import liveness.android.CameraXDeviceAdapter;
import liveness.android.CompositeFaceDetector;
import liveness.android.FaceDetector;
import liveness.android.YuNetFaceDetector;
import liveness.android.YuvFrame;
import liveness.android.glue.MlKitFaceDetector;
import liveness.core.FaceFrame;
import liveness.device.DeviceException;
import liveness.policy.LivenessConfig;

/**
 * Live check of the relative blur gate on the real camera path. BENCH ONLY. CameraX analyzer feeds
 * CameraXDeviceAdapter.onYuvFrame; this thread pulls captureFrame() (same adapter code that fills fullFrameNorm)
 * and feeds BlurWindowGate with the LivenessConfig defaults. Only faceCount==1 frames with a valid norm are fed,
 * as the session's quality gate would drop the others first. Does NOT run LivenessSession. Candidate constants,
 * not fitted. Run once flap OFF and once flap ON in one fixed light; flap state is user-stated, not in the JSON.
 */
final class CameraRelativeGateBenchmark {
    static final int TOTAL = 330;

    private CameraRelativeGateBenchmark() {}

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

    static String run(Context ctx, String label) throws Exception {
        final Context app = ctx.getApplicationContext();
        final File yunetModel = BenchRunner.copyAsset(app, "face_detection_yunet.onnx");
        final LivenessConfig cfg = LivenessConfig.builder().build();
        final BlurWindowGate gate = new BlurWindowGate(cfg.relativeBlurWindow(),
                cfg.relativeBlurDropFactor(), cfg.relativeBlurYShiftFactor());

        final StringBuilder states = new StringBuilder();
        final StringBuilder norms = new StringBuilder("[");
        final StringBuilder ys = new StringBuilder("[");
        final StringBuilder fcs = new StringBuilder("[");
        int nI = 0, nO = 0, nB = 0, nL = 0, notFed = 0, yInBounds = 0, firstB = -1, firstL = -1;
        int captured = 0;
        String error = null;
        int dropped = 0;

        final Handler main = new Handler(Looper.getMainLooper());
        final Owner owner = new Owner();
        final ProcessCameraProvider[] provider = {null};
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final String[] camError = {null};
        final long t0 = System.currentTimeMillis();

        try (YuNetFaceDetector yunet = new YuNetFaceDetector(yunetModel.toPath());
             MlKitFaceDetector mlkit = new MlKitFaceDetector()) {
            final FaceDetector detector = new CompositeFaceDetector(yunet, mlkit);
            final CameraXDeviceAdapter adapter = new CameraXDeviceAdapter("front-camera", detector);
            adapter.open();

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

            final double yMin = cfg.brightnessMin();
            final double yMax = cfg.brightnessMax();
            for (int i = 0; i < TOTAL; i++) {
                FaceFrame ff;
                try {
                    ff = adapter.captureFrame();
                } catch (DeviceException e) {
                    error = e.toString();
                    break;
                }
                captured++;
                double norm = ff.fullFrameNorm();
                double y = ff.brightnessMean();
                int fc = ff.faceCount();
                char c;
                if (fc == 1 && norm >= 0 && !Double.isNaN(norm) && !Double.isNaN(y)) {
                    BlurWindowGate.State s = gate.update(norm, y);
                    switch (s) {
                        case OK: c = 'O'; nO++; break;
                        case BLUR: c = 'B'; nB++; if (firstB < 0) firstB = i; break;
                        case LOST: c = 'L'; nL++; if (firstL < 0) firstL = i; break;
                        default: c = 'I'; nI++; break;
                    }
                } else {
                    c = '-';
                    notFed++;
                }
                if (y >= yMin && y <= yMax) yInBounds++;
                states.append(c);
                if (i > 0) { norms.append(','); ys.append(','); fcs.append(','); }
                norms.append(String.format(Locale.ROOT, "%.4f", norm));
                ys.append(String.format(Locale.ROOT, "%.2f", y));
                fcs.append(fc);
            }
            dropped = adapter.droppedFrames();
            adapter.close();

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
        norms.append(']');
        ys.append(']');
        fcs.append(']');

        StringBuilder j = new StringBuilder(32768);
        j.append("{\"bench\":\"camera_relative_gate_live\"");
        j.append(",\"condition_label\":\"").append(label).append("\"");
        j.append(",\"what\":\"live front camera -> CameraXDeviceAdapter -> BlurWindowGate; no LivenessSession; candidate constants\"");
        j.append(",\"gate\":{\"window\":").append(cfg.relativeBlurWindow())
                .append(",\"drop_factor\":").append(cfg.relativeBlurDropFactor())
                .append(",\"y_shift_factor\":").append(cfg.relativeBlurYShiftFactor()).append("}");
        j.append(",\"requested_frames\":").append(TOTAL);
        j.append(",\"captured_frames\":").append(captured);
        j.append(",\"adapter_dropped_frames\":").append(dropped);
        j.append(",\"counts\":{\"insufficient\":").append(nI).append(",\"ok\":").append(nO)
                .append(",\"blur\":").append(nB).append(",\"lost\":").append(nL)
                .append(",\"not_fed\":").append(notFed).append("}");
        j.append(",\"first_blur_index\":").append(firstB);
        j.append(",\"first_lost_index\":").append(firstL);
        j.append(",\"frames_y_in_session_bounds\":").append(yInBounds);
        j.append(",\"y_bounds\":[").append(cfg.brightnessMin()).append(',').append(cfg.brightnessMax()).append("]");
        j.append(",\"wall_clock_ms\":").append(System.currentTimeMillis() - t0);
        j.append(",\"error\":");
        if (error == null && camError[0] == null) j.append("null");
        else j.append('"').append((String.valueOf(error) + " | " + camError[0]).replace("\"", "'")).append('"');
        j.append(",\"state_string\":\"").append(states).append("\"");
        j.append(",\"per_frame\":{\"full_norm\":").append(norms)
                .append(",\"y\":").append(ys)
                .append(",\"face_count\":").append(fcs).append("}}");

        File dir = new File(app.getExternalFilesDir(null), "bench");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        File out = new File(dir, "camera_relative_gate_" + label + "_" + t0 + ".json");
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(j.toString().getBytes(StandardCharsets.UTF_8));
        }
        Log.i(BenchRunner.TAG, "relative gate live saved " + out.getAbsolutePath());
        return "saved: " + out.getAbsolutePath() + "\n" + j.substring(0, Math.min(j.length(), 1500));
    }
}
