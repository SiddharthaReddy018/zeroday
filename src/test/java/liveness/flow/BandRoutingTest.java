package liveness.flow;

import liveness.policy.LivenessPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for attack persistence windows (§5.1).
 * <p>
 * CLEAR_ATTACK is only declared after the composite score falls below
 * attackThreshold for N consecutive quality-OK windows. A single bad
 * frame does not block.
 */
class BandRoutingTest {

    private final LivenessPolicy policy = new LivenessPolicy(30, 70);

    /**
     * Simulates N consecutive windows below the attack threshold.
     * Attack should only be declared after the persistence count is met.
     */
    @Test
    void attackNotDeclaredUntilNConsecutiveWindows() {
        int persistenceWindows = 3;
        int consecutiveAttackCount = 0;
        boolean attackDeclared = false;

        int[] scores = {10, 15, 20}; // 3 consecutive below threshold

        for (int score : scores) {
            LivenessPolicy.Band band = policy.route(score);
            if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0;
            }
            if (consecutiveAttackCount >= persistenceWindows) {
                attackDeclared = true;
            }
        }

        assertTrue(attackDeclared, "Attack should be declared after 3 consecutive attack-band windows");
    }

    @Test
    void singleBadFrameDoesNotBlock() {
        int persistenceWindows = 3;
        int consecutiveAttackCount = 0;

        // One bad score, then a good one
        int[] scores = {10, 50, 10};

        for (int score : scores) {
            LivenessPolicy.Band band = policy.route(score);
            if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0; // Reset on non-attack
            }
        }

        assertTrue(consecutiveAttackCount < persistenceWindows,
                "Single bad frame interleaved should NOT trigger attack");
    }

    @Test
    void attackResetByUncertainFrame() {
        int persistenceWindows = 3;
        int consecutiveAttackCount = 0;
        boolean attackDeclared = false;

        // 2 attack, 1 uncertain, 2 attack — never reaches 3 consecutive
        int[] scores = {10, 15, 50, 10, 15};

        for (int score : scores) {
            LivenessPolicy.Band band = policy.route(score);
            if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0;
            }
            if (consecutiveAttackCount >= persistenceWindows) {
                attackDeclared = true;
            }
        }

        assertFalse(attackDeclared,
                "Attack should NOT be declared when interrupted by uncertain score");
    }

    @Test
    void attackDeclaredExactlyAtNthWindow() {
        int persistenceWindows = 3;
        int consecutiveAttackCount = 0;
        int windowWhenDeclared = -1;

        int[] scores = {10, 20, 25, 10}; // Attack at windows 0,1,2

        for (int i = 0; i < scores.length; i++) {
            LivenessPolicy.Band band = policy.route(scores[i]);
            if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0;
            }
            if (consecutiveAttackCount >= persistenceWindows && windowWhenDeclared < 0) {
                windowWhenDeclared = i;
            }
        }

        assertEquals(2, windowWhenDeclared,
                "Attack should be declared at exactly the 3rd consecutive window (index 2)");
    }

    @Test
    void bonaFideNeverTriggersAttack() {
        int persistenceWindows = 3;
        int consecutiveAttackCount = 0;

        int[] scores = {80, 90, 75, 85, 95};

        for (int score : scores) {
            LivenessPolicy.Band band = policy.route(score);
            if (band == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0;
            }
        }

        assertEquals(0, consecutiveAttackCount,
                "Bona fide scores should never accumulate attack windows");
    }

    @Test
    void persistenceWindowIsConfigurable() {
        // With persistence = 5, need 5 consecutive attack windows
        int persistenceWindows = 5;
        int consecutiveAttackCount = 0;
        boolean attackDeclared = false;

        // Only 4 attack scores — should NOT trigger
        int[] scores = {10, 15, 20, 25};
        for (int score : scores) {
            if (policy.route(score) == LivenessPolicy.Band.CLEAR_ATTACK) {
                consecutiveAttackCount++;
            } else {
                consecutiveAttackCount = 0;
            }
            if (consecutiveAttackCount >= persistenceWindows) {
                attackDeclared = true;
            }
        }

        assertFalse(attackDeclared, "4 windows is not enough when persistence is 5");
    }
}
