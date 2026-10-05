package liveness.flow;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sliding-window retry rate limiter (§6.8).
 * <p>
 * Tracks retry counts per {@code transactionId} and per {@code machineId+userId}.
 * <p>
 * In production this is persisted in the encrypted local DB.
 * This in-memory implementation is for M1 testing only.
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

    private final int maxRetries;
    private final int maxDeviceReconnects;

    // Per-transaction counters
    private final Map<String, Integer> retryCountByTransaction = new HashMap<>();
    // Per machine+user counters
    private final Map<String, Integer> retryCountByMachineUser = new HashMap<>();
    // Device reconnect attempts
    private final Map<String, Integer> reconnectCount = new HashMap<>();

    /**
     * @param maxRetries         maximum retry budget per transaction
     * @param maxDeviceReconnects maximum benign device reconnect attempts
     */
    public RetryRateLimiter(int maxRetries, int maxDeviceReconnects) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        if (maxDeviceReconnects < 0) throw new IllegalArgumentException("maxDeviceReconnects must be >= 0");
        this.maxRetries = maxRetries;
        this.maxDeviceReconnects = maxDeviceReconnects;
    }

    public RetryRateLimiter(int maxRetries) {
        this(maxRetries, 3);
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

        int txCount = retryCountByTransaction.getOrDefault(transactionId, 0);
        int muCount = retryCountByMachineUser.getOrDefault(machineUserId, 0);

        if (txCount >= maxRetries || muCount >= maxRetries) {
            return false;
        }

        retryCountByTransaction.put(transactionId, txCount + 1);
        retryCountByMachineUser.put(machineUserId, muCount + 1);
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

        if (reason == DeviceErrorReason.INTEGRITY_FAILURE) {
            // Counts toward the lock budget
            int txCount = retryCountByTransaction.getOrDefault(transactionId, 0);
            retryCountByTransaction.put(transactionId, txCount + 1);
            int muCount = retryCountByMachineUser.getOrDefault(machineUserId, 0);
            retryCountByMachineUser.put(machineUserId, muCount + 1);
            return txCount + 1 < maxRetries && muCount + 1 < maxRetries;
        } else {
            // DEVICE_UNAVAILABLE: free, but bounded by reconnect attempts
            String reconnectKey = transactionId + ":" + machineUserId;
            int count = reconnectCount.getOrDefault(reconnectKey, 0);
            reconnectCount.put(reconnectKey, count + 1);
            return count + 1 <= maxDeviceReconnects;
        }
    }

    /**
     * Records a BLOCKED_ATTACK session — always counts toward lock budget.
     * Supervisor exemption never applies.
     */
    public void recordBlockedAttack(String transactionId, String machineUserId) {
        int txCount = retryCountByTransaction.getOrDefault(transactionId, 0);
        retryCountByTransaction.put(transactionId, txCount + 1);
        int muCount = retryCountByMachineUser.getOrDefault(machineUserId, 0);
        retryCountByMachineUser.put(machineUserId, muCount + 1);
    }

    /** Current retry count for a transaction. */
    public int retriesUsed(String transactionId) {
        return retryCountByTransaction.getOrDefault(transactionId, 0);
    }

    /** Current retry count for a machine+user pair. */
    public int retriesUsedForMachineUser(String machineUserId) {
        return retryCountByMachineUser.getOrDefault(machineUserId, 0);
    }

    public int maxRetries() { return maxRetries; }
}
