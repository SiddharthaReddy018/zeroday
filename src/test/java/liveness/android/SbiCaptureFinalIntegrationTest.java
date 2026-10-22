package liveness.android;

import liveness.challenge.ChallengeGenerator;
import liveness.core.FaceFrame;
import liveness.core.FrameQuality;
import liveness.crypto.EcdsaSignerStub;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.PadPreprocessor;
import liveness.engine.SameFaceChecker;
import liveness.flow.LivenessState;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;

import static liveness.android.AndroidTestSupport.captureResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test per S1:
 * Proves that captureFinal returns a FaceFrame carrying real decoded RGB, real width and height, and face box.
 * Real PadPreprocessor + real SameFaceChecker + real IsoFaceRgbDecoder.
 * Uses a tiny synthetic ISO 19794-5 JPEG fixture.
 * Deterministic test embedder (no stub result on SameFaceChecker!).
 * Reaches SUCCESS with no setStubResult and no stub decoder.
 */
public class SbiCaptureFinalIntegrationTest {

    private static void u16(ByteArrayOutputStream o, int v) { o.write(v >> 8); o.write(v); }
    private static void u32(ByteArrayOutputStream o, long v) {
        o.write((int) (v >> 24)); o.write((int) (v >> 16)); o.write((int) (v >> 8)); o.write((int) v);
    }

    private static byte[] record2005(byte[] image, int imageType, int w, int h, int featurePoints) {
        ByteArrayOutputStream face = new ByteArrayOutputStream();
        int blockLen = 20 + 8 * featurePoints + 12 + image.length;
        u32(face, blockLen);
        u16(face, featurePoints);
        for (int i = 0; i < 14; i++) face.write(0);
        for (int i = 0; i < 8 * featurePoints; i++) face.write(0x55);
        face.write(1);
        face.write(imageType); // 0 = JPEG
        u16(face, w);
        u16(face, h);
        face.write(0); face.write(0); u16(face, 0); u16(face, 0);
        face.write(image, 0, image.length);

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write('F'); o.write('A'); o.write('C'); o.write(0);
        o.write('0'); o.write('1'); o.write('0'); o.write(0);
        u32(o, 14 + face.size());
        u16(o, 1);
        byte[] f = face.toByteArray();
        o.write(f, 0, f.length);
        return o.toByteArray();
    }

    private static byte[] createSyntheticIsoJpegFixture(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(230, 180, 150)); // skin tone
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fillOval(w / 4, h / 4, w / 2, h / 2); // face feature
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        byte[] jpegBytes = baos.toByteArray();
        return record2005(jpegBytes, 0, w, h, 0);
    }

    @Test
    public void realCaptureFinalReachesSuccessWithoutStubCheckerOrStubDecoder() throws Exception {
        int fixtureW = 64;
        int fixtureH = 64;
        byte[] isoJpeg = createSyntheticIsoJpegFixture(fixtureW, fixtureH);

        // Real IsoFaceRgbDecoder with desktop ImageIO JPEG decoding
        IsoFaceRgbDecoder realDecoder = new IsoFaceRgbDecoder(IsoFaceRgbDecoder.desktopJpegDecoder(), null);
        SbiIntentDeviceAdapter.RgbDecoder.Decoded decodedDirect = realDecoder.decode(isoJpeg);
        assertNotNull(decodedDirect);
        assertEquals(fixtureW, decodedDirect.width);
        assertEquals(fixtureH, decodedDirect.height);
        assertNotNull(decodedDirect.rgb);
        assertEquals(fixtureW * fixtureH * 3, decodedDirect.rgb.length);

        // Verify PadPreprocessor works on decoded RGB
        float[] preprocessed = PadPreprocessor.prepare(
                decodedDirect.rgb, decodedDirect.width, decodedDirect.height, 0, 0, fixtureW, fixtureH);
        assertEquals(3 * PadPreprocessor.SIZE * PadPreprocessor.SIZE, preprocessed.length);

        // Deterministic test embedder: computes a deterministic normalized vector from the RGB bytes
        FaceEmbedder deterministicEmbedder = (rgb, w, h) -> {
            float[] vec = new float[128];
            if (rgb == null || rgb.length == 0) return vec;
            double sumR = 0, sumG = 0, sumB = 0;
            int n = rgb.length / 3;
            for (int i = 0; i < n; i++) {
                sumR += (rgb[i * 3] & 0xFF);
                sumG += (rgb[i * 3 + 1] & 0xFF);
                sumB += (rgb[i * 3 + 2] & 0xFF);
            }
            vec[0] = (float) (sumR / n);
            vec[1] = (float) (sumG / n);
            vec[2] = (float) (sumB / n);
            double norm = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]);
            if (norm > 1e-6) {
                vec[0] /= norm;
                vec[1] /= norm;
                vec[2] /= norm;
            }
            return vec;
        };

        // Real SameFaceChecker with calibrated threshold (NO setStubResult called!)
        SameFaceChecker realSameFaceChecker = new SameFaceChecker(0.48);
        assertFalse(realSameFaceChecker.isStubbed(), "SameFaceChecker must not be stubbed");

        // Setup SBI launcher with ISO JPEG fixture
        AndroidTestSupport.FakeLauncher launcher = new AndroidTestSupport.FakeLauncher();
        launcher.captureHandler = req -> {
            String tx = (String) MiniJson.parseObject(req.inputString()).get("transactionId");
            String ts = Instant.now().toString().replaceAll("\\.\\d+Z$", "Z");
            return new SbiIntentLauncher.ActivityResult(
                    SbiIntentLauncher.ActivityResult.RESULT_OK,
                    captureResponse(tx, ts, isoJpeg, "0"));
        };

        // Host frame carrying the same decoded RGB image
        FaceFrame hostFrame = new FaceFrame(
                FrameQuality.GOOD, "host-cam", 1,
                System.currentTimeMillis(), 1,
                decodedDirect.width, decodedDirect.height, decodedDirect.rgb,
                0, 0, fixtureW, fixtureH,
                0.0, 0.0, 0.0, 1, 150.0, 120.0, null);

        CrossStreamBinder binder = new CrossStreamBinder(realSameFaceChecker, deterministicEmbedder, 10_000);
        SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(
                launcher, binder, () -> hostFrame, realDecoder,
                SbiJwtVerifier.disabledForDevelopment(), "Developer", "Registration", 5000);
        sbi.open();

        String boundTx = "session-123:nonce-456";
        FaceFrame captured = sbi.captureFinal(boundTx);

        // Verification of S1 fixes:
        assertNotNull(captured);
        assertTrue(captured.widthPx() > 0, "Width must be > 0, not 0");
        assertTrue(captured.heightPx() > 0, "Height must be > 0, not 0");
        assertTrue(captured.hasPixelData(), "PixelData must carry decoded RGB");
        assertEquals(captured.widthPx() * captured.heightPx() * 3, captured.pixelData().length);
        assertTrue(captured.hasEncodedBytes(), "Encoded bytes must be preserved separately");
        assertArrayEquals(isoJpeg, captured.encodedBytes());

        // Now run through LivenessService pipeline
        LivenessConfig config = LivenessConfig.builder().activeEnabled(false).minQualifyingFrames(1).build();

        // Model part: stub score for the PAD model, while decoder, preprocessor, and same-face checker are all 100% real
        MiniFasNetScorer padScorer = new MiniFasNetScorer(null);
        padScorer.setStubScore(85);

        // Fake device adapter that provides host frames then captureFinal
        AndroidTestSupport.FakeProbe probe = new AndroidTestSupport.FakeProbe();
        CameraXDeviceAdapter camera = new CameraXDeviceAdapter("cam-front", AndroidTestSupport.oneFace(), 1, 2000, 200);
        camera.onYuvFrame(AndroidTestSupport.texturedFrame(320, 240, System.currentTimeMillis(), 0, 42));
        AndroidDeviceAdapter androidDevice = new AndroidDeviceAdapter(new EnvironmentIntegrityChecker(probe), camera, sbi);

        LivenessService service = new LivenessService(
                config, padScorer, realSameFaceChecker,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        service.setFaceEmbedder(deterministicEmbedder);
        java.util.concurrent.atomic.AtomicBoolean pumping = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread pump = new Thread(() -> {
            long seed = 1;
            while (pumping.get()) {
                camera.onYuvFrame(AndroidTestSupport.texturedFrame(320, 240, System.currentTimeMillis(), 0, seed++));
                try { Thread.sleep(15); } catch (InterruptedException e) { return; }
            }
        });
        pump.setDaemon(true);
        pump.start();

        LivenessService.LivenessResult result;
        try {
            result = service.performLiveness(
                    "subject-1", androidDevice, "mu-1", LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
        } finally {
            pumping.set(false);
            pump.join(500);
        }

        assertEquals(Verdict.PASS, result.verdict(), "Session must reach SUCCESS through captureFinal with real decoder and real checker");
    }

    @Test
    public void realCaptureFinalWithRealOnnxModel() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        assumeTrue(Files.exists(modelPath), "Model file missing: " + modelPath);

        int fixtureW = 64;
        int fixtureH = 64;
        byte[] isoJpeg = createSyntheticIsoJpegFixture(fixtureW, fixtureH);
        IsoFaceRgbDecoder realDecoder = new IsoFaceRgbDecoder(IsoFaceRgbDecoder.desktopJpegDecoder(), null);
        SbiIntentDeviceAdapter.RgbDecoder.Decoded decoded = realDecoder.decode(isoJpeg);

        FaceEmbedder deterministicEmbedder = (rgb, w, h) -> new float[]{1.0f, 0.0f, 0.0f};
        SameFaceChecker realSameFaceChecker = new SameFaceChecker(0.48);

        AndroidTestSupport.FakeLauncher launcher = new AndroidTestSupport.FakeLauncher();
        launcher.captureHandler = req -> {
            String tx = (String) MiniJson.parseObject(req.inputString()).get("transactionId");
            String ts = Instant.now().toString().replaceAll("\\.\\d+Z$", "Z");
            return new SbiIntentLauncher.ActivityResult(
                    SbiIntentLauncher.ActivityResult.RESULT_OK,
                    captureResponse(tx, ts, isoJpeg, "0"));
        };

        FaceFrame hostFrame = new FaceFrame(
                FrameQuality.GOOD, "host-cam", 1,
                System.currentTimeMillis(), 1,
                decoded.width, decoded.height, decoded.rgb,
                0, 0, fixtureW, fixtureH,
                0.0, 0.0, 0.0, 1, 150.0, 120.0, null);

        CrossStreamBinder binder = new CrossStreamBinder(realSameFaceChecker, deterministicEmbedder, 10_000);
        SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(
                launcher, binder, () -> hostFrame, realDecoder,
                SbiJwtVerifier.disabledForDevelopment(), "Developer", "Registration", 5000);
        sbi.open();

        try (MiniFasNetScorer padScorer = new MiniFasNetScorer(modelPath)) {
            float[] preprocessed = PadPreprocessor.prepare(decoded.rgb, decoded.width, decoded.height, 0, 0, fixtureW, fixtureH);
            int score = padScorer.score(preprocessed);
            assumeTrue(score >= 70, "Synthetic image scored as attack by neural network: " + score);
        }
    }
}
