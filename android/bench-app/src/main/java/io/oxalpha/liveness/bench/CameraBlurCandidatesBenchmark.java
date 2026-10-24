package io.oxalpha.liveness.bench;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import liveness.android.BlurCandidates;
import liveness.android.CompositeFaceDetector;
import liveness.android.FaceDetector;
import liveness.android.LumaProbe;
import liveness.android.YuNetFaceDetector;
import liveness.android.YuvConverter;
import liveness.android.YuvFrame;
import liveness.android.glue.MlKitFaceDetector;

/**
 * Live front-camera blur CANDIDATE survey. Same camera request as CameraBlurBenchmark / production.
 * Per frame: old whole-frame Laplacian variance (LumaProbe), Y brightness, whole-frame RGB mean luma,
 * full-frame and face-crop candidate metrics (BlurCandidates), faceCount, bbox, detect and convert time.
 * BENCH ONLY. Not accuracy, not a gate. Detector is the real composite (YuNet + ML Kit).
 */
final class CameraBlurCandidatesBenchmark {
    static final int WARMUP = 30;
    static final int MEASURE = 300;

    private CameraBlurCandidatesBenchmark() {}

    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry reg = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return reg; }
    }

    private static double[] nan(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    static String run(Context ctx, String label) throws Exception {
        final Context app = ctx.getApplicationContext();
        final File yunetModel = BenchRunner.copyAsset(app, "face_detection_yunet.onnx");
        final int total = WARMUP + MEASURE;

        final double[] whole = nan(total), ybr = nan(total), rgbMean = nan(total);
        final double[] fullLv = nan(total), fullNorm = nan(total), fullRatio = nan(total);
        final double[] cropLv = nan(total), cropNorm = nan(total), cropRatio = nan(total), cropMean = nan(total);
        final double[] detMs = nan(total), convMs = nan(total);
        final int[] fc = new int[total];
        final int[] bx = new int[total], by = new int[total], bw = new int[total], bh = new int[total];
        Arrays.fill(fc, -1);

        final Map<String, Integer> shapes = new LinkedHashMap<>();
        final int[] count = {0};
        final int[] chromaMismatch = {0};
        final int[] errFrames = {0};
        final int[] cropNull = {0};
        final String[] error = {null};
        final CountDownLatch done = new CountDownLatch(1);
        final Handler main = new Handler(Looper.getMainLooper());
        final Owner owner = new Owner();
        final ProcessCameraProvider[] provider = {null};
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final String thermalBefore = thermal(app);
        final long t0 = System.currentTimeMillis();
        boolean complete = false;

        try (YuNetFaceDetector yunet = new YuNetFaceDetector(yunetModel.toPath());
             MlKitFaceDetector mlkit = new MlKitFaceDetector()) {
            final FaceDetector detector = new CompositeFaceDetector(yunet, mlkit);

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
                            int n = count[0];
                            if (n >= total) return;
                            ImageProxy.PlaneProxy[] p = image.getPlanes();
                            if (p.length < 3) {
                                error[0] = "planes<3";
                                count[0] = n + 1;
                                if (n + 1 >= total) done.countDown();
                                return;
                            }
                            YuvFrame f = new YuvFrame(image.getWidth(), image.getHeight(),
                                    copy(p[0].getBuffer()), copy(p[1].getBuffer()), copy(p[2].getBuffer()),
                                    p[0].getRowStride(), p[1].getRowStride(), p[1].getPixelStride(),
                                    image.getImageInfo().getRotationDegrees(), System.currentTimeMillis());
                            if (p[2].getRowStride() != p[1].getRowStride()
                                    || p[2].getPixelStride() != p[1].getPixelStride()) chromaMismatch[0]++;
                            String key = f.width + "x" + f.height + " rot" + f.rotationDegrees
                                    + " yRowStride" + f.yRowStride + " uvRowStride" + f.uvRowStride
                                    + " uvPixelStride" + f.uvPixelStride;
                            shapes.merge(key, 1, Integer::sum);

                            double[] ls = LumaProbe.stats(f);
                            whole[n] = ls[0];
                            ybr[n] = ls[1];

                            long t = System.nanoTime();
                            byte[] rgb = YuvConverter.toRgb(f);
                            convMs[n] = (System.nanoTime() - t) / 1e6;
                            int ow = YuvConverter.outputWidth(f);
                            int oh = YuvConverter.outputHeight(f);

                            double[] full = BlurCandidates.compute(rgb, ow, oh, 0, 0, ow, oh);
                            if (full != null) {
                                fullLv[n] = full[BlurCandidates.LAP_VAR];
                                fullNorm[n] = full[BlurCandidates.LAP_VAR_NORM];
                                fullRatio[n] = full[BlurCandidates.SHARP_RATIO];
                                rgbMean[n] = full[BlurCandidates.MEAN_LUMA];
                            }

                            t = System.nanoTime();
                            FaceDetector.Observation o = detector.detect(rgb, ow, oh);
                            detMs[n] = (System.nanoTime() - t) / 1e6;
                            if (o == null) o = FaceDetector.Observation.NONE;
                            fc[n] = o.faceCount;
                            bx[n] = o.bboxX;
                            by[n] = o.bboxY;
                            bw[n] = o.bboxW;
                            bh[n] = o.bboxH;
                            if (o.faceCount == 1) {
                                double[] c = BlurCandidates.compute(rgb, ow, oh, o.bboxX, o.bboxY, o.bboxW, o.bboxH);
                                if (c != null) {
                                    cropLv[n] = c[BlurCandidates.LAP_VAR];
                                    cropNorm[n] = c[BlurCandidates.LAP_VAR_NORM];
                                    cropRatio[n] = c[BlurCandidates.SHARP_RATIO];
                                    cropMean[n] = c[BlurCandidates.MEAN_LUMA];
                                } else {
                                    cropNull[0]++;
                                }
                            }
                            count[0] = n + 1;
                            if (n + 1 >= total) done.countDown();
                        } catch (RuntimeException e) {
                            error[0] = e.toString();
                            errFrames[0]++;
                            int k = count[0] + 1;
                            count[0] = k;
                            if (k >= total) done.countDown();
                        } finally {
                            image.close();
                        }
                    });
                    owner.reg.setCurrentState(Lifecycle.State.RESUMED);
                    provider[0].unbindAll();
                    provider[0].bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
                } catch (Exception e) {
                    error[0] = e.toString();
                    done.countDown();
                }
            }, main::post);

            complete = done.await(120, TimeUnit.SECONDS);

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

        final int got = Math.min(count[0], total);
        if (got - WARMUP <= 0) {
            return "FAILED: no measured frames. got=" + got + " error=" + error[0];
        }
        int one = 0, none = 0, multi = 0;
        for (int i = WARMUP; i < got; i++) {
            if (fc[i] == 1) one++;
            else if (fc[i] == 0) none++;
            else if (fc[i] > 1) multi++;
        }

        StringBuilder j = new StringBuilder(65536);
        j.append("{\"bench\":\"camera_blur_candidates\"");
        j.append(",\"condition_label\":\"").append(label).append("\"");
        j.append(",\"what\":\"live front camera, ImageAnalysis only, real composite detector, BlurCandidates on full frame and face crop, whole-frame lap via LumaProbe\"");
        j.append(",\"request\":\"target 640x480, KEEP_ONLY_LATEST, YUV_420_888, DEFAULT_FRONT_CAMERA\"");
        j.append(",\"warmup_frames\":").append(WARMUP);
        j.append(",\"measured_frames\":").append(got - WARMUP);
        j.append(",\"requested_measured_frames\":").append(MEASURE);
        j.append(",\"complete\":").append(complete);
        j.append(",\"frames_one_face\":").append(one);
        j.append(",\"frames_no_face\":").append(none);
        j.append(",\"frames_multi_face\":").append(multi);
        j.append(",\"crop_null_frames\":").append(cropNull[0]);
        j.append(",\"chroma_stride_mismatch_frames\":").append(chromaMismatch[0]);
        j.append(",\"error_frames\":").append(errFrames[0]);
        j.append(",\"delivered_shapes\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : shapes.entrySet()) {
            if (!first) j.append(',');
            first = false;
            j.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        j.append("}");
        j.append(",\"stats\":{");
        j.append("\"whole_frame_lap_old\":").append(stats(whole, WARMUP, got));
        j.append(",\"full_frame_lap112\":").append(stats(fullLv, WARMUP, got));
        j.append(",\"full_frame_norm\":").append(stats(fullNorm, WARMUP, got));
        j.append(",\"full_frame_sharp_ratio\":").append(stats(fullRatio, WARMUP, got));
        j.append(",\"crop_lap112\":").append(stats(cropLv, WARMUP, got));
        j.append(",\"crop_norm\":").append(stats(cropNorm, WARMUP, got));
        j.append(",\"crop_sharp_ratio\":").append(stats(cropRatio, WARMUP, got));
        j.append(",\"crop_mean_luma\":").append(stats(cropMean, WARMUP, got));
        j.append(",\"y_brightness\":").append(stats(ybr, WARMUP, got));
        j.append(",\"rgb_whole_mean_luma\":").append(stats(rgbMean, WARMUP, got));
        j.append(",\"detect_ms\":").append(stats(detMs, WARMUP, got));
        j.append(",\"yuv_to_rgb_ms\":").append(stats(convMs, WARMUP, got));
        j.append("}");
        j.append(",\"thermal_before\":\"").append(thermalBefore).append("\"");
        j.append(",\"thermal_after\":\"").append(thermal(app)).append("\"");
        j.append(",\"wall_clock_ms\":").append(System.currentTimeMillis() - t0);
        j.append(",\"error\":");
        if (error[0] == null) j.append("null");
        else j.append('"').append(error[0].replace("\"", "'")).append('"');
        j.append(",\"per_frame\":{");
        j.append("\"whole_lap\":").append(arr(whole, WARMUP, got));
        j.append(",\"y_brightness\":").append(arr(ybr, WARMUP, got));
        j.append(",\"rgb_mean_luma\":").append(arr(rgbMean, WARMUP, got));
        j.append(",\"full_lap112\":").append(arr(fullLv, WARMUP, got));
        j.append(",\"full_norm\":").append(arr(fullNorm, WARMUP, got));
        j.append(",\"full_ratio\":").append(arr(fullRatio, WARMUP, got));
        j.append(",\"crop_lap112\":").append(arr(cropLv, WARMUP, got));
        j.append(",\"crop_norm\":").append(arr(cropNorm, WARMUP, got));
        j.append(",\"crop_ratio\":").append(arr(cropRatio, WARMUP, got));
        j.append(",\"crop_mean_luma\":").append(arr(cropMean, WARMUP, got));
        j.append(",\"detect_ms\":").append(arr(detMs, WARMUP, got));
        j.append(",\"face_count\":").append(Arrays.toString(Arrays.copyOfRange(fc, WARMUP, got)).replace(" ", ""));
        j.append(",\"bbox_xywh\":[");
        for (int i = WARMUP; i < got; i++) {
            if (i > WARMUP) j.append(',');
            j.append('[').append(bx[i]).append(',').append(by[i]).append(',').append(bw[i]).append(',').append(bh[i]).append(']');
        }
        j.append("]}}");

        File dir = new File(app.getExternalFilesDir(null), "bench");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        File out = new File(dir, "camera_blur_cand_" + label + "_" + t0 + ".json");
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(j.toString().getBytes(StandardCharsets.UTF_8));
        }
        Log.i(BenchRunner.TAG, "camera blur candidates saved " + out.getAbsolutePath());
        return "saved: " + out.getAbsolutePath() + "\n" + j.substring(0, Math.min(j.length(), 3000));
    }

    private static String f(double x) {
        return Double.isNaN(x) || Double.isInfinite(x) ? "null" : String.format(Locale.ROOT, "%.4f", x);
    }

    private static String arr(double[] a, int from, int to) {
        StringBuilder s = new StringBuilder("[");
        for (int i = from; i < to; i++) {
            if (i > from) s.append(',');
            s.append(Double.isNaN(a[i]) ? "null" : String.format(Locale.ROOT, "%.3f", a[i]));
        }
        return s.append(']').toString();
    }

    private static String stats(double[] a, int from, int to) {
        int n = 0;
        double[] v = new double[Math.max(0, to - from)];
        double sum = 0;
        for (int i = from; i < to; i++) {
            if (!Double.isNaN(a[i])) {
                v[n++] = a[i];
                sum += a[i];
            }
        }
        if (n == 0) return "{\"n\":0}";
        double[] s = Arrays.copyOf(v, n);
        Arrays.sort(s);
        return "{\"n\":" + n + ",\"mean\":" + f(sum / n) + ",\"min\":" + f(s[0])
                + ",\"p5\":" + f(q(s, 0.05)) + ",\"p50\":" + f(q(s, 0.50))
                + ",\"p95\":" + f(q(s, 0.95)) + ",\"max\":" + f(s[n - 1]) + "}";
    }

    private static double q(double[] sorted, double p) {
        int i = (int) Math.min(sorted.length - 1, Math.floor(p * (sorted.length - 1) + 0.5));
        return sorted[i];
    }

    private static String thermal(Context c) {
        if (Build.VERSION.SDK_INT < 29) return "n/a";
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null ? "n/a" : String.valueOf(pm.getCurrentThermalStatus());
    }

    private static byte[] copy(ByteBuffer b) {
        ByteBuffer d = b.duplicate();
        d.rewind();
        byte[] out = new byte[d.remaining()];
        d.get(out);
        return out;
    }
}
