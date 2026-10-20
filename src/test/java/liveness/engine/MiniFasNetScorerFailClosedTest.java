package liveness.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class MiniFasNetScorerFailClosedTest {

    @Test
    void missingModelFileThrowsInsteadOfSilentStub() {
        Path p = Paths.get("definitely/not/here.onnx");
        assertThrows(IllegalStateException.class, () -> new MiniFasNetScorer(p));
    }

    @Test
    void corruptModelFileThrowsInsteadOfSilentStub() throws Exception {
        Path tmp = Files.createTempFile("bad", ".onnx");
        try {
            Files.write(tmp, new byte[]{1, 2, 3});
            assertThrows(IllegalStateException.class, () -> new MiniFasNetScorer(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void nullPathIsExplicitStubAndSaysSoInTheModelVersion() {
        try (MiniFasNetScorer s = new MiniFasNetScorer(null)) {
            assertTrue(s.isStubMode());
            assertTrue(s.modelVersion().startsWith("STUB"));
        }
    }
}
