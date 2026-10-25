package liveness.challenge;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for challenge generator (§6.3).
 * <ul>
 *   <li>No immediate repeat of the same action</li>
 *   <li>Pool filtering works</li>
 *   <li>SecureRandom selection</li>
 * </ul>
 */
class ChallengeGeneratorTest {

    @Test
    void noImmediateRepeat() {
        ChallengeGenerator gen = new ChallengeGenerator();

        // Generate many challenges and verify no two consecutive are the same
        String previousAction = null;
        for (int i = 0; i < 100; i++) {
            BoundChallenge challenge = gen.next("nonce-1", i, 5000);
            assertNotEquals(previousAction, challenge.action(),
                    "Challenge at index " + i + " should not repeat the previous action");
            previousAction = challenge.action();
        }
    }

    @Test
    void challengeFromPool() {
        List<String> pool = Arrays.asList("BLINK", "SMILE", "TURN_LEFT");
        ChallengeGenerator gen = new ChallengeGenerator(pool, Collections.emptyList());

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            BoundChallenge c = gen.next("nonce-1", i, 5000);
            seen.add(c.action());
            assertTrue(pool.contains(c.action()),
                    "Action '" + c.action() + "' should be from the pool");
        }

        // With 50 iterations and 3 options, we should see all of them
        assertEquals(new HashSet<>(pool), seen,
                "All pool actions should be generated eventually");
    }

    @Test
    void twoElementPoolAlternates() {
        List<String> pool = Arrays.asList("BLINK", "SMILE");
        ChallengeGenerator gen = new ChallengeGenerator(pool, Collections.emptyList());

        // With only 2 elements and no-repeat rule, they must strictly alternate
        String prev = null;
        for (int i = 0; i < 20; i++) {
            BoundChallenge c = gen.next("nonce-1", i, 5000);
            assertNotEquals(prev, c.action());
            prev = c.action();
        }
    }

    @Test
    void challengeHasSessionNonce() {
        ChallengeGenerator gen = new ChallengeGenerator();
        BoundChallenge c = gen.next("my-session-nonce", 0, 5000);
        assertEquals("my-session-nonce", c.sessionNonce());
    }

    @Test
    void challengeHasSequenceIndex() {
        ChallengeGenerator gen = new ChallengeGenerator();
        BoundChallenge c0 = gen.next("nonce", 0, 5000);
        BoundChallenge c1 = gen.next("nonce", 1, 5000);
        assertEquals(0, c0.sequenceIndex());
        assertEquals(1, c1.sequenceIndex());
    }

    @Test
    void challengeExpiry() {
        ChallengeGenerator gen = new ChallengeGenerator();
        long before = System.currentTimeMillis();
        BoundChallenge c = gen.next("nonce", 0, 5000);
        long after = System.currentTimeMillis();

        assertTrue(c.issuedAtMs() >= before);
        assertTrue(c.issuedAtMs() <= after);
        assertTrue(c.expiresAtMs() >= before + 5000);
        assertTrue(c.expiresAtMs() <= after + 5000);
    }

    @Test
    void challengeExpiryCheck() {
        ChallengeGenerator gen = new ChallengeGenerator();
        BoundChallenge c = gen.next("nonce", 0, 5000);

        assertFalse(c.isExpired(c.issuedAtMs()));
        assertFalse(c.isExpired(c.expiresAtMs()));
        assertTrue(c.isExpired(c.expiresAtMs() + 1));
    }

    @Test
    void compoundChallengeGeneratesMultipleActions() {
        ChallengeGenerator gen = new ChallengeGenerator();
        BoundChallenge c = gen.nextCompound("nonce", 0, 10000, 3);

        assertEquals(3, c.expectedSequence().size());
        // No two consecutive steps should be the same within the compound
        for (int i = 1; i < c.expectedSequence().size(); i++) {
            assertNotEquals(c.expectedSequence().get(i - 1), c.expectedSequence().get(i),
                    "Compound step " + i + " should differ from step " + (i - 1));
        }
    }

    @Test
    void compoundChallengeActionsFromPool() {
        List<String> pool = Arrays.asList("BLINK", "SMILE", "TURN_LEFT", "TURN_RIGHT");
        ChallengeGenerator gen = new ChallengeGenerator(pool, Collections.emptyList());
        BoundChallenge c = gen.nextCompound("nonce", 0, 10000, 3);

        for (String action : c.expectedSequence()) {
            assertTrue(pool.contains(action),
                    "Compound action '" + action + "' should be from the pool");
        }
    }

    @Test
    void challengeIdIsUnique() {
        ChallengeGenerator gen = new ChallengeGenerator();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            BoundChallenge c = gen.next("nonce", i, 5000);
            assertTrue(ids.add(c.challengeId()),
                    "Challenge IDs should be unique");
        }
    }

    @Test
    void singleElementPoolThrowsOnSecondCall() {
        List<String> pool = Collections.singletonList("BLINK");
        ChallengeGenerator gen = new ChallengeGenerator(pool, Collections.emptyList());

        // First call succeeds
        BoundChallenge c = gen.next("nonce", 0, 5000);
        assertEquals("BLINK", c.action());

        // Second call should fail — BLINK was just issued, only option is removed
        assertThrows(IllegalStateException.class,
                () -> gen.next("nonce", 1, 5000));
    }

    @Test
    void defaultPoolHasAllExpectedActions() {
        assertEquals(7, ChallengeGenerator.DEFAULT_POOL.size());
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("BLINK"));
        assertTrue(!ChallengeGenerator.DEFAULT_POOL.contains("SMILE"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("TURN_LEFT"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("TURN_RIGHT"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("LOOK_LEFT"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("LOOK_RIGHT"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("LOOK_UP"));
        assertTrue(ChallengeGenerator.DEFAULT_POOL.contains("LOOK_DOWN"));
    }
}
