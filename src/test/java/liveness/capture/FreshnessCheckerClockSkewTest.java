package liveness.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clock skew and freshness verification under mobile device timestamp drift (Part 2 X7).
 * Validates FreshnessChecker and CaptureIntegrityChecker under extreme NTP offsets,
 * forward/backward clock drift, and boundary conditions.
 */
public class FreshnessCheckerClockSkewTest {

    private static final long DEFAULT_WINDOW_MS = 30000L; // 30 seconds

    @Test
    @DisplayName("Timestamps with zero drift are fresh")
    void zeroDriftIsFresh() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long now = 1700000000000L;
        assertTrue(checker.checkFreshness(now, now));
    }

    @Test
    @DisplayName("Forward drift within window is accepted")
    void forwardDriftWithinWindowAccepted() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        long deviceTime = requestTime + 15000L; // 15 seconds fast
        assertTrue(checker.checkFreshness(deviceTime, requestTime));
    }

    @Test
    @DisplayName("Backward drift within window is accepted")
    void backwardDriftWithinWindowAccepted() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        long deviceTime = requestTime - 15000L; // 15 seconds slow
        assertTrue(checker.checkFreshness(deviceTime, requestTime));
    }

    @Test
    @DisplayName("Exact boundary drift (+/- window) is accepted")
    void exactWindowBoundaryAccepted() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        assertTrue(checker.checkFreshness(requestTime + DEFAULT_WINDOW_MS, requestTime));
        assertTrue(checker.checkFreshness(requestTime - DEFAULT_WINDOW_MS, requestTime));
    }

    @Test
    @DisplayName("Drift exceeding window by 1 ms is rejected")
    void driftExceedingWindowRejected() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        assertFalse(checker.checkFreshness(requestTime + DEFAULT_WINDOW_MS + 1L, requestTime));
        assertFalse(checker.checkFreshness(requestTime - DEFAULT_WINDOW_MS - 1L, requestTime));
    }

    @Test
    @DisplayName("Extreme forward skew (hours/days) is rejected")
    void extremeForwardSkewRejected() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        assertFalse(checker.checkFreshness(requestTime + 3600000L, requestTime)); // +1 hr
        assertFalse(checker.checkFreshness(requestTime + 86400000L, requestTime)); // +1 day
        assertFalse(checker.checkFreshness(requestTime + 31536000000L, requestTime)); // +1 yr
    }

    @Test
    @DisplayName("Extreme backward skew (replay attack/stale token) is rejected")
    void extremeBackwardSkewRejected() {
        FreshnessChecker checker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        long requestTime = 1700000000000L;
        assertFalse(checker.checkFreshness(requestTime - 3600000L, requestTime)); // -1 hr
        assertFalse(checker.checkFreshness(requestTime - 86400000L, requestTime)); // -1 day
        assertFalse(checker.checkFreshness(0L, requestTime)); // Epoch 1970
    }

    @Test
    @DisplayName("Custom configured freshness windows are respected")
    void customFreshnessWindowRespected() {
        FreshnessChecker tightChecker = new FreshnessChecker(5000L); // 5s tight window
        long now = 1700000000000L;
        assertTrue(tightChecker.checkFreshness(now + 4999L, now));
        assertFalse(tightChecker.checkFreshness(now + 5001L, now));

        FreshnessChecker relaxedChecker = new FreshnessChecker(60000L); // 60s relaxed window
        assertTrue(relaxedChecker.checkFreshness(now + 45000L, now));
        assertFalse(relaxedChecker.checkFreshness(now + 65000L, now));
    }

    @Test
    @DisplayName("CaptureIntegrityChecker rejects valid transaction ID when timestamp is skewed")
    void captureIntegrityRejectsSkewedTimestamp() {
        FrameSequenceMonitor monitor = new FrameSequenceMonitor(15, 33);
        DeviceIdentityValidator deviceValidator = new DeviceIdentityValidator();
        FreshnessChecker freshnessChecker = new FreshnessChecker(DEFAULT_WINDOW_MS);
        SessionTransactionBinder txBinder = new SessionTransactionBinder();

        CaptureIntegrityChecker integrityChecker = new CaptureIntegrityChecker(
                monitor, deviceValidator, freshnessChecker, txBinder);

        String baseTxId = "tx-456";
        String boundTxId = integrityChecker.boundTransactionId(baseTxId);
        long now = 1700000000000L;

        // Valid transaction ID and fresh timestamp -> passes
        assertTrue(integrityChecker.verifyRCapture(boundTxId, now + 1000L, now));

        // Valid transaction ID but expired/skewed timestamp (+31s) -> rejected
        assertFalse(integrityChecker.verifyRCapture(boundTxId, now + 31000L, now));

        // Valid transaction ID but stale timestamp (-31s) -> rejected
        assertFalse(integrityChecker.verifyRCapture(boundTxId, now - 31000L, now));

        // Tampered transaction ID with fresh timestamp -> rejected
        assertFalse(integrityChecker.verifyRCapture("corrupted-tx-id", now, now));
    }
}
