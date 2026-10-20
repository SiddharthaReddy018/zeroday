package liveness.policy;

/**
 * Production stub guard (ADR-010). Stub PAD scorers and stubbed same-face checkers exist for unit tests and UI
 * demos. They must never run in a real deployment, because a stub that returns a fixed pass would approve anyone.
 * A stub is permitted only when the JVM property {@code liveness.allowStubs=true} is set (the Maven surefire
 * config sets it; Android release builds and production JVMs do not).
 */
public final class StubPolicy {
    public static final String PROPERTY = "liveness.allowStubs";

    private StubPolicy() {}

    public static boolean stubsAllowed() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** @throws IllegalStateException if {@code usingStub} and stubs are not explicitly allowed. */
    public static void check(boolean usingStub, String component) {
        if (usingStub && !stubsAllowed()) {
            throw new IllegalStateException(component + " is a stub; refusing to run without -D"
                    + PROPERTY + "=true (test/demo only)");
        }
    }
}
