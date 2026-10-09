package liveness.capture;

import java.util.UUID;

public class SessionTransactionBinder {
    private final String sessionNonce;

    public SessionTransactionBinder() {
        this.sessionNonce = UUID.randomUUID().toString();
    }

    public String sessionNonce() {
        return sessionNonce;
    }

    public String bindToTransactionId(String baseTransactionId) {
        return baseTransactionId + ":" + sessionNonce;
    }

    public boolean verifyTransactionId(String returnedTransactionId) {
        if (returnedTransactionId == null) return false;
        return returnedTransactionId.endsWith(":" + sessionNonce);
    }
}
