package liveness.flow;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * Sliding-window retry rate limiter (§6.8).
 * <p>
 * Tracks retry counts per {@code transactionId} and per {@code machineId+userId} within a sliding time window.
 * Thread-safe via ConcurrentHashMap and lock-free timestamp queues with an injectable clock.
 * <p>
 * DEVICE_ERROR with reason DEVICE_UNAVAILABLE does not count against budget.
 * DEVICE_ERROR with reason INTEGRITY_FAILURE counts toward the lock budget.
 * Supervisor exemption never applies to BLOCKED_ATTACK sessions.
 */
public final class RetryRateLimiter {

    /** Reason codes for DEVICE_ERROR recovery (16.2). */
    public enum DeviceErrorReason {
        /** Benign: disconnect, camera unavailable, RCAPTURE timeout. Free — no budget charge. */
        DEVICE_UNAVAILABLE,
        /** Security: frozen/looped frames, device identity change, bad JWT. Counts toward lock. */
        INTEGRITY_FAILURE
    }

    public static final long DEFAULT_WINDOW_MS = 15 * 60 * 1000L; // 15 minutes sliding window

    private final int maxRetries;
    private final int maxDeviceReconnects;
    private final long windowDurationMs;
    private final Clock clock;

    // Per-transaction timestamp records
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Long>> retryTimestampsByTx = new ConcurrentHashMap<>();
    // Per machine+user timestamp records
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Long>> retryTimestampsByMachineUser = new ConcurrentHashMap<>();
    // Benign reconnect attempts
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Long>> reconnectTimestamps = new ConcurrentHashMap<>();

    public RetryRateLimiter(int maxRetries, int maxDeviceReconnects, long windowDurationMs, Clock clock) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        if (maxDeviceReconnects < 0) throw new IllegalArgumentException("maxDeviceReconnects must be >= 0");
        if (windowDurationMs <= 0) throw new IllegalArgumentException("windowDurationMs must be > 0");
        this.maxRetries = maxRetries;
        this.maxDeviceReconnects = maxDeviceReconnects;
        this.windowDurationMs = windowDurationMs;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RetryRateLimiter(int maxRetries, int maxDeviceReconnects) {
        this(maxRetries, maxDeviceReconnects, DEFAULT_WINDOW_MS, Clock.systemUTC());
    }

    public RetryRateLimiter(int maxRetries) {
        this(maxRetries, 3, DEFAULT_WINDOW_MS, Clock.systemUTC());
    }

    private void evictExpired(ConcurrentLinkedDeque<Long> deque, long now) {
        long cutoff = now - windowDurationMs;
        while (!deque.isEmpty()) {
            Long first = deque.peekFirst();
            if (first != null && first < cutoff) {
                deque.pollFirst();
            } else {
                break;
            }
        }
    }

    private int activeCount(ConcurrentMap<String, ConcurrentLinkedDeque<Long>> map, String key, long now) {
        ConcurrentLinkedDeque<Long> deque = map.get(key);
        if (deque == null) return 0;
        evictExpired(deque, now);
        return deque.size();
    }

    private void recordTimestamp(ConcurrentMap<String, ConcurrentLinkedDeque<Long>> map, String key, long now) {
        map.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).addLast(now);
    }

    /**
     * Checks whether the machine+user is currently locked due to budget exhaustion.
     */
    public boolean isLocked(String machineUserId) {
        if (machineUserId == null) return false;
        long now = clock.millis();
        return activeCount(retryTimestampsByMachineUser, machineUserId, now) >= maxRetries;
    }

    /**
     * Checks whether a retry is allowed and increments the counter.
     *
     * @param transactionId the current transaction ID
     * @param machineUserId the "machineId+userId" composite key
     * @return true if the retry is granted; false if budget is exhausted
     */
    public boolean tryRetry(String transactionId, String machineUserId) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(machineUserId);

        long now = clock.millis();
        int txCount = activeCount(retryTimestampsByTx, transactionId, now);
        int muCount = activeCount(retryTimestampsByMachineUser, machineUserId, now);

        if (txCount >= maxRetries || muCount >= maxRetries) {
            return false;
        }

        recordTimestamp(retryTimestampsByTx, transactionId, now);
        recordTimestamp(retryTimestampsByMachineUser, machineUserId, now);
        return true;
    }

    /**
     * Records a DEVICE_ERROR session ending.
     * Benign reasons are free; integrity failures count toward the lock budget.
     *
     * @return true if recovery (new session) is allowed
     */
    public boolean recordDeviceError(String transactionId, String machineUserId,
                                     DeviceErrorReason reason) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(machineUserId);
        long now = clock.millis();

        if (reason == DeviceErrorReason.INTEGRITY_FAILURE) {
            recordTimestamp(retryTimestampsByTx, transactionId, now);
            recordTimestamp(retryTimestampsByMachineUser, machineUserId, now);
            return activeCount(retryTimestampsByTx, transactionId, now) < maxRetries
                    && activeCount(retryTimestampsByMachineUser, machineUserId, now) < maxRetries;
        } else {
            String reconnectKey = transactionId + ":" + machineUserId;
            recordTimestamp(reconnectTimestamps, reconnectKey, now);
            return activeCount(reconnectTimestamps, reconnectKey, now) <= maxDeviceReconnects;
        }
    }

    /**
     * Records a BLOCKED_ATTACK session — always counts toward lock budget.
     * Supervisor exemption never applies.
     */
    public void recordBlockedAttack(String transactionId, String machineUserId) {
        if (transactionId == null || machineUserId == null) return;
        long now = clock.millis();
        recordTimestamp(retryTimestampsByTx, transactionId, now);
        recordTimestamp(retryTimestampsByMachineUser, machineUserId, now);
    }

    /** Current retry count for a transaction within window. */
    public int retriesUsed(String transactionId) {
        return activeCount(retryTimestampsByTx, transactionId, clock.millis());
    }

    /** Current retry count for a machine+user pair within window. */
    public int retriesUsedForMachineUser(String machineUserId) {
        return activeCount(retryTimestampsByMachineUser, machineUserId, clock.millis());
    }

    public int maxRetries() { return maxRetries; }
    public long windowDurationMs() { return windowDurationMs; }
    public Clock clock() { return clock; }
}
