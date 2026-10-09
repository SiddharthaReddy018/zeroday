package liveness.capture;

public class RCaptureBindingVerifier {
    public boolean verify(String returnedTransactionId, String sessionNonce, long rcaptureTimestamp, long requestTimestamp, long freshnessWindowMs, boolean sameFacePass, boolean finalPadPass) {
        if (returnedTransactionId == null || sessionNonce == null) return false;
        boolean nonceMatches = returnedTransactionId.endsWith(":" + sessionNonce);
        boolean freshnessPass = Math.abs(rcaptureTimestamp - requestTimestamp) <= freshnessWindowMs;
        return nonceMatches && freshnessPass && sameFacePass && finalPadPass;
    }
}
