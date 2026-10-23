package liveness.service;

import liveness.android.FaceEmbedder;
import liveness.challenge.ChallengeGenerator;
import liveness.core.FaceFrame;
import liveness.crypto.EcdsaSignerStub;
import liveness.device.DeviceAdapter;
import liveness.device.DeviceException;
import liveness.device.MockDeviceAdapter;
import liveness.engine.MiniFasNetScorer;
import liveness.engine.SameFaceChecker;
import liveness.flow.LivenessSession;
import liveness.flow.LivenessState;
import liveness.flow.RetryRateLimiter;
import liveness.flow.Verdict;
import liveness.policy.LivenessConfig;
import liveness.policy.StubPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * startBenchmarkSession skips only the production-embedder guard, and the session it returns can never PASS.
 * The production startSession guard must stay exactly as strict as before.
 */
class BenchmarkSessionGuardTest {

    private static final Path INT8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");

    private static final FaceEmbedder PRODUCTION_SAME = new FaceEmbedder() {
        @Override public float[] embed(byte[] rgb, int w, int h) { return new float[]{1f, 0f, 0f, 0f}; }
        @Override public boolean productionGrade() { return true; }
    };

    private static final FaceEmbedder NOT_PRODUCTION = (rgb, w, h) -> new float[]{1f, 0f};

    private static LivenessService service(FaceEmbedder embedder) {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
        MiniFasNetScorer pad = new MiniFasNetScorer(null);
        pad.setStubScore(85);
        LivenessService s = new LivenessService(cfg, pad, new SameFaceChecker(0.6),
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                new RetryRateLimiter(3), new EcdsaSignerStub());
        if (embedder != null) s.setFaceEmbedder(embedder);
        return s;
    }

    private static DeviceAdapter bonaFide() {
        return new MockDeviceAdapter(MockDeviceAdapter.Scenario.BONA_FIDE);
    }

    /** Same drive as LivenessService.performLiveness for an active-disabled run, but on a session we opened. */
    private static Verdict drive(LivenessSession session, DeviceAdapter device) throws DeviceException {
        LivenessState state = session.start();
        state = session.openDevice();
        int frames = 0;
        while (!state.isTerminal() && frames < 300) {
            state = session.currentState();
            switch (state) {
                case POSITIONING:
                case PASSIVE:
                case CHALLENGE_WAIT: {
                    FaceFrame f = device.captureFrame();
                    if (f != null) {
                        state = session.processFrame(f);
                        frames++;
                    }
                    break;
                }
                case FINAL_CAPTURE: {
                    String tx = session.boundTransactionId();
                    long requestTs = System.currentTimeMillis();
                    FaceFrame r = device.captureFinal(tx);
                    if (r != null && r.hasPixelData()) {
                        String returned = device.lastReturnedTransactionId();
                        state = session.processRCapture(r, returned != null ? returned : tx, r.timestampMs(), requestTs);
                    } else {
                        state = session.rcaptureFailure();
                    }
                    break;
                }
                case RETRY_AVAILABLE:
                    state = session.attemptRetry();
                    break;
                default:
                    break;
            }
        }
        return session.verdict();
    }

    @Test
    void productionStartSessionStillRefusesNonProductionEmbedderOutsideDev() {
        String prev = System.getProperty(StubPolicy.PROPERTY);
        try {
            LivenessService withFake = service(NOT_PRODUCTION);
            LivenessService withNone = service(null);
            System.clearProperty(StubPolicy.PROPERTY);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> withFake.startSession("s", bonaFide(), "mu", LivenessService.WorkflowType.RESIDENT_REGISTRATION));
            assertTrue(e.getMessage().contains("refusing to start outside dev without a production FaceEmbedder"));
            assertThrows(IllegalStateException.class,
                    () -> withNone.startSession("s", bonaFide(), "mu", LivenessService.WorkflowType.RESIDENT_REGISTRATION));
        } finally {
            if (prev != null) System.setProperty(StubPolicy.PROPERTY, prev);
        }
    }

    /** Outside dev the session constructor also refuses a stub PAD scorer, so this needs the real model. */
    @Test
    void benchmarkSessionOpensOutsideDevWithoutAnyEmbedder() {
        assumeTrue(Files.exists(INT8), "INT8 model not present; SKIPPED (not passed): " + INT8);
        String prev = System.getProperty(StubPolicy.PROPERTY);
        try (MiniFasNetScorer pad = new MiniFasNetScorer(INT8)) {
            LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
            LivenessService svc = new LivenessService(cfg, pad, new SameFaceChecker(0.6),
                    new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                    new RetryRateLimiter(3), new EcdsaSignerStub());
            System.clearProperty(StubPolicy.PROPERTY);
            assertThrows(IllegalStateException.class,
                    () -> svc.startSession("s", bonaFide(), "mu", LivenessService.WorkflowType.RESIDENT_REGISTRATION));
            LivenessSession s = svc.startBenchmarkSession(
                    "s", bonaFide(), "mu2", LivenessService.WorkflowType.RESIDENT_REGISTRATION);
            assertNotNull(s);
        } finally {
            if (prev != null) System.setProperty(StubPolicy.PROPERTY, prev);
        }
    }

    @Test
    void benchmarkSessionStillHonoursTheRateLimiter() {
        LivenessConfig cfg = LivenessConfig.builder().minQualifyingFrames(3).activeEnabled(false).maxRetries(3).build();
        RetryRateLimiter limiter = new RetryRateLimiter(1);
        MiniFasNetScorer pad = new MiniFasNetScorer(null);
        pad.setStubScore(85);
        LivenessService s = new LivenessService(cfg, pad, new SameFaceChecker(0.6),
                new ChallengeGenerator(ChallengeGenerator.DEFAULT_POOL, new ArrayList<>()),
                limiter, new EcdsaSignerStub());
        limiter.recordBlockedAttack("tx-locked", "mu-locked");
        assertTrue(limiter.isLocked("mu-locked"), "precondition: limiter must be locked");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.startBenchmarkSession(
                "s", bonaFide(), "mu-locked", LivenessService.WorkflowType.RESIDENT_REGISTRATION));
        assertTrue(e.getMessage().startsWith("LOCKED"));
    }

    @Test
    void benchmarkEmbedderIsNotProductionGradeAndAlwaysThrows() {
        assertFalse(LivenessService.BENCHMARK_ONLY_EMBEDDER.productionGrade());
        assertThrows(UnsupportedOperationException.class,
                () -> LivenessService.BENCHMARK_ONLY_EMBEDDER.embed(new byte[12], 2, 2));
    }

    @Test
    void benchmarkSessionCanNeverPassEvenWithAProductionEmbedderSet() throws Exception {
        LivenessService control = service(PRODUCTION_SAME);
        DeviceAdapter d1 = bonaFide();
        LivenessSession normal = control.startSession("s", d1, "mu-control", LivenessService.WorkflowType.RESIDENT_REGISTRATION);
        assertEquals(Verdict.PASS, drive(normal, d1), "driver sanity: the same setup must PASS through startSession");

        LivenessService bench = service(PRODUCTION_SAME);
        DeviceAdapter d2 = bonaFide();
        LivenessSession benchSession = bench.startBenchmarkSession("s", d2, "mu-bench", LivenessService.WorkflowType.RESIDENT_REGISTRATION);
        assertNotEquals(Verdict.PASS, drive(benchSession, d2));
    }
}
