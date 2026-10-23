package liveness.desktop;

import liveness.challenge.ChallengeGenerator;
import liveness.android.TestGridEmbedder;
import liveness.core.FaceFrame;
import liveness.core.FrameQuality;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.DeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real-pixel LivenessService integration tests (T4), split out of
 * liveness.service.LivenessServiceIntegrationTest unchanged. They decode PNG/JPEG through
 * javax.imageio, so they live in the desktop-only liveness.desktop package and are excluded from
 * the Android unit-test source set. They still run under Maven.
 */
class LivenessServiceRealPixelIntegrationTest {

    private ChallengeGenerator challengeGen;
    private EcdsaSignerStub signer;

    @BeforeEach
    void setUp() {
        challengeGen = new ChallengeGenerator(
                ChallengeGenerator.DEFAULT_POOL,
                new ArrayList<>());
        signer = new EcdsaSignerStub();
    }


    private static byte[] toRgb(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] rgb = new byte[w * h * 3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getRGB(x, y);
                int idx = (y * w + x) * 3;
                rgb[idx] = (byte) ((pixel >> 16) & 0xFF);     // R
                rgb[idx + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
                rgb[idx + 2] = (byte) (pixel & 0xFF);         // B
            }
        }
        return rgb;
    }

    @Test
    void realPixelDependentBonaFideReachesSuccess() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        assumeTrue(Files.exists(modelPath), "Model file missing: " + modelPath);
        Path imagePath = Paths.get("datasets/onot/digital/S108161842/S108161842-GM-A52-EEA-T11-S1-LH-C1-I0003-F00.png");
        assumeTrue(Files.exists(imagePath), "Bona fide image missing: " + imagePath);

        BufferedImage img = ImageIO.read(imagePath.toFile());
        assertNotNull(img);
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] rgb = toRgb(img);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        byte[] isoJpeg = SbiCaptureFinalIntegrationTest.record2005(baos.toByteArray(), 0, w, h, 0);

        try (MiniFasNetScorer realPadScorer = new MiniFasNetScorer(modelPath)) {
            assertFalse(realPadScorer.isStubMode(), "PadScorer must not be in stub mode");

            SameFaceChecker realSameFaceChecker = new SameFaceChecker(0.48);
            assertFalse(realSameFaceChecker.isStubbed(), "SameFaceChecker must not be stubbed");

            TestGridEmbedder embedder = new TestGridEmbedder();
            AtomicInteger seq = new AtomicInteger(1);
            long now = System.currentTimeMillis();

            DeviceAdapter device = new DeviceAdapter() {
                private boolean open = false;
                @Override public void open() { open = true; }
                @Override public void close() { open = false; }
                @Override public boolean isOpen() { return open; }
                @Override public FaceFrame captureFrame() {
                    int s = seq.getAndIncrement();
                    byte[] copy = rgb.clone();
                    copy[0] = (byte) (copy[0] ^ (s & 1));
                    return new FaceFrame(
                            FrameQuality.GOOD, "real-cam-sig", 1,
                            now + (s * 33L), s,
                            w, h, copy,
                            50, 50, 400, 400,
                            0.0, 0.0, 0.0, 1, 150.0, 120.0, null, 0L, null);
                }
                @Override public FaceFrame captureFinal(String boundTx) {
                    int s = seq.getAndIncrement();
                    return new FaceFrame(
                            FrameQuality.GOOD, "real-cam-sig", 1,
                            System.currentTimeMillis(), s,
                            w, h, rgb.clone(),
                            50, 50, 400, 400,
                            0.0, 0.0, 0.0, 1, 150.0, 120.0, null, 0L, isoJpeg);
                }
            };

            LivenessConfig realConfig = LivenessConfig.builder()
                    .minQualifyingFrames(3)
                    .activeEnabled(false)
                    .maxRetries(3)
                    .build();

            LivenessService service = new LivenessService(
                    realConfig, realPadScorer, realSameFaceChecker, challengeGen,
                    new RetryRateLimiter(3), signer);
            service.setFaceEmbedder(embedder);

            LivenessService.LivenessResult result = service.performLiveness(
                    "subject-real-bf", device, "mu-real-1",
                    LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

            assertEquals(Verdict.PASS, result.verdict(), "Real bona fide must produce PASS");
            assertTrue(result.isSuccess());
            assertNotNull(result.attestation());
            assertTrue(signer.verify(result.attestation().canonical(), result.attestation().signature()));
        }
    }

    @Test
    void realPixelDependentAttackRefusedByPipeline() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        assumeTrue(Files.exists(modelPath), "Model file missing: " + modelPath);
        Path attackPath = Paths.get("datasets/attacks/print/print_007_f0.png");
        assumeTrue(Files.exists(attackPath), "Attack image missing: " + attackPath);

        BufferedImage img = ImageIO.read(attackPath.toFile());
        assertNotNull(img);
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] rgb = toRgb(img);

        try (MiniFasNetScorer realPadScorer = new MiniFasNetScorer(modelPath)) {
            assertFalse(realPadScorer.isStubMode(), "PadScorer must not be in stub mode");

            SameFaceChecker realSameFaceChecker = new SameFaceChecker(0.48);
            assertFalse(realSameFaceChecker.isStubbed(), "SameFaceChecker must not be stubbed");

            TestGridEmbedder embedder = new TestGridEmbedder();
            AtomicInteger seq = new AtomicInteger(1);
            long now = System.currentTimeMillis();

            DeviceAdapter device = new DeviceAdapter() {
                private boolean open = false;
                @Override public void open() { open = true; }
                @Override public void close() { open = false; }
                @Override public boolean isOpen() { return open; }
                @Override public FaceFrame captureFrame() {
                    int s = seq.getAndIncrement();
                    byte[] copy = rgb.clone();
                    copy[0] = (byte) (copy[0] ^ (s & 1));
                    return new FaceFrame(
                            FrameQuality.GOOD, "attack-cam-sig", 1,
                            now + (s * 33L), s,
                            w, h, copy,
                            0, 0, w, h,
                            0.0, 0.0, 0.0, 1, 150.0, 120.0, null, 0L, null);
                }
            };

            LivenessConfig realConfig = LivenessConfig.builder()
                    .minQualifyingFrames(3)
                    .activeEnabled(false)
                    .maxRetries(3)
                    .build();

            LivenessService service = new LivenessService(
                    realConfig, realPadScorer, realSameFaceChecker, challengeGen,
                    new RetryRateLimiter(3), signer);
            service.setFaceEmbedder(embedder);

            LivenessService.LivenessResult result = service.performLiveness(
                    "subject-real-atk", device, "mu-real-2",
                    LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

            assertNotEquals(Verdict.PASS, result.verdict(), "Attack presentation must not PASS");
            assertFalse(result.isSuccess(), "Attack presentation must not succeed");
            assertNull(result.attestation(), "Attack presentation must never receive attestation");
        }
    }
}
