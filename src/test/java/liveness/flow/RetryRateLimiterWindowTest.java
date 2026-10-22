package liveness.flow;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RetryRateLimiterWindowTest {

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone;

        MutableClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
            this.zone = ZoneId.of("UTC");
        }

        void advance(long durationMs) {
            millis.addAndGet(durationMs);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Test
    void lockAfterNFailuresAndWindowExpiryRestoresAccess() {
        MutableClock clock = new MutableClock(1_000_000L);
        long windowMs = 60_000L; // 1 minute window
        RetryRateLimiter limiter = new RetryRateLimiter(2, 3, windowMs, clock);

        String machineUserId = "terminal-1:agent-42";
        assertFalse(limiter.isLocked(machineUserId));

        // First failure
        limiter.recordDeviceError("tx-1", machineUserId, RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
        assertFalse(limiter.isLocked(machineUserId));
        assertEquals(1, limiter.retriesUsedForMachineUser(machineUserId));

        // Second failure reaches maxRetries (2) -> Locked
        limiter.recordDeviceError("tx-2", machineUserId, RetryRateLimiter.DeviceErrorReason.INTEGRITY_FAILURE);
        assertTrue(limiter.isLocked(machineUserId));
        assertFalse(limiter.tryRetry("tx-3", machineUserId));

        // Advance clock partially (30s) -> still locked
        clock.advance(30_000L);
        assertTrue(limiter.isLocked(machineUserId));

        // Advance clock past the sliding window cutoff (another 35s = 65s total)
        clock.advance(35_000L);
        assertFalse(limiter.isLocked(machineUserId), "Should be unlocked after sliding window expires");
        assertEquals(0, limiter.retriesUsedForMachineUser(machineUserId));

        // Can retry again
        assertTrue(limiter.tryRetry("tx-4", machineUserId));
    }
}
