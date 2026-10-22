package liveness.policy;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.RetryRateLimiter;
import liveness.service.LivenessService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class StubPolicyTest {

    private LivenessService.LivenessResult run(MiniFasNetScorer pad, SameFaceChecker sf) {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).build();
        LivenessService s = new LivenessService(cfg, pad, sf,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        return s.performLiveness("subj", new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE), "mu",
                LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
    }

    private void withStubsForbidden(Runnable r) {
        String old = System.getProperty(StubPolicy.PROPERTY);
        System.clearProperty(StubPolicy.PROPERTY);
        try { r.run(); } finally { if (old != null) System.setProperty(StubPolicy.PROPERTY, old); }
    }

    @Test
    void stubScorerIsRefusedWhenStubsNotAllowed() {
        withStubsForbidden(() -> {
            MiniFasNetScorer pad = new MiniFasNetScorer(null);
            assertThrows(IllegalStateException.class, () -> run(pad, new SameFaceChecker(0.6)));
        });
    }

    @Test
    void stubbedSameFaceIsRefusedWhenStubsNotAllowed() {
        SameFaceChecker sf = new SameFaceChecker(0.6);
        sf.setStubResult(true);
        withStubsForbidden(() -> {
            MiniFasNetScorer pad = new MiniFasNetScorer(null);
            assertThrows(IllegalStateException.class, () -> run(pad, sf));
        });
    }

    @Test
    void stubsAreAllowedUnderTheTestProperty() {
        assertTrue(StubPolicy.stubsAllowed(), "surefire config must set liveness.allowStubs=true");
    }

    @Test
    void setStubScoreIsRefusedWhenStubsForbidden() {
        MiniFasNetScorer pad = new MiniFasNetScorer(null);
        withStubsForbidden(() -> {
            assertThrows(IllegalStateException.class, () -> pad.setStubScore(90));
        });
    }

    @Test
    void setStubResultIsRefusedWhenStubsForbidden() {
        SameFaceChecker sf = new SameFaceChecker(0.6);
        withStubsForbidden(() -> {
            assertThrows(IllegalStateException.class, () -> sf.setStubResult(true));
        });
    }

    @Test
    void crossStreamBinderWithStubbedSameFaceIsRefusedWhenStubsForbidden() {
        SameFaceChecker sf = new SameFaceChecker(0.6);
        sf.setStubResult(true);
        withStubsForbidden(() -> {
            assertThrows(IllegalStateException.class, () -> new liveness.android.CrossStreamBinder(sf, (pix, w, h) -> new float[128], 5000));
        });
    }
}
