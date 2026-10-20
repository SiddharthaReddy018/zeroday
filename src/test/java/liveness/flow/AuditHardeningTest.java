package liveness.flow;

import liveness.challenge.ChallengeGenerator;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.evaluation.IadMetrics;
import liveness.evaluation.WilsonConfidenceInterval;
import liveness.policy.LivenessConfig;
import liveness.service.LivenessService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for the Oct 2026 external audit fixes. */
class AuditHardeningTest {

    private LivenessService.LivenessResult runWithPadScore(int padScore) {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
        MiniFasNetScorer pad = new MiniFasNetScorer(null);
        pad.setStubScore(padScore);
        SameFaceChecker sf = new SameFaceChecker(0.6);
        sf.setStubResult(true);
        LivenessService s = new LivenessService(cfg, pad, sf,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        return s.performLiveness("subj", new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE),
                "mu", LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
    }

    @Test
    void clearBonaFideScoreStillPasses() {
        assertEquals(Verdict.PASS, runWithPadScore(95).verdict());
    }

    @Test
    void scoreBelowPassThresholdNeverProducesAttestation() {
        LivenessConfig c = LivenessConfig.builder().build();
        int mid = (c.attackThreshold() + c.passThreshold()) / 2;   // UNCERTAIN band
        LivenessService.LivenessResult r = runWithPadScore(mid);
        assertNotEquals(Verdict.PASS, r.verdict());
        assertNull(r.attestation());
    }

    @Test
    void supervisorCredentialCheckRefusedWithoutAllowStubs() {
        String old = System.getProperty("liveness.allowStubs");
        System.clearProperty("liveness.allowStubs");
        try {
            assertThrows(IllegalStateException.class, () -> new SupervisorAuth("sup").authenticate("anything"));
        } finally {
            if (old != null) System.setProperty("liveness.allowStubs", old);
        }
    }

    @Test
    void iadRateIsNaNWithNoTrials() {
        IadMetrics m = new IadMetrics();
        assertTrue(Double.isNaN(m.freezeDetectionRate()));
        assertTrue(Double.isNaN(m.loopDetectionRate()));
        assertTrue(Double.isNaN(m.nonceSwapRejectionRate()));
    }

    @Test
    void wilsonStringHasTwoBoundsNotCenter() {
        String s = WilsonConfidenceInterval.compute(0, 9).toString();
        assertEquals(1, s.chars().filter(ch -> ch == ',').count(), s);
    }
}
