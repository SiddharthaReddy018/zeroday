package liveness.android;

import java.util.List;

/**
 * Android glue seam: starts the SBI activity and blocks for its result.
 * The glue resolves the "response" extra to bytes: a byte[] extra is used as is; a String extra that is a
 * content:// URI is read once through the ContentResolver (the spec requires the provider to invalidate it
 * after the read); any other String is treated as inline JSON.
 */
public interface SbiIntentLauncher {

    /** Android RESULT_OK is -1. */
    final class ActivityResult {
        public static final int RESULT_OK = -1;
        public final int resultCode;
        public final byte[] response; // may be null/empty if the SBI returned nothing
        public ActivityResult(int resultCode, byte[] response) {
            this.resultCode = resultCode;
            this.response = response;
        }
    }

    /** Thrown when the SBI app does not answer within the allotted time. */
    final class TimeoutException extends Exception {
        public TimeoutException(String m) { super(m); }
    }

    /** Package names of installed apps that handle action "io.sbi.device". */
    List<String> resolveSbiPackages();

    /** Launch and wait. Must not be called from the main thread. */
    ActivityResult launch(SbiIntentRequest request, long timeoutMs) throws TimeoutException;
}
