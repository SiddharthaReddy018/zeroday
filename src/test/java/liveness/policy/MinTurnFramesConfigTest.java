package liveness.policy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MinTurnFramesConfigTest {
    @Test
    void defaultIsOne() {
        assertEquals(1, LivenessConfig.builder().build().minTurnFrames());
    }

    @Test
    void acceptsBounds() {
        assertEquals(2, LivenessConfig.builder().minTurnFrames(2).build().minTurnFrames());
        assertEquals(10, LivenessConfig.builder().minTurnFrames(10).build().minTurnFrames());
    }

    @Test
    void rejectsOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> LivenessConfig.builder().minTurnFrames(0).build());
        assertThrows(IllegalArgumentException.class, () -> LivenessConfig.builder().minTurnFrames(11).build());
    }
}
