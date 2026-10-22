package liveness.service;

import liveness.android.FaceEmbedder;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Same-face is decided from embeddings, with NO setStubResult anywhere. */
class RealSameFaceWiringTest {

    private LivenessService service(SameFaceChecker sf, FaceEmbedder emb) {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
        MiniFasNetScorer pad = new MiniFasNetScorer(null);
        pad.setStubScore(85);
        LivenessService s = new LivenessService(cfg, pad, sf,
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        if (emb != null) s.setFaceEmbedder(emb);
        return s;
    }

    private LivenessService.LivenessResult run(LivenessService s) {
        return s.performLiveness("subj", new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE),
                "mu", LivenessService.WorkflowType.RESIDENT_REGISTRATION, null);
    }

    @Test
    void matchingEmbeddingsPass() {
        FaceEmbedder same = (rgb, w, h) -> new float[]{1f, 0f, 0f, 0f};
        assertEquals(Verdict.PASS, run(service(new SameFaceChecker(0.6), same)).verdict());
    }

    @Test
    void differentEmbeddingsDoNotPass() {
        AtomicInteger n = new AtomicInteger();
        FaceEmbedder differing = (rgb, w, h) -> n.getAndIncrement() % 2 == 0
                ? new float[]{1f, 0f, 0f, 0f} : new float[]{0f, 1f, 0f, 0f};
        assertNotEquals(Verdict.PASS, run(service(new SameFaceChecker(0.6), differing)).verdict());
    }

    @Test
    void noEmbedderAndNoStubFailsClosed() {
        assertNotEquals(Verdict.PASS, run(service(new SameFaceChecker(0.6), null)).verdict());
    }

    @Test
    void embedderFailureFailsClosed() {
        FaceEmbedder boom = (rgb, w, h) -> { throw new IllegalArgumentException("x"); };
        assertNotEquals(Verdict.PASS, run(service(new SameFaceChecker(0.6), boom)).verdict());
    }

    @Test
    void renamedGridEmbedderIsStillRefusedOutsideDev() {
        String prev = System.getProperty(liveness.policy.StubPolicy.PROPERTY);
        try {
            // Class does not contain "Grid" or "Reference" in simple name, but productionGrade() defaults to false
            FaceEmbedder renamedMock = new FaceEmbedder() {
                @Override
                public float[] embed(byte[] rgb, int width, int height) {
                    return new float[]{1f, 0f};
                }
            };
            LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
            MiniFasNetScorer pad = new MiniFasNetScorer(null); // no setStubScore called
            LivenessService s = new LivenessService(cfg, pad, new SameFaceChecker(0.6),
                    new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                    new RetryRateLimiter(3), new EcdsaSignerStub());
            s.setFaceEmbedder(renamedMock);

            System.clearProperty(liveness.policy.StubPolicy.PROPERTY);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> run(s));
            assertTrue(e.getMessage().contains("refusing to start outside dev without a production FaceEmbedder"));
        } finally {
            if (prev != null) System.setProperty(liveness.policy.StubPolicy.PROPERTY, prev);
        }
    }

}

