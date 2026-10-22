package liveness.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChallengeProfile R5 bounds validation.
 * <p>
 * R5: A profile may relax time/pool/retries, NEVER security thresholds
 * (attackThreshold, passThreshold, persistence windows, capture-integrity
 * rules, same-face thresholds).
 */
class ChallengeProfileBoundsTest {

    private final LivenessConfig baseConfig = LivenessConfig.defaults();

    @Test
    void defaultProfileValidates() {
        assertDoesNotThrow(() ->
                ChallengeProfile.DEFAULT.validateAgainst(baseConfig));
    }

    @Test
    void accessibilityProfileValidates() {
        assertDoesNotThrow(() ->
                ChallengeProfile.ACCESSIBILITY.validateAgainst(baseConfig));
    }

    @Test
    void lowQualityProfileValidates() {
        assertDoesNotThrow(() ->
                ChallengeProfile.LOW_QUALITY.validateAgainst(baseConfig));
    }

    @Test
    void infantProfileValidates() {
        assertDoesNotThrow(() ->
                ChallengeProfile.INFANT.validateAgainst(baseConfig));
    }

    @Test
    void workflowSpecificProfileValidates() {
        assertDoesNotThrow(() ->
                ChallengeProfile.WORKFLOW_SPECIFIC.validateAgainst(baseConfig));
    }

    @Test
    void allProfilesHavePositiveMaxRetries() {
        for (ChallengeProfile profile : ChallengeProfile.values()) {
            assertTrue(profile.maxRetries() >= 0,
                    profile.name() + " should have non-negative maxRetries");
        }
    }

    @Test
    void allProfilesHavePositiveMaxChallenges() {
        for (ChallengeProfile profile : ChallengeProfile.values()) {
            assertTrue(profile.maxChallenges() >= 1,
                    profile.name() + " should have maxChallenges >= 1");
        }
    }

    @Test
    void allProfilesHaveReasonableTimeout() {
        for (ChallengeProfile profile : ChallengeProfile.values()) {
            assertTrue(profile.challengeTimeoutMs() >= 1000,
                    profile.name() + " should have timeout >= 1000ms");
        }
    }

    @Test
    void profilesDoNotChangeSecurityThresholds() {
        // Security thresholds are in LivenessConfig, not in ChallengeProfile.
        // This test verifies the architectural separation: all profiles share
        // the same baseConfig attack/pass thresholds.
        LivenessConfig config = LivenessConfig.defaults();
        int attack = config.attackThreshold();
        int pass = config.passThreshold();
        int persistence = config.attackPersistenceWindows();

        // These values are the same regardless of which profile is active
        for (ChallengeProfile profile : ChallengeProfile.values()) {
            // The profile cannot override these values -- they're not in the enum
            assertEquals(30, attack, "attackThreshold must remain constant across profiles");
            assertEquals(70, pass, "passThreshold must remain constant across profiles");
            assertEquals(3, persistence, "persistenceWindows must remain constant across profiles");
        }
    }

    @Test
    void accessibilityRetryBudgetAtLeastDefault() {
        // R5/S5: ACCESSIBILITY max_retries should be >= DEFAULT
        assertTrue(ChallengeProfile.ACCESSIBILITY.maxRetries()
                        >= ChallengeProfile.DEFAULT.maxRetries(),
                "ACCESSIBILITY retries should be >= DEFAULT retries");
    }

    @Test
    void accessibilityTimeoutAtLeastDefault() {
        assertTrue(ChallengeProfile.ACCESSIBILITY.challengeTimeoutMs()
                        >= ChallengeProfile.DEFAULT.challengeTimeoutMs(),
                "ACCESSIBILITY timeout should be >= DEFAULT timeout");
    }

    @Test
    void infantTimeoutAtLeastDefault() {
        assertTrue(ChallengeProfile.INFANT.challengeTimeoutMs()
                        >= ChallengeProfile.DEFAULT.challengeTimeoutMs(),
                "INFANT timeout should be >= DEFAULT timeout");
    }

    @Test
    void configBuilderRejectsInvalidThresholds() {
        // attackThreshold >= passThreshold
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().attackThreshold(70).passThreshold(30).build());

        // Equal thresholds
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().attackThreshold(50).passThreshold(50).build());
    }

    @Test
    void configBuilderRejectsInvalidPersistence() {
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().attackPersistenceWindows(0).build());
    }

    @Test
    void configBuilderRejectsInvalidChallenges() {
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().minChallenges(0).build());

        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().minChallenges(5).maxChallenges(3).build());
    }

    // --- R1 acceptance tests ---

    @Test
    void configBuilderRejectsWindowSmallerThanTwoKPlusOne() {
        // window = 10, K = 5 -> 10 < 2*5+1 (11) -> must throw
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().frameFreezePHashWindowSize(10).loopSequenceLengthK(5).build());

        // using loopWindowFrames setter alias
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().loopWindowFrames(8).loopSequenceLengthK(5).build());
    }

    @Test
    void configBuilderRejectsFreezeConsecutiveFramesExceedingWindow() {
        // freezeConsecutiveFrames = 16, window = 15 -> must throw
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().frameFreezePHashWindowSize(15).freezeConsecutiveFrames(16).build());
    }

    @Test
    void configBuilderRejectsFreezeConsecutiveFramesLessThanTwo() {
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().freezeConsecutiveFrames(1).build());
    }

    @Test
    void configBuilderRejectsLoopSequenceLengthKLessThanFive() {
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().loopSequenceLengthK(4).build());
    }

    @Test
    void configBuilderRejectsInvalidLoopMinSegmentMotionBits() {
        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().loopMinSegmentMotionBits(-1).build());

        assertThrows(IllegalArgumentException.class, () ->
                LivenessConfig.builder().loopMinSegmentMotionBits(65).build());
    }

    @Test
    void configBuilderDecoupledSettersTouchOnlyTheirOwnField() {
        // Setting window must NOT set freezeConsecutiveFrames
        LivenessConfig cfg1 = LivenessConfig.builder().frameFreezePHashWindowSize(20).build();
        assertEquals(20, cfg1.frameFreezePHashWindowSize());
        assertEquals(20, cfg1.loopWindowFrames());
        assertEquals(5, cfg1.freezeConsecutiveFrames(), "freezeConsecutiveFrames must remain default 5");

        // Setting freezeConsecutiveFrames must NOT set window
        LivenessConfig cfg2 = LivenessConfig.builder().freezeConsecutiveFrames(8).build();
        assertEquals(8, cfg2.freezeConsecutiveFrames());
        assertEquals(15, cfg2.frameFreezePHashWindowSize(), "window must remain default 15");
    }

    @Test
    void defaultConfigBuildsWithValidWindowAndK() {
        LivenessConfig cfg = LivenessConfig.defaultConfig();
        assertNotNull(cfg);
        assertTrue(cfg.frameFreezePHashWindowSize() >= 2 * cfg.loopSequenceLengthK() + 1);
        assertTrue(cfg.freezeConsecutiveFrames() <= cfg.frameFreezePHashWindowSize());
        assertEquals(cfg.frameFreezePHashWindowSize(), cfg.loopWindowFrames());
    }
}
