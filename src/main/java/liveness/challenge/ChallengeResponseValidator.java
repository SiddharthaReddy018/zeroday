package liveness.challenge;

import java.util.List;
import java.util.Objects;

/**
 * Validates challenge responses against their bound constraints (§6.3).
 * <p>
 * Rejects replayed or expired challenges; checks sequence order for
 * compound challenges; logs latency plausibility (SUSPICIOUS_LATENCY)
 * but never hard-blocks on latency alone.
 */
public final class ChallengeResponseValidator {

    /**
     * Result of a challenge response validation.
     */
    public static final class ValidationResult {
        private final boolean accepted;
        private final String reason;
        private final boolean suspiciousLatency;

        private ValidationResult(boolean accepted, String reason, boolean suspiciousLatency) {
            this.accepted = accepted;
            this.reason = reason;
            this.suspiciousLatency = suspiciousLatency;
        }

        public boolean isAccepted() { return accepted; }
        public String reason() { return reason; }
        public boolean isSuspiciousLatency() { return suspiciousLatency; }

        static ValidationResult accept() {
            return new ValidationResult(true, "ACCEPTED", false);
        }

        static ValidationResult acceptWithSuspiciousLatency() {
            return new ValidationResult(true, "ACCEPTED_SUSPICIOUS_LATENCY", true);
        }

        static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason, false);
        }
    }

    /**
     * Minimum plausible response latency in ms.
     * Below this is suspicious (too fast for human reaction).
     * Engineering assumption — not a verified empirical range.
     */
    private static final long MIN_PLAUSIBLE_LATENCY_MS = 150;

    /**
     * Maximum plausible response latency in ms.
     * Above this is suspicious (too slow or delayed replay).
     */
    private static final long MAX_PLAUSIBLE_LATENCY_MS = 800;

    /**
     * Validates a single action response against a bound challenge.
     *
     * @param challenge    the bound challenge being responded to
     * @param responseAction the action the subject performed
     * @param responseTimeMs when the response was detected (epoch ms)
     * @return validation result
     */
    public ValidationResult validate(BoundChallenge challenge,
                                     String responseAction,
                                     long responseTimeMs) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(responseAction, "responseAction");

        // Check expiry
        if (challenge.isExpired(responseTimeMs)) {
            return ValidationResult.reject("EXPIRED");
        }

        // Check action matches expected
        List<String> expected = challenge.expectedSequence();
        int seqIdx = challenge.sequenceIndex();
        if (seqIdx < 0 || seqIdx >= expected.size()) {
            return ValidationResult.reject("INVALID_SEQUENCE_INDEX");
        }

        String expectedAction = expected.get(seqIdx);
        if (!expectedAction.equals(responseAction)) {
            return ValidationResult.reject("WRONG_ACTION");
        }

        // Check latency plausibility (log only, never hard-block)
        long latencyMs = responseTimeMs - challenge.issuedAtMs();
        boolean suspicious = latencyMs < MIN_PLAUSIBLE_LATENCY_MS
                || latencyMs > MAX_PLAUSIBLE_LATENCY_MS;

        if (suspicious) {
            return ValidationResult.acceptWithSuspiciousLatency();
        }

        return ValidationResult.accept();
    }

    /**
     * Validates a compound challenge sequence in order.
     *
     * @param challenge       the bound challenge
     * @param responseActions the actions performed in order
     * @param responseTimeMs  when the final response was detected
     * @return validation result
     */
    public ValidationResult validateCompound(BoundChallenge challenge,
                                             List<String> responseActions,
                                             long responseTimeMs) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(responseActions, "responseActions");

        if (challenge.isExpired(responseTimeMs)) {
            return ValidationResult.reject("EXPIRED");
        }

        List<String> expected = challenge.expectedSequence();
        if (responseActions.size() != expected.size()) {
            return ValidationResult.reject("INCOMPLETE_SEQUENCE");
        }

        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(responseActions.get(i))) {
                return ValidationResult.reject("WRONG_ACTION_AT_STEP_" + i);
            }
        }

        long latencyMs = responseTimeMs - challenge.issuedAtMs();
        boolean suspicious = latencyMs < MIN_PLAUSIBLE_LATENCY_MS
                || latencyMs > MAX_PLAUSIBLE_LATENCY_MS * expected.size();

        if (suspicious) {
            return ValidationResult.acceptWithSuspiciousLatency();
        }

        return ValidationResult.accept();
    }
}
