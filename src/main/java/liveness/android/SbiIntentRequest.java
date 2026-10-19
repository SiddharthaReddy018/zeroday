package liveness.android;

import java.util.Objects;

/** Platform-neutral description of an Android Intent to an SBI app (built by SbiIntentBridge, launched by the glue). */
public final class SbiIntentRequest {
    private final String action;
    private final String targetPackage;   // nullable: implicit
    private final byte[] input;           // serialized JSON, sent as byte[] extra "input" (MDS Android spec)
    private final boolean grantReadUri;   // FLAG_GRANT_READ_URI_PERMISSION (capture / rCapture)

    public SbiIntentRequest(String action, String targetPackage, byte[] input, boolean grantReadUri) {
        this.action = Objects.requireNonNull(action, "action");
        this.targetPackage = targetPackage;
        this.input = input == null ? new byte[0] : input.clone();
        this.grantReadUri = grantReadUri;
    }

    public String action() { return action; }
    public String targetPackage() { return targetPackage; }
    public byte[] input() { return input.clone(); }
    public String inputString() { return new String(input, java.nio.charset.StandardCharsets.UTF_8); }
    public boolean grantReadUri() { return grantReadUri; }

    @Override
    public String toString() { return "SbiIntentRequest{" + action + ", pkg=" + targetPackage + "}"; }
}
