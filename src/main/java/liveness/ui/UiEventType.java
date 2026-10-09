package liveness.ui;

/**
 * UI event types sent from the engine to the UI layer.
 * <p>
 * The UI never calls the engine directly — it receives these events
 * through {@link LivenessUiListener} (§9, S22).
 */
public enum UiEventType {

    // --- State transitions ---
    STATE_CHANGED,

    // --- Preview ---
    PREVIEW_FRAME,

    // --- Quality guidance ---
    GUIDE_FACE_TOO_CLOSE,
    GUIDE_FACE_TOO_FAR,
    GUIDE_FACE_NOT_CENTERED,
    GUIDE_POSE_YAW_EXCEEDED,
    GUIDE_POSE_PITCH_EXCEEDED,
    GUIDE_BLUR_DETECTED,
    GUIDE_BRIGHTNESS_LOW,
    GUIDE_BRIGHTNESS_HIGH,
    GUIDE_NO_FACE_DETECTED,
    GUIDE_MULTIPLE_FACES,
    GUIDE_OPERATOR_ADJUST_CAMERA,

    // --- Challenge ---
    CHALLENGE_PROMPT,
    CHALLENGE_COUNTDOWN,
    CHALLENGE_RESULT,

    // --- Progress ---
    PROGRESS_UPDATE,

    // --- Terminal ---
    RESULT_SUCCESS,
    RESULT_BLOCKED,
    RESULT_FAILED,
    RESULT_DEVICE_ERROR,

    // --- Error ---
    ERROR
}
