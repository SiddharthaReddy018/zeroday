package liveness.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MiniFasNetScorerBytesTest {

    private static final String ZERO_PIN = "0000000000000000000000000000000000000000000000000000000000000000";

    private static String sha(byte[] b) {
        return liveness.util.Sha256.hex(liveness.util.Sha256.digest(b));
    }

    @Test
    void nullBytesFailClosedNotStub() {
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetScorer((byte[]) null, ZERO_PIN));
    }

    @Test
    void emptyBytesFailClosedNotStub() {
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetScorer(new byte[0], ZERO_PIN));
    }

    @Test
    void pinIsMandatoryForBytes() {
        byte[] b = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetScorer(b, null));
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetScorer(b, ""));
        assertThrows(IllegalArgumentException.class, () -> new MiniFasNetScorer(b, "   "));
    }

    @Test
    void wrongPinThrowsSecurityExceptionBeforeAnyModelLoad() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> new MiniFasNetScorer(new byte[]{1, 2, 3}, ZERO_PIN));
        assertTrue(e.getMessage().contains("mismatch"));
    }

    @Test
    void correctPinButCorruptBytesThrowsIllegalState() {
        byte[] bad = {1, 2, 3};
        assertThrows(IllegalStateException.class, () -> new MiniFasNetScorer(bad, sha(bad)));
    }

    @Test
    void pinnedBytesGiveSameLogitsAsPathConstructor() throws Exception {
        Path model = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");
        if (!Files.exists(model)) {
            model = Paths.get("android/liveness-android/src/main/assets/MiniFASNetV2-SE-int8.onnx");
        }
        assumeTrue(Files.exists(model), "INT8 model file not found; test SKIPPED (not passed)");
        byte[] bytes = Files.readAllBytes(model);
        float[] input = new float[3 * 128 * 128];
        Random r = new Random(7);
        for (int i = 0; i < input.length; i++) input[i] = r.nextFloat();
        try (MiniFasNetScorer fromPath = new MiniFasNetScorer(model);
             MiniFasNetScorer fromBytes = new MiniFasNetScorer(bytes, sha(bytes))) {
            assertFalse(fromBytes.isStubMode());
            assertArrayEquals(fromPath.rawLogits(input), fromBytes.rawLogits(input), 1e-6f);
            assertEquals(fromPath.modelVersion(), fromBytes.modelVersion());
        }
    }
}
