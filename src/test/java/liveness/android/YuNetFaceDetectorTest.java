package liveness.android;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import liveness.engine.LandmarkLayout;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class YuNetFaceDetectorTest {

    private static final Path MODEL = Paths.get("datasets/models/face_detection_yunet.onnx");

    @Test
    void decodesOneFaceBoxAndFiveAlignmentPointsIntoTheSharedLayout() {
        float[][][][] outputs = blankOutputs();
        int index = 10 * 80 + 20;
        outputs[0][0][index][0] = 0.81f;
        outputs[3][0][index][0] = 1.0f;
        outputs[6][0][index][0] = 0.0f;
        outputs[6][0][index][1] = 0.0f;
        outputs[6][0][index][2] = (float) Math.log(10.0);
        outputs[6][0][index][3] = (float) Math.log(12.0);
        float[] p = {0, 0, 0.5f, 0.5f, 1, 1, 1.5f, 1.5f, 2, 2};
        System.arraycopy(p, 0, outputs[9][0][index], 0, p.length);

        FaceDetector.Observation o = YuNetFaceDetector.decode(outputs, 640, 640, 1.0, 1.0);
        assertEquals(1, o.faceCount);
        assertEquals(120, o.bboxX);
        assertEquals(32, o.bboxY);
        assertEquals(80, o.bboxW);
        assertEquals(96, o.bboxH);
        assertEquals(LandmarkLayout.FULL_LENGTH, o.landmarks.length);
        assertFalse(LandmarkLayout.hasBase(o.landmarks), "YuNet must not imply eye or mouth contours exist");
        assertTrue(Double.isNaN(o.landmarks[0]));
        assertTrue(LandmarkLayout.hasAlign5(o.landmarks));
        assertEquals(160.0, o.landmarks[LandmarkLayout.ALIGN5], 1e-6);
        assertEquals(80.0, o.landmarks[LandmarkLayout.ALIGN5 + 1], 1e-6);
        assertEquals(176.0, o.landmarks[LandmarkLayout.ALIGN5 + 8], 1e-6);
        assertEquals(96.0, o.landmarks[LandmarkLayout.ALIGN5 + 9], 1e-6);
        assertEquals(YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, o.yawDeg);
        assertEquals(YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, o.pitchDeg);
        assertEquals(YuNetFaceDetector.UNAVAILABLE_POSE_DEGREES, o.rollDeg);
    }

    @Test
    void mapsCoordinatesBackThroughResizeScale() {
        float[][][][] outputs = blankOutputs();
        int index = 10 * 80 + 20;
        setCandidate(outputs, index, 0.81f, 1.0f, 0.0f, 0.0f,
                (float) Math.log(10.0), (float) Math.log(12.0));
        FaceDetector.Observation o = YuNetFaceDetector.decode(outputs, 320, 320, 2.0, 2.0);
        assertEquals(60, o.bboxX);
        assertEquals(16, o.bboxY);
        assertEquals(40, o.bboxW);
        assertEquals(48, o.bboxH);
        assertEquals(80.0, o.landmarks[LandmarkLayout.ALIGN5], 1e-6);
        assertEquals(40.0, o.landmarks[LandmarkLayout.ALIGN5 + 1], 1e-6);
    }

    @Test
    void confidenceThresholdAndNmsSuppressLowConfidenceAndDuplicateBoxes() {
        float[][][][] outputs = blankOutputs();
        setCandidate(outputs, 10 * 80 + 20, 0.9f, 1.0f, 0, 0,
                (float) Math.log(10.0), (float) Math.log(12.0));
        setCandidate(outputs, 10 * 80 + 21, 0.8f, 1.0f, 0, 0,
                (float) Math.log(10.0), (float) Math.log(12.0));
        setCandidate(outputs, 40 * 80 + 50, 0.64f, 1.0f, 0, 0,
                (float) Math.log(10.0), (float) Math.log(12.0));
        setCandidate(outputs, 12 * 80 + 60, 0.2f, 1.0f, 0, 0,
                (float) Math.log(10.0), (float) Math.log(12.0));

        FaceDetector.Observation o = YuNetFaceDetector.decode(outputs, 640, 640, 1.0, 1.0);
        assertEquals(2, o.faceCount);
        assertEquals(120, o.bboxX, "highest confidence candidate must be first");
    }

    @Test
    void noQualifyingCandidatesReturnsNoFace() {
        FaceDetector.Observation o = YuNetFaceDetector.decode(blankOutputs(), 640, 480, 1.0, 1.0);
        assertEquals(0, o.faceCount);
        assertNull(o.landmarks);
    }

    @Test
    void malformedOutputCountOrShapeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decode(new float[1][][][], 640, 480, 1.0, 1.0));
        float[][][][] malformed = blankOutputs();
        malformed[0] = new float[1][10][1];
        assertThrows(IllegalArgumentException.class,
                () -> YuNetFaceDetector.decode(malformed, 640, 480, 1.0, 1.0));
    }

    @Test
    void wrongModelHashFailsBeforeOnnxSessionCreation() throws Exception {
        Path fake = Files.createTempFile("yunet-wrong-pin", ".onnx");
        try {
            Files.write(fake, new byte[]{1, 2, 3, 4});
            SecurityException e = assertThrows(SecurityException.class, () -> new YuNetFaceDetector(fake));
            assertTrue(e.getMessage().contains("SHA-256 mismatch"));
        } finally {
            Files.deleteIfExists(fake);
        }
    }

    @Test
    void pinnedLocalModelOpensAndExecutesWhenAvailable() throws Exception {
        assumeTrue(Files.isRegularFile(MODEL), "YuNet model not present: " + MODEL);
        byte[] rgb = new byte[640 * 480 * 3];
        for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) ((i * 37) & 0xff);
        try (YuNetFaceDetector detector = new YuNetFaceDetector(MODEL)) {
            assertEquals(YuNetFaceDetector.MODEL_SHA256, detector.modelSha256());
            FaceDetector.Observation o = detector.detect(rgb, 640, 480);
            assertNotNull(o);
            assertTrue(o.faceCount >= 0);
        }
    }

    @Test
    void timedDetectMatchesPlainDetectAndFillsEveryStage() throws Exception {
        assumeTrue(Files.isRegularFile(MODEL), "YuNet model not present: " + MODEL);
        byte[] rgb = new byte[640 * 480 * 3];
        for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) ((i * 37) & 0xff);
        try (YuNetFaceDetector detector = new YuNetFaceDetector(MODEL)) {
            assertThrows(IllegalArgumentException.class, () -> detector.detectTimed(rgb, 640, 480, null));
            assertThrows(IllegalArgumentException.class, () -> detector.detectTimed(rgb, 640, 480, new long[2]));
            long[] stages = new long[YuNetFaceDetector.STAGE_NAMES.length];
            long start = System.nanoTime();
            FaceDetector.Observation timed = detector.detectTimed(rgb, 640, 480, stages);
            long wall = System.nanoTime() - start;
            FaceDetector.Observation plain = detector.detect(rgb, 640, 480);
            assertEquals(plain.faceCount, timed.faceCount);
            assertEquals(plain.bboxX, timed.bboxX);
            assertEquals(plain.bboxY, timed.bboxY);
            assertEquals(plain.bboxW, timed.bboxW);
            assertEquals(plain.bboxH, timed.bboxH);
            long sum = 0;
            for (long s : stages) {
                assertTrue(s > 0, "every stage must record a positive duration");
                sum += s;
            }
            assertTrue(sum <= wall, "stage sum cannot exceed the wall time around the call");
        }
    }

    @Test
    void flatExtractionMatchesOrtGetValueOnTheRealModel() throws Exception {
        assumeTrue(Files.isRegularFile(MODEL), "YuNet model not present: " + MODEL);
        byte[] rgb = new byte[640 * 480 * 3];
        for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) ((i * 37) & 0xff);
        YuNetInputPreprocessor preprocessor = new YuNetInputPreprocessor();
        preprocessor.prepare(rgb, 640, 480);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (YuNetFaceDetector detector = new YuNetFaceDetector(MODEL);
             OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            try (OrtSession session = env.createSession(Files.readAllBytes(MODEL), options);
                 OnnxTensor input = OnnxTensor.createTensor(env,
                         FloatBuffer.wrap(preprocessor.buffer()), new long[]{1, 3, 640, 640});
                 OrtSession.Result result = session.run(Collections.singletonMap("input", input))) {
                detector.detect(rgb, 640, 480);
                float[][] flat = detector.flatOutputsSnapshot();
                assertEquals(YuNetFaceDetector.OUTPUT_NAMES.length, flat.length);
                for (int i = 0; i < flat.length; i++) {
                    String name = YuNetFaceDetector.OUTPUT_NAMES[i];
                    float[][][] nested = (float[][][]) result.get(name).get().getValue();
                    int channels = nested[0][0].length;
                    assertEquals(nested[0].length * channels, flat[i].length);
                    for (int a = 0; a < nested[0].length; a++) {
                        for (int c = 0; c < channels; c++) {
                            assertEquals(Float.floatToRawIntBits(nested[0][a][c]),
                                    Float.floatToRawIntBits(flat[i][a * channels + c]),
                                    name + " anchor " + a + " channel " + c);
                        }
                    }
                }
            }
        }
    }

    private static void setCandidate(float[][][][] outputs, int index,
                                     float cls, float obj, float left, float top,
                                     float logWidth, float logHeight) {
        outputs[0][0][index][0] = cls;
        outputs[3][0][index][0] = obj;
        outputs[6][0][index][0] = left;
        outputs[6][0][index][1] = top;
        outputs[6][0][index][2] = logWidth;
        outputs[6][0][index][3] = logHeight;
    }

    private static float[][][][] blankOutputs() {
        float[][][][] out = new float[12][][][];
        for (int level = 0; level < 3; level++) {
            int anchors = (640 / (8 << level)) * (640 / (8 << level));
            out[level] = new float[1][anchors][1];
            out[level + 3] = new float[1][anchors][1];
            out[level + 6] = new float[1][anchors][4];
            out[level + 9] = new float[1][anchors][10];
        }
        return out;
    }
}
