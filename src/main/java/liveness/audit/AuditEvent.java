package liveness.audit;

import java.util.Objects;

/**
 * One entry in the audit chain (§6.6).
 * Contains a timestamp, channel, event code, and free-form fields.
 * No biometric data — scores and event codes only.
 */
public final class AuditEvent {

    private final long timeMs;
    private final String channel;
    private final String event;
    private final String fields;

    /**
     * @param timeMs  epoch milliseconds
     * @param channel audit channel (e.g. "PAD", "STATE", "CHALLENGE", "INTEGRITY")
     * @param event   event code (e.g. "TRANSITION", "SCORE", "BLOCKED")
     * @param fields  key=value pairs as a deterministic string; never contains biometric data
     */
    public AuditEvent(long timeMs, String channel, String event, String fields) {
        this.timeMs = timeMs;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.event = Objects.requireNonNull(event, "event");
        this.fields = Objects.requireNonNull(fields, "fields");
    }

    public long timeMs()   { return timeMs; }
    public String channel() { return channel; }
    public String event()   { return event; }
    public String fields()  { return fields; }

    /**
     * Produces a deterministic canonical string for hash-chain computation.
     * Format: {@code timeMs|channel|event|fields}
     */
    public String canonical() {
        return timeMs + "|" + channel + "|" + event + "|" + fields;
    }

    @Override
    public String toString() {
        return "AuditEvent{" + canonical() + "}";
    }
}
