package liveness.android;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;
import liveness.util.Sha256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static liveness.android.AndroidTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end: CameraX frames (T3) judge liveness, SBI rCapture (T1) is the accepted image, bound by nonce +
 * time gap + same-face, driven by the real LivenessService with DEFAULT config (5 qualifying frames).
 */
class AndroidPipelineIntegrationTest {

    private static final byte[] SBI_IMAGE = new byte[48];
    static { for (int i = 0; i < SBI_IMAGE.length; i++) SBI_IMAGE[i] = (byte) (i * 5 + 3); }

    private Thread pump;
    private final AtomicBoolean pumping = new AtomicBoolean(true);
    private final EcdsaSignerStub signer = new EcdsaSignerStub();
    private final AtomicInteger embedCalls = new AtomicInteger();
    private volatile boolean sameFace = true;
    private volatile String txOverride = null;
    private volatile boolean sbiTimesOut = false;
    private FakeLauncher launcher;
    private FakeProbe probe;
    private CameraXDeviceAdapter camera;

    @BeforeEach
    void setUp() {
        probe = new FakeProbe();
        launcher = new FakeLauncher();
        launcher.captureHandler = req -> {
            String tx = txOverride != null ? txOverride : (String) MiniJson.parseObject(req.inputString()).get("transactionId");
            String ts = Instant.now().toString().replaceAll("\\.\\d+Z$", "Z");
            return new SbiIntentLauncher.ActivityResult(SbiIntentLauncher.ActivityResult.RESULT_OK,
                    captureResponse(tx, ts, SBI_IMAGE, "0"));
        };
        camera = new CameraXDeviceAdapter("cam-front", oneFace(), 3, 2000, 200);
        pump = new Thread(() -> {
            long seed = 1;
            while (pumping.get()) {
                camera.onYuvFrame(texturedFrame(320, 240, System.currentTimeMillis(), 0, seed++));
                try { Thread.sleep(15); } catch (InterruptedException e) { return; }
            }
        });
        pump.setDaemon(true);
        pump.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        pumping.set(false);
        pump.join(500);
    }

    private AndroidDeviceAdapter device() {
        // Host frame embeds as {1,0,0}; the SBI image embeds as {1,0,0} (same) or {0,1,0} (different person).
        FaceEmbedder embedder = (rgb, w, h) -> {
            boolean hostCall = embedCalls.incrementAndGet() % 2 == 1;
            return (hostCall || sameFace) ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
        };
        CrossStreamBinder binder = new CrossStreamBinder(new SameFaceChecker(0.2), embedder, 10_000);
        SbiIntentDeviceAdapter sbi = new SbiIntentDeviceAdapter(launcher, binder, camera::latestFrame,
                bytes -> new SbiIntentDeviceAdapter.RgbDecoder.Decoded(bytes, 4, 4),
                SbiJwtVerifier.disabledForDevelopment(), "Developer", "Registration", 3000);
        return new AndroidDeviceAdapter(new EnvironmentIntegrityChecker(probe), camera, sbi);
    }

    private LivenessService.LivenessResult run() {
        LivenessConfig cfg = LivenessConfig.builder().activeEnabled(false).build(); // defaults: 5 qualifying frames
        MiniFasNetScorer scorer = new MiniFasNetScorer(null);
        scorer.setStubScore(85);
        SameFaceChecker sessionChecker = new SameFaceChecker(0.6);
        sessionChecker.setStubResult(true);
        LivenessService svc = new LivenessService(cfg, scorer, sessionChecker,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), signer);
        AndroidDeviceAdapter d = device();
        try {
            return svc.performLiveness("subject-9", d, "machine-user-9",
                    LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
        } finally {
            d.close();
        }
    }

    @Test
    void happyPathAttestsTheSbiImageNotTheHostFrame() {
        LivenessService.LivenessResult r = run();
        assertEquals(Verdict.PASS, r.verdict(), "failure reason: " + r.failureReason());
        assertTrue(r.isSuccess());
        assertEquals(Sha256.hex(Sha256.digest(SBI_IMAGE)), r.attestation().imageSha256());
        assertEquals(64, r.attestation().imageSha256().length(), "a SHA-256 hex digest, not the encoded image");
        assertEquals("T1", r.attestation().deviceTier());
        assertTrue(r.attestation().verify(signer));
        SbiIntentRequest sent = launcher.launched.get(launcher.launched.size() - 1);
        assertEquals("com.sbi.face.rCapture", sent.action());
        assertTrue(SbiIntentBridge.isValidTransactionId(
                (String) MiniJson.parseObject(sent.inputString()).get("transactionId")),
                "the id sent to a spec-compliant SBI must satisfy the 4..50 [A-Za-z0-9-] rule");
        assertEquals("HOST_CAMERA+SBI_RCAPTURE_INTENT", r.attestation().mode(),
                "attestation must not claim MDS STREAM on Android");
    }

    @Test
    void differentPersonAtFinalCaptureIsNeverAttested() {
        sameFace = false;
        LivenessService.LivenessResult r = run();
        assertFalse(r.isSuccess());
        assertNull(r.attestation());
        assertEquals(Verdict.DEVICE_FAILURE, r.verdict());
    }

    @Test
    void swappedTransactionIdIsNeverAttested() {
        txOverride = "attacker-session-1234";
        LivenessService.LivenessResult r = run();
        assertFalse(r.isSuccess());
        assertNull(r.attestation());
    }

    @Test
    void sbiTimeoutIsADeviceErrorWithoutAttestation() {
        launcher.captureHandler = null; // SBI never answers
        LivenessService.LivenessResult r = run();
        assertFalse(r.isSuccess());
        assertNull(r.attestation());
        assertEquals(Verdict.DEVICE_FAILURE, r.verdict());
    }

    @Test
    void rootedHostStopsBeforeAnyCaptureHappens() {
        probe.files.add("/system/xbin/su");
        LivenessService.LivenessResult r = run();
        assertEquals(Verdict.DEVICE_FAILURE, r.verdict());
        assertNull(r.attestation());
        assertTrue(launcher.launched.isEmpty());
        assertEquals(0, camera.latestFrame() == null ? 0 : camera.latestFrame().sequenceNum());
    }
}
