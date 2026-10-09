package liveness.ui;

/**
 * Listener for UI events from the liveness engine (§9, S22).
 * <p>
 * The UI implements this interface. The engine pushes events;
 * the UI never calls the engine directly.
 * <p>
 * Implementations must be thread-safe — events arrive from the
 * engine's background thread. JavaFX implementations must use
 * {@code Platform.runLater()} to update the scene graph.
 */
public interface LivenessUiListener {

    /**
     * Called when a liveness event occurs.
     *
     * @param event the UI event (never null)
     */
    void onEvent(UiEvent event);
}
