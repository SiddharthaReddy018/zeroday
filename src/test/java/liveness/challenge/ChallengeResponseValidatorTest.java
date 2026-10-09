package liveness.challenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.Arrays;
import java.util.Collections;

public class ChallengeResponseValidatorTest {

    @Test
    public void testValidResponse() {
        BoundChallenge challenge = new BoundChallenge("nonce", "blink", Collections.singletonList("blink"), 1000L, 5000L, 0);
        ChallengeResponseValidator validator = new ChallengeResponseValidator();
        ChallengeResponseValidator.ValidationResult result = validator.validate(challenge, "blink", 2000L);
        Assertions.assertTrue(result.isAccepted());
    }

    @Test
    public void testExpiredChallenge() {
        BoundChallenge challenge = new BoundChallenge("nonce", "blink", Collections.singletonList("blink"), 1000L, 5000L, 0);
        ChallengeResponseValidator validator = new ChallengeResponseValidator();
        ChallengeResponseValidator.ValidationResult result = validator.validate(challenge, "blink", 6000L);
        Assertions.assertFalse(result.isAccepted());
        Assertions.assertEquals("EXPIRED", result.reason());
    }

    @Test
    public void testWrongAction() {
        BoundChallenge challenge = new BoundChallenge("nonce", "blink", Collections.singletonList("blink"), 1000L, 5000L, 0);
        ChallengeResponseValidator validator = new ChallengeResponseValidator();
        ChallengeResponseValidator.ValidationResult result = validator.validate(challenge, "smile", 2000L);
        Assertions.assertFalse(result.isAccepted());
        Assertions.assertEquals("WRONG_ACTION", result.reason());
    }

    @Test
    public void testSuspiciousLatency() {
        BoundChallenge challenge = new BoundChallenge("nonce", "blink", Collections.singletonList("blink"), 1000L, 5000L, 0);
        ChallengeResponseValidator validator = new ChallengeResponseValidator();
        ChallengeResponseValidator.ValidationResult result = validator.validate(challenge, "blink", 4500L);
        Assertions.assertTrue(result.isAccepted());
        Assertions.assertTrue(result.isSuspiciousLatency());
    }

    @Test
    public void testCompoundSequence() {
        BoundChallenge challenge = new BoundChallenge("nonce", "compound", Arrays.asList("blink", "smile"), 1000L, 5000L, 0);
        ChallengeResponseValidator validator = new ChallengeResponseValidator();
        ChallengeResponseValidator.ValidationResult result = validator.validateCompound(challenge, Arrays.asList("blink", "smile"), 2000L);
        Assertions.assertTrue(result.isAccepted());
    }
}
