package liveness.evaluation;

import liveness.capture.FrameFingerprint;
import liveness.capture.FrameSequenceMonitor;
import liveness.capture.RCaptureBindingVerifier;

import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

/**
 * Executes synthetic mechanism-level presentations through actual verification components (Section 12.2, Section 24.2).
 * <p>
 * Feeds simulated frames through {@link FrameSequenceMonitor} and {@link RCaptureBindingVerifier}
 * to measure real detection rates, bypass rates, and detection latency instead of hardcoded numbers.
 */
public final class IadMechanismRunner {

    private final Random rng;

    public IadMechanismRunner() {
        this(new Random(42));
    }

    public IadMechanismRunner(Random rng) {
        this.rng = rng != null ? rng : new Random(42);
    }

    /**
     * Runs full synthetic evaluation across all IAD mechanisms.
     *
     * @param freezeTrials number of freeze presentation attempts (>= 30 for Rule of 30)
     * @param loopTrials   number of loop replay presentation attempts (>= 30)
     * @param nonceTrials  number of nonce-swap presentation attempts (>= 30)
     * @param legitTrials  number of bona fide presentations (>= 30)
     * @return populated IadMetrics
     */
    public IadMetrics run(int freezeTrials, int loopTrials, int nonceTrials, int legitTrials) {
        IadMetrics metrics = new IadMetrics();
        run(metrics, freezeTrials, loopTrials, nonceTrials, legitTrials);
        return metrics;
    }

    public void run(IadMetrics metrics, int freezeTrials, int loopTrials, int nonceTrials, int legitTrials) {
        runFreezeTrials(metrics, freezeTrials);
        runLoopTrials(metrics, loopTrials);
        runNonceSwapTrials(metrics, nonceTrials);
        runLegitimateTrials(metrics, legitTrials);
    }

    private void runFreezeTrials(IadMetrics metrics, int trials) {
        for (int t = 0; t < trials; t++) {
            // Freeze threshold is 5 frames
            FrameSequenceMonitor monitor = new FrameSequenceMonitor(15, 200, 5, FrameSequenceMonitor.LOOP_HAMMING_THRESHOLD, 5);
            byte[] identicalDigest = new byte[32];
            Arrays.fill(identicalDigest, (byte) (t + 1));
            long pHash = 0x12345678L + t;
            boolean detected = false;
            long startNs = System.nanoTime();

            for (int f = 0; f < 8; f++) {
                FrameFingerprint fp = new FrameFingerprint(pHash, 1000L + f * 100L, f, 640, identicalDigest);
                Optional<FrameSequenceMonitor.IntegrityViolation> violation = monitor.addFrame(fp);
                if (violation.isPresent() && violation.get() == FrameSequenceMonitor.IntegrityViolation.FROZEN) {
                    detected = true;
                    metrics.recordDetectionLatencyNs(System.nanoTime() - startNs);
                    break;
                }
            }
            metrics.recordFreezeAttempt(detected);
        }
    }

    private void runLoopTrials(IadMetrics metrics, int trials) {
        for (int t = 0; t < trials; t++) {
            // K = 5, loop window size = 15
            FrameSequenceMonitor monitor = new FrameSequenceMonitor(15, 200, 5, FrameSequenceMonitor.LOOP_HAMMING_THRESHOLD, 5);
            int K = 5;
            long[] loopPhashes = new long[K];
            byte[][] loopDigests = new byte[K][32];
            for (int k = 0; k < K; k++) {
                loopPhashes[k] = (0x100L * (k + 1)) ^ t;
                Arrays.fill(loopDigests[k], (byte) (k + 10));
            }

            boolean detected = false;
            long startNs = System.nanoTime();
            int seq = 0;
            long timeMs = 1000L;

            // 1. First segment (0..4)
            for (int k = 0; k < K; k++) {
                FrameFingerprint fp = new FrameFingerprint(loopPhashes[k], timeMs, seq++, 640, loopDigests[k]);
                timeMs += 100L;
                monitor.addFrame(fp);
            }

            // 2. Intervening gap frame to satisfy non-adjacent gap >= 1 requirement
            byte[] gapDigest = new byte[32];
            Arrays.fill(gapDigest, (byte) 0xEE);
            FrameFingerprint gapFrame = new FrameFingerprint(0xFFFFFFFFL, timeMs, seq++, 640, gapDigest);
            timeMs += 100L;
            monitor.addFrame(gapFrame);

            // 3. Replay segment (repeating 0..4)
            for (int k = 0; k < K; k++) {
                FrameFingerprint fp = new FrameFingerprint(loopPhashes[k], timeMs, seq++, 640, loopDigests[k]);
                timeMs += 100L;
                Optional<FrameSequenceMonitor.IntegrityViolation> violation = monitor.addFrame(fp);
                if (violation.isPresent() && violation.get() == FrameSequenceMonitor.IntegrityViolation.LOOP_REPLAY) {
                    detected = true;
                    metrics.recordDetectionLatencyNs(System.nanoTime() - startNs);
                    break;
                }
            }
            metrics.recordLoopAttempt(detected);
        }
    }

    private void runNonceSwapTrials(IadMetrics metrics, int trials) {
        RCaptureBindingVerifier verifier = new RCaptureBindingVerifier();
        for (int t = 0; t < trials; t++) {
            String sessionNonce = "nonce-" + t + "-" + rng.nextInt(10000);
            // Attacker swaps nonce or sends mismatched transaction ID
            String returnedTx = "tx:swapped-" + rng.nextInt(10000);
            long reqTime = 100000L;
            long rcaptureTime = reqTime + 50L;
            long freshnessWindowMs = 2000L;

            long startNs = System.nanoTime();
            boolean accepted = verifier.verify(returnedTx, sessionNonce, rcaptureTime, reqTime, freshnessWindowMs, true, true);
            // Detected = rejected
            boolean detected = !accepted;
            if (detected) {
                metrics.recordDetectionLatencyNs(System.nanoTime() - startNs);
            }
            metrics.recordNonceSwapAttempt(detected);
        }
    }

    private void runLegitimateTrials(IadMetrics metrics, int trials) {
        RCaptureBindingVerifier verifier = new RCaptureBindingVerifier();
        for (int t = 0; t < trials; t++) {
            FrameSequenceMonitor monitor = new FrameSequenceMonitor(15, 200, 5, FrameSequenceMonitor.LOOP_HAMMING_THRESHOLD, 5);
            boolean falseAlarm = false;

            // Feed natural live frames with varying content and pHash
            for (int f = 0; f < 10; f++) {
                byte[] digest = new byte[32];
                rng.nextBytes(digest);
                long pHash = rng.nextLong();
                FrameFingerprint fp = new FrameFingerprint(pHash, 1000L + f * 100L, f, 640, digest);
                Optional<FrameSequenceMonitor.IntegrityViolation> violation = monitor.addFrame(fp);
                if (violation.isPresent()) {
                    falseAlarm = true;
                    break;
                }
            }

            // Legitimate transaction binding
            String nonce = "valid-nonce-" + t;
            String returnedTx = "transaction-prefix:" + nonce;
            long reqTime = 500000L;
            long rcaptureTime = reqTime + 100L;
            boolean verified = verifier.verify(returnedTx, nonce, rcaptureTime, reqTime, 2000L, true, true);
            if (!verified) {
                falseAlarm = true;
            }

            metrics.recordLegitimateSession(falseAlarm);
        }
    }
}
