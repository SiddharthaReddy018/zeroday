package liveness.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DEVICE_ERROR recovery (16.2 DECIDED).
 * <p>
 * Benign reasons (DEVICE_UNAVAILABLE) are free — no budget charged.
 * Integrity failures count toward the lock budget.
 * Supervisor exemption never applies to BLOCKED_ATTACK sessions.
 */
class DeviceErrorRecoveryTest {

    @Test
    void benignDisconnectDoesNotChargeBudget() {
        RetryRateLimiter limiter = new RetryRateLimiter(3, 5);

        // Record a benign device error
        boolean allowed = limiter.recordDeviceError("tx-1", "machine-user-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE);

        assertTrue(allowed, "Benign disconnect should allow recovery");
        assertEquals(0, limiter.retriesUsed("tx-1"),
                "Benign disconnect should NOT charge retry budget");
    }

    @Test
    void integrityFailureChargesBudget() {
        RetryRateLimiter limiter = new RetryRateLimiter(3, 5);

        boolean allowed = limiter.recordDeviceError("tx-1", "machine-user-1",
                RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);

        assertTrue(allowed, "First integrity failure should still allow recovery");
        assertEquals(1, limiter.retriesUsed("tx-1"),
                "Integrity failure should charge retry budget");
    }

    @Test
    void multipleIntegrityFailuresExhaustBudget() {
        RetryRateLimiter limiter = new RetryRateLimiter(3, 5);

        limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
        limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
        boolean third = limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);

        assertFalse(third,
                "3rd integrity failure should exhaust the budget (max=3)");
    }

    @Test
    void benignReconnectsBounded() {
        RetryRateLimiter limiter = new RetryRateLimiter(3, 2);

        assertTrue(limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE));
        assertTrue(limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE));
        assertFalse(limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE),
                "Should be denied after max reconnect attempts");
    }

    @Test
    void blockedAttackCountsTowardLock() {
        RetryRateLimiter limiter = new RetryRateLimiter(3);

        limiter.recordBlockedAttack("tx-1", "mu-1");
        assertEquals(1, limiter.retriesUsed("tx-1"));
        assertEquals(1, limiter.retriesUsedForMachineUser("mu-1"));
    }

    @Test
    void blockedAttackExhaustsBudget() {
        RetryRateLimiter limiter = new RetryRateLimiter(2);

        limiter.recordBlockedAttack("tx-1", "mu-1");
        limiter.recordBlockedAttack("tx-1", "mu-1");

        // Budget is exhausted
        assertFalse(limiter.tryRetry("tx-1", "mu-1"),
                "Budget should be exhausted after 2 blocked attacks (max=2)");
    }

    @Test
    void retryGrantedAndExhausted() {
        RetryRateLimiter limiter = new RetryRateLimiter(2);

        assertTrue(limiter.tryRetry("tx-1", "mu-1"), "First retry should be granted");
        assertTrue(limiter.tryRetry("tx-1", "mu-1"), "Second retry should be granted");
        assertFalse(limiter.tryRetry("tx-1", "mu-1"), "Third retry should be denied");
    }

    @Test
    void separateTransactionsHaveSeparateBudgets() {
        RetryRateLimiter limiter = new RetryRateLimiter(2);

        assertTrue(limiter.tryRetry("tx-1", "mu-1"));
        assertTrue(limiter.tryRetry("tx-1", "mu-1"));
        assertFalse(limiter.tryRetry("tx-1", "mu-1"));

        // Different transaction, same user — should have its own tx budget
        // but shares machine+user budget
        assertFalse(limiter.tryRetry("tx-2", "mu-1"),
                "Machine-user budget is shared across transactions");
    }

    @Test
    void mixedBenignAndIntegrityFailures() {
        RetryRateLimiter limiter = new RetryRateLimiter(3, 5);

        // 2 benign disconnects — free
        limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE);
        limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.DEVICE_UNAVAILABLE);
        assertEquals(0, limiter.retriesUsed("tx-1"),
                "Benign errors should not charge budget");

        // 1 integrity failure — charges
        limiter.recordDeviceError("tx-1", "mu-1",
                RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
        assertEquals(1, limiter.retriesUsed("tx-1"),
                "Integrity failure should charge budget");

        // Regular retry still works
        assertTrue(limiter.tryRetry("tx-1", "mu-1"),
                "Regular retry should still be allowed with remaining budget");
    }

    @Test
    void deviceErrorStateMachineTransitions() {
        // Verify the state machine transitions for device error scenarios
        LivenessStateMachine sm = new LivenessStateMachine();

        // Row 3: DEVICE_CHECK -> DEVICE_ERROR (device unavailable)
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_UNAVAILABLE);
        assertEquals(LivenessState.DEVICE_ERROR, sm.current());
        assertTrue(sm.current().isTerminal());
    }

    @Test
    void midStreamDisconnectTransitions() {
        // Row 24: live non-terminal -> DEVICE_ERROR (disconnect)
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);

        // PASSIVE is a live non-terminal state
        sm.fire(LivenessEvent.DEVICE_DISCONNECT);
        assertEquals(LivenessState.DEVICE_ERROR, sm.current());
    }

    @Test
    void iadFailureTransitions() {
        // Row 25: live non-terminal -> DEVICE_ERROR (IAD failure)
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);

        sm.fire(LivenessEvent.IAD_FAILURE);
        assertEquals(LivenessState.DEVICE_ERROR, sm.current());
    }

    @Test
    void bindingFailureTransition() {
        // Row 21: VERIFYING_FINAL -> DEVICE_ERROR (integrity failure)
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);
        sm.fire(LivenessEvent.CLEAR_BONA_FIDE);
        sm.fire(LivenessEvent.RCAPTURE_RETURNED);

        sm.fire(LivenessEvent.BINDING_FAILURE);
        assertEquals(LivenessState.DEVICE_ERROR, sm.current());
    }
}
