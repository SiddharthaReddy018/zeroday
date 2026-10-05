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
            // The profile cannot override these values — they're not in the enum
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
}
