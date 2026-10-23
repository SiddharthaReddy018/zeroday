package liveness.android;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Detector-only latency measurement. Uses deterministic synthetic RGB input; this is not an accuracy test. */
public final class YuNetDetectorBenchmark {

    private YuNetDetectorBenchmark() {}

    public static Map<String, Object> run(Path modelPath, int width, int height,
                                          int warmupFrames, int frames) throws Exception {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("frame dimensions");
        if (warmupFrames < 0) throw new IllegalArgumentException("warmupFrames");
        if (frames < 1) throw new IllegalArgumentException("frames must be >= 1");
        long rgbLength = (long) width * height * 3L;
        if (rgbLength > Integer.MAX_VALUE) throw new IllegalArgumentException("frame is too large");

        byte[] rgb = syntheticRgb((int) rgbLength);
        try (YuNetFaceDetector detector = new YuNetFaceDetector(modelPath)) {
            for (int i = 0; i < warmupFrames; i++) detector.detect(rgb, width, height);

            double[] samples = new double[frames];
            int framesWithDetections = 0;
            long totalDetections = 0;
            for (int i = 0; i < frames; i++) {
                long start = System.nanoTime();
                FaceDetector.Observation observation = detector.detect(rgb, width, height);
                samples[i] = (System.nanoTime() - start) / 1e6;
                if (observation.faceCount > 0) framesWithDetections++;
                totalDetections += observation.faceCount;
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bench", "yunet_detector_only");
            out.put("detector", "YuNet ONNX Runtime CPU; BASIC_OPT; fixed 640x640 input");
            out.put("model", "face_detection_yunet.onnx");
            out.put("model_sha256", detector.modelSha256());
            out.put("frame", width + "x" + height + " synthetic deterministic RGB noise");
            out.put("measured", "input validation, RGB-to-BGR resize/pad, ORT inference, decode, NMS");
            out.put("not_measured", "camera/ImageProxy copy, UI, SFace, PAD, signing, challenge accuracy");
            out.put("liveness_capability_note",
                    "YuNet has 5 SFace alignment points only; it has no eye/mouth contours or 3D pose");
            out.put("warmup_frames", warmupFrames);
            out.put("frames", frames);
            out.put("frames_with_detections_on_synthetic_noise", framesWithDetections);
            out.put("detections_on_synthetic_noise_not_accuracy", totalDetections);
            out.put("latency_ms", PadBenchmark.statsMap(LatencyBenchmark.summarize(samples)));
            out.put("cores", Runtime.getRuntime().availableProcessors());
            return out;
        }
    }

    private static byte[] syntheticRgb(int length) {
        byte[] rgb = new byte[length];
        int state = 0x6d2b79f5;
        for (int i = 0; i < rgb.length; i++) {
            state = state * 1664525 + 1013904223;
            rgb[i] = (byte) (state >>> 24);
        }
        return rgb;
    }
}
