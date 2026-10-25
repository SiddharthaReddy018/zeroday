package liveness.engine;

import liveness.policy.LivenessConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Challenge names are the person's own sides; front-camera ML Kit yaw is positive for own left. */
class ChallengeActionDirectionTest {

    private final LivenessConfig c = LivenessConfig.builder().build();

    @Test
    void ownLeftIsPositiveYaw() {
        assertTrue(ChallengeActionDetector.detect("TURN_LEFT", null, 25, 0, c));
        assertTrue(ChallengeActionDetector.detect("LOOK_LEFT", null, 12, 0, c));
        assertFalse(ChallengeActionDetector.detect("TURN_LEFT", null, -25, 0, c));
        assertFalse(ChallengeActionDetector.detect("TURN_RIGHT", null, 25, 0, c));
    }

    @Test
    void ownRightIsNegativeYaw() {
        assertTrue(ChallengeActionDetector.detect("TURN_RIGHT", null, -25, 0, c));
        assertTrue(ChallengeActionDetector.detect("LOOK_RIGHT", null, -12, 0, c));
        assertFalse(ChallengeActionDetector.detect("TURN_RIGHT", null, 25, 0, c));
        assertFalse(ChallengeActionDetector.detect("LOOK_LEFT", null, -12, 0, c));
    }
}
