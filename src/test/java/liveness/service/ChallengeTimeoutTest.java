package liveness.service;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** An unanswered challenge must time out and leave CHALLENGE_WAIT instead of staying pending. */
class ChallengeTimeoutTest {

    @Test
    void unansweredChallengeTimesOutAndSessionDoesNotStayPending() {
        LivenessConfig cfg = LivenessConfig.builder()
                .minQualifyingFrames(3)
                .activeEnabled(true)
                .minChallenges(1)
                .maxChallenges(2)
                .maxRetries(1)
                .challengeTimeoutMs(1)
                .build();

        MiniFasNetScorer padScorer = new MiniFasNetScorer(null);
        padScorer.setStubScore(50);
        SameFaceChecker sameFace = new SameFaceChecker(0.6);
        sameFace.setStubResult(true);
        ChallengeGenerator gen = new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>());

        LivenessService service = new LivenessService(
                cfg, padScorer, sameFace, gen, new RetryRateLimiter(3), new EcdsaSignerStub());

        List<String> states = Collections.synchronizedList(new ArrayList<>());
        LivenessCallback cb = new LivenessCallback() {
            @Override public void onStateChanged(String state, String message) { states.add(state); }
            @Override public void onPreviewFrame(byte[] jpegBytes) { }
            @Override public void onChallengePrompt(String action, long timeoutMs) { }
            @Override public void onProgress(int framesProcessed, int framesNeeded) { }
            @Override public void onResult(LivenessService.LivenessResult result) { }
            @Override public void onError(String errorCode, String message) { }
        };

        LivenessService.LivenessResult result = service.performLiveness(
                "subject-1", new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE),
                "machine-user-1", LivenessService.WorkflowType.RESIDENT_REGISTRATION, cb);

        assertTrue(states.contains("CHALLENGE_PROMPT"), "challenge should be issued");
        assertTrue(states.contains("RETRY_AVAILABLE"), "expired challenge should time out to RETRY_AVAILABLE");
        assertNotEquals(Verdict.PENDING, result.verdict(), "session must not stay pending");
    }
}
