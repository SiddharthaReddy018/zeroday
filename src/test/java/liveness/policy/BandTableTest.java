package liveness.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 4-band score routing (§5.1).
 * <p>
 * Default thresholds: attackThreshold=30, passThreshold=70.
 * <ul>
 *   <li>score &lt; 30 → CLEAR_ATTACK</li>
 *   <li>30 &le; score &lt; 70 → UNCERTAIN</li>
 *   <li>score &ge; 70 → CLEAR_BONA_FIDE</li>
 * </ul>
 */
class BandTableTest {

    private final LivenessPolicy policy = new LivenessPolicy(30, 70);

    // ─── CLEAR_ATTACK band ───

    @Test
    void scoreBelowAttackThreshold_isClearAttack() {
        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, policy.route(0));
        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, policy.route(10));
        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, policy.route(29));
    }

    // ─── UNCERTAIN band ───

    @Test
    void scoreAtAttackThreshold_isUncertain() {
        assertEquals(LivenessPolicy.Band.UNCERTAIN, policy.route(30));
    }

    @Test
    void scoreBetweenThresholds_isUncertain() {
        assertEquals(LivenessPolicy.Band.UNCERTAIN, policy.route(50));
        assertEquals(LivenessPolicy.Band.UNCERTAIN, policy.route(69));
    }

    // ─── CLEAR_BONA_FIDE band ───

    @Test
    void scoreAtPassThreshold_isClearBonaFide() {
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, policy.route(70));
    }

    @Test
    void scoreAbovePassThreshold_isClearBonaFide() {
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, policy.route(85));
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, policy.route(100));
    }

    // ─── Boundary values ───

    @Test
    void exactBoundaries() {
        // Just below attack threshold
        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, policy.route(29));
        // At attack threshold
        assertEquals(LivenessPolicy.Band.UNCERTAIN, policy.route(30));
        // Just below pass threshold
        assertEquals(LivenessPolicy.Band.UNCERTAIN, policy.route(69));
        // At pass threshold
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, policy.route(70));
    }

    // ─── Threshold invariant ───

    @Test
    void attackThresholdMustBeLessThanPassThreshold() {
        assertThrows(IllegalArgumentException.class,
                () -> new LivenessPolicy(70, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new LivenessPolicy(50, 50));
    }

    // ─── Config-driven policy ───

    @Test
    void policyFromConfig() {
        LivenessConfig config = LivenessConfig.builder()
                .attackThreshold(20)
                .passThreshold(80)
                .build();
        LivenessPolicy p = new LivenessPolicy(config);

        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, p.route(19));
        assertEquals(LivenessPolicy.Band.UNCERTAIN, p.route(20));
        assertEquals(LivenessPolicy.Band.UNCERTAIN, p.route(79));
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, p.route(80));
    }

    // ─── Custom thresholds ───

    @Test
    void customThresholds() {
        LivenessPolicy narrow = new LivenessPolicy(40, 60);
        assertEquals(LivenessPolicy.Band.CLEAR_ATTACK, narrow.route(39));
        assertEquals(LivenessPolicy.Band.UNCERTAIN, narrow.route(40));
        assertEquals(LivenessPolicy.Band.UNCERTAIN, narrow.route(59));
        assertEquals(LivenessPolicy.Band.CLEAR_BONA_FIDE, narrow.route(60));
    }
}
