package liveness.service;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;

import liveness.android.IsoFaceRgbDecoder;
import liveness.android.SbiIntentDeviceAdapter;
import liveness.core.FaceFrame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for {@link LivenessService} using
 * {@link MockDeviceAdapter} (§12.5, M3 gate).
 * <p>
 * Tests the full pipeline: device open → frame processing → decision →
 * challenge (if uncertain) → RCAPTURE → attestation/rejection.
 * <p>
 * M3 gate: bona fide → SUCCESS + attestation; print → not SUCCESS;
 * frozen/loop frames caught; device identity change rejected.
 */
class LivenessServiceIntegrationTest {

    private LivenessConfig config;
    private MiniFasNetScorer padScorer;
    private SameFaceChecker sameFaceChecker;
    private ChallengeGenerator challengeGen;
    private EcdsaSignerStub signer;

    @BeforeEach
    void setUp() {
        // Config with active challenges disabled for simpler bona fide testing,
        // low qualifying frame count for faster tests
        config = LivenessConfig.builder()
                .minQualifyingFrames(3)
                .activeEnabled(false)
                .maxRetries(3)
                .build();

        padScorer = new MiniFasNetScorer(null); // stub mode
        sameFaceChecker = new SameFaceChecker(0.6);
        sameFaceChecker.setStubResult(true); // stub: always same face
        challengeGen = new ChallengeGenerator(
                ChallengeGenerator.DEFAULT_POOL,
                new ArrayList<>());
        signer = new EcdsaSignerStub();
    }

    // ─── Happy path: bona fide → SUCCESS ───

    @Test
    void stateMachineAdvancesWhenBonaFideScoreInjected() {
        padScorer.setStubScore(85); // well above passThreshold (70)
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertEquals(Verdict.PASS, result.verdict());
        assertTrue(result.isSuccess());
        assertNotNull(result.attestation());
        assertNotNull(result.sessionId());
    }

    @Test
    void attestationSignatureValidWhenBonaFideScoreInjected() {
        padScorer.setStubScore(85);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.attestation().signature());
        assertNotNull(result.attestation().imageSha256());
        // Verify ECDSA signature
        assertTrue(signer.verify(
                result.attestation().canonical(),
                result.attestation().signature()));
    }

    @Test
    void stateMachineRejectsUncertainScoreWhenInjected() {
        padScorer.setStubScore(50); // UNCERTAIN band (between attackThreshold 30 and passThreshold 70)
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertFalse(result.isSuccess(), "Passive-only config must never pass UNCERTAIN band");
        assertNotEquals(Verdict.PASS, result.verdict());
    }

    // ─── Attack scenarios: print/screen/video → not SUCCESS ───

    @Test
    void stateMachineRejectsPrintScoreWhenInjected() {
        padScorer.setStubScore(15); // below attackThreshold (30)
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.PRINT);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertNotEquals(Verdict.PASS, result.verdict());
        assertFalse(result.isSuccess());
        assertNull(result.attestation());
    }

    @Test
    void stateMachineRejectsScreenScoreWhenInjected() {
        padScorer.setStubScore(10);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.SCREEN);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertNotEquals(Verdict.PASS, result.verdict());
        assertFalse(result.isSuccess());
    }

    @Test
    void stateMachineRejectsVideoScoreWhenInjected() {
        padScorer.setStubScore(20);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.VIDEO);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertNotEquals(Verdict.PASS, result.verdict());
        assertFalse(result.isSuccess());
    }

    // ─── Device error scenarios ───

    @Test
    void deviceDisconnectProducesDeviceFailure() {
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.DISCONNECT);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertEquals(Verdict.DEVICE_FAILURE, result.verdict());
        assertFalse(result.isSuccess());
    }

    @Test
    void deviceBusyProducesDeviceFailure() {
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BUSY);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertEquals(Verdict.DEVICE_FAILURE, result.verdict());
    }

    @Test
    void deviceTimeoutProducesDeviceFailure() {
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.TIMEOUT);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertEquals(Verdict.DEVICE_FAILURE, result.verdict());
    }

    // ─── Workflow variants ───

    @Test
    void operatorAuthWorkflowWithInjectedScore() {
        padScorer.setStubScore(85);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "operator-1", device, "machine-user-1",
                LivenessService.WorkflowType.OPERATOR_AUTH, null);

        assertEquals(Verdict.PASS, result.verdict());
        assertTrue(result.isSuccess());
    }

    @Test
    void supervisorAuthWorkflowWithInjectedScore() {
        padScorer.setStubScore(85);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "supervisor-1", device, "machine-user-1",
                LivenessService.WorkflowType.SUPERVISOR_AUTH, null);

        assertEquals(Verdict.PASS, result.verdict());
        assertTrue(result.isSuccess());
    }

    @Test
    void infantWorkflowWithInjectedScore() {
        // INFANT uses passive-only (§14 item 9)
        padScorer.setStubScore(85);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "infant-1", device, "machine-user-1",
                LivenessService.WorkflowType.INFANT, null);

        assertEquals(Verdict.PASS, result.verdict());
        assertTrue(result.isSuccess());
    }

    // ─── Liveness-failed captures are NEVER saved ───

    @Test
    void failedLivenessProducesNoAttestationWithInjectedScore() {
        padScorer.setStubScore(10); // clear attack
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.PRINT);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertNull(result.attestation(), "Liveness-failed captures must never produce an attestation");
    }

    // ─── Callback receives events ───

    @Test
    void callbackReceivesStateChangesWithInjectedScore() {
        padScorer.setStubScore(85);
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        java.util.List<String> statesReceived = Collections.synchronizedList(new ArrayList<>());

        LivenessCallback callback = new LivenessCallback() {
            @Override public void onStateChanged(String state, String message) {
                statesReceived.add(state);
            }
            @Override public void onPreviewFrame(byte[] jpegBytes) { }
            @Override public void onChallengePrompt(String action, long timeoutMs) { }
            @Override public void onProgress(int framesProcessed, int framesNeeded) { }
            @Override public void onResult(LivenessService.LivenessResult result) { }
            @Override public void onError(String errorCode, String message) { }
        };

        service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, callback);

        // Should have received at least DEVICE_CHECK and POSITIONING states
        assertFalse(statesReceived.isEmpty(), "Callback should receive state changes");
        assertTrue(statesReceived.contains("DEVICE_CHECK"), "Should pass through DEVICE_CHECK");
        assertTrue(statesReceived.contains("POSITIONING"), "Should pass through POSITIONING");
    }

    // ─── Uncertain score triggers challenge path ───

    @Test
    void uncertainScoreWithActiveChallengesEnabledWithInjectedScore() {
        LivenessConfig activeConfig = LivenessConfig.builder()
                .minQualifyingFrames(3)
                .activeEnabled(true)
                .minChallenges(1)
                .maxChallenges(2)
                .maxRetries(3)
                .build();

        padScorer.setStubScore(50); // between attack(30) and pass(70) → UNCERTAIN
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                activeConfig, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        java.util.List<String> statesReceived = Collections.synchronizedList(new ArrayList<>());

        LivenessCallback callback = new LivenessCallback() {
            @Override public void onStateChanged(String state, String message) {
                statesReceived.add(state);
            }
            @Override public void onPreviewFrame(byte[] jpegBytes) { }
            @Override public void onChallengePrompt(String action, long timeoutMs) { }
            @Override public void onProgress(int framesProcessed, int framesNeeded) { }
            @Override public void onResult(LivenessService.LivenessResult result) { }
            @Override public void onError(String errorCode, String message) { }
        };

        service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, callback);

        // With uncertain score, should reach CHALLENGE_PROMPT
        assertTrue(statesReceived.contains("CHALLENGE_PROMPT"),
                "Uncertain score should trigger active challenge path");
    }

    // ─── Same-face mismatch ───

    @Test
    void sameFaceMismatchDoesNotProduceSuccessWithInjectedScore() {
        padScorer.setStubScore(85);
        sameFaceChecker.setStubResult(false); // same-face FAILS
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        assertNotEquals(Verdict.PASS, result.verdict(),
                "Same-face mismatch should prevent SUCCESS");
        assertFalse(result.isSuccess());
    }

    // ─── Corrupt frame from device ───

    @Test
    void corruptFrameProducesDeviceFailure() {
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.CORRUPT_FRAME);
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        LivenessService service = new LivenessService(
                config, padScorer, sameFaceChecker, challengeGen, limiter, signer);

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", device, "machine-user-1",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);

        // Corrupt frames throw DeviceException → device disconnect path
        assertNotEquals(Verdict.PASS, result.verdict());
    }

    // ─── Pixel data verification (S19) ───

    @Test
    void framePixelsAreRealAndPixelDependentNotDummyBytes() throws Exception {
        MockDeviceAdapter device = new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
        device.open();
        liveness.core.FaceFrame frame1 = device.captureFrame();
        liveness.core.FaceFrame frame2 = device.captureFrame();

        assertNotNull(frame1.pixelData());
        assertTrue(frame1.hasPixelData());
        assertNotEquals(8, frame1.pixelData().length, "Must not be 8 dummy bytes (S19)");
        assertEquals(frame1.widthPx() * frame1.heightPx() * 3, frame1.pixelData().length,
                "Must be full RGB buffer matching width*height*3");

        // Pixels must be dependent on frame content and sequence
        assertFalse(java.util.Arrays.equals(frame1.pixelData(), frame2.pixelData()),
                "Different frames in stream must have varying pixel-dependent contents");

        // PadPreprocessor must accept the real frame pixels without throwing IllegalArgumentException
        float[] tensor = liveness.engine.PadPreprocessor.prepare(
                frame1.pixelData(), frame1.widthPx(), frame1.heightPx(),
                frame1.faceBboxX(), frame1.faceBboxY(), frame1.faceBboxWidth(), frame1.faceBboxHeight());
        assertNotNull(tensor);
        assertEquals(3 * 128 * 128, tensor.length);
    }
}
