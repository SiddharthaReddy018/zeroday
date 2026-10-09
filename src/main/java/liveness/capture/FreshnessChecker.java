package liveness.capture;

public class FreshnessChecker {
    private final long freshnessWindowMs;

    public FreshnessChecker(long freshnessWindowMs) {
        this.freshnessWindowMs = freshnessWindowMs;
    }

    public boolean checkFreshness(long rcaptureTimestamp, long requestTimestamp) {
        return Math.abs(rcaptureTimestamp - requestTimestamp) <= freshnessWindowMs;
    }
}
