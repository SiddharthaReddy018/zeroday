package liveness.ui;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event sent from the liveness engine to the UI layer (§9, S22).
 * <p>
 * The UI never calls the engine directly. All communication flows
 * through events matching the {@code ui-contract/events-schema.json}.
 */
public final class UiEvent {

    private final UiEventType type;
    private final String state;          // current LivenessState name
    private final String message;        // localised user-facing message
    private final String action;         // challenge action (e.g. "BLINK", "SMILE")
    private final long timeoutMs;        // challenge timeout
    private final int progress;          // 0-100 progress percentage
    private final byte[] previewFrame;   // JPEG preview bytes (nullable)
    private final Map<String, String> metadata;  // extra key-value pairs

    private UiEvent(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.state = builder.state;
        this.message = builder.message;
        this.action = builder.action;
        this.timeoutMs = builder.timeoutMs;
        this.progress = builder.progress;
        this.previewFrame = builder.previewFrame;
        this.metadata = builder.metadata != null
                ? Collections.unmodifiableMap(builder.metadata)
                : Collections.emptyMap();
    }

    public UiEventType type()       { return type; }
    public String state()           { return state; }
    public String message()         { return message; }
    public String action()          { return action; }
    public long timeoutMs()         { return timeoutMs; }
    public int progress()           { return progress; }
    public byte[] previewFrame()    { return previewFrame; }
    public Map<String, String> metadata() { return metadata; }

    // --- Factory methods for common events ---

    public static UiEvent stateChanged(String state, String message) {
        return new Builder(UiEventType.STATE_CHANGED)
                .state(state).message(message).build();
    }

    public static UiEvent challengePrompt(String action, long timeoutMs, String message) {
        return new Builder(UiEventType.CHALLENGE_PROMPT)
                .action(action).timeoutMs(timeoutMs).message(message).build();
    }

    public static UiEvent progress(String state, int percent, String message) {
        return new Builder(UiEventType.PROGRESS_UPDATE)
                .state(state).progress(percent).message(message).build();
    }

    public static UiEvent previewFrame(byte[] jpegBytes) {
        return new Builder(UiEventType.PREVIEW_FRAME)
                .previewFrame(jpegBytes).build();
    }

    public static UiEvent guidance(UiEventType guideType, String message) {
        return new Builder(guideType).message(message).build();
    }

    public static UiEvent success(String message) {
        return new Builder(UiEventType.RESULT_SUCCESS).message(message).build();
    }

    public static UiEvent blocked(String message) {
        return new Builder(UiEventType.RESULT_BLOCKED).message(message).build();
    }

    public static UiEvent failed(String message) {
        return new Builder(UiEventType.RESULT_FAILED).message(message).build();
    }

    public static UiEvent deviceError(String message) {
        return new Builder(UiEventType.RESULT_DEVICE_ERROR).message(message).build();
    }

    public static UiEvent error(String errorCode, String message) {
        return new Builder(UiEventType.ERROR)
                .state(errorCode).message(message).build();
    }

    @Override
    public String toString() {
        return "UiEvent{type=" + type + ", state=" + state
                + ", message=" + message + "}";
    }

    // --- Builder ---

    public static final class Builder {
        private final UiEventType type;
        private String state;
        private String message;
        private String action;
        private long timeoutMs;
        private int progress;
        private byte[] previewFrame;
        private Map<String, String> metadata;

        public Builder(UiEventType type) {
            this.type = type;
        }

        public Builder state(String state)              { this.state = state; return this; }
        public Builder message(String message)          { this.message = message; return this; }
        public Builder action(String action)            { this.action = action; return this; }
        public Builder timeoutMs(long timeoutMs)        { this.timeoutMs = timeoutMs; return this; }
        public Builder progress(int progress)           { this.progress = progress; return this; }
        public Builder previewFrame(byte[] frame)       { this.previewFrame = frame; return this; }
        public Builder metadata(Map<String, String> md) { this.metadata = md; return this; }

        public UiEvent build() { return new UiEvent(this); }
    }
}
