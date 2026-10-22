package liveness.engine;

import liveness.util.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelRegistryTest {

    @Test
    void registerAndVerifyModelShaAndMetadata(@TempDir Path tempDir) throws IOException {
        Path modelFile = tempDir.resolve("test_model.onnx");
        byte[] content = "dummy-model-binary-weights-12345".getBytes(StandardCharsets.UTF_8);
        Files.write(modelFile, content);

        String correctSha = Sha256.hex(Sha256.digest(content));

        ModelRegistry registry = new ModelRegistry();
        registry.registerModel("testModel", modelFile, correctSha, 1, "1.0.0");

        assertEquals(modelFile, registry.getModelPath("testModel"));
        assertEquals("1.0.0", registry.modelVersion("testModel"));
        assertEquals(1, registry.antiRollbackCounter());

        ModelRegistry.ModelMetadata meta = registry.getMetadata("testModel");
        assertNotNull(meta);
        assertEquals(correctSha, meta.sha256());
    }

    @Test
    void registerModelThrowsOnShaMismatch(@TempDir Path tempDir) throws IOException {
        Path modelFile = tempDir.resolve("corrupted_model.onnx");
        byte[] content = "corrupted-content".getBytes(StandardCharsets.UTF_8);
        Files.write(modelFile, content);

        ModelRegistry registry = new ModelRegistry();
        assertThrows(SecurityException.class, () ->
                registry.registerModel("corrupted", modelFile, "0000111122223333444455556666777788889999aaaabbbbccccddddeeeeffff"));
    }

    @Test
    void antiRollbackRejectsLowerVersion(@TempDir Path tempDir) throws IOException {
        Path modelV2 = tempDir.resolve("model_v2.onnx");
        Files.write(modelV2, "model-v2".getBytes(StandardCharsets.UTF_8));
        Path modelV1 = tempDir.resolve("model_v1.onnx");
        Files.write(modelV1, "model-v1".getBytes(StandardCharsets.UTF_8));

        ModelRegistry registry = new ModelRegistry();
        registry.registerModel("v2", modelV2, null, 2, "2.0.0");
        assertEquals(2, registry.antiRollbackCounter());

        // Attempting to register an older version (ordinal 1 < 2) must be rejected
        assertThrows(SecurityException.class, () ->
                registry.registerModel("v1", modelV1, null, 1, "1.0.0"));
    }

    @Test
    void scorerLoadWithMismatchedShaThrowsSecurityException(@TempDir Path tempDir) throws IOException {
        Path modelFile = tempDir.resolve("fake_model.onnx");
        Files.write(modelFile, "fake-bytes".getBytes(StandardCharsets.UTF_8));

        assertThrows(SecurityException.class, () ->
                new MiniFasNetScorer(modelFile, "deadbeefcafebabe0000111122223333444455556666777788889999aaaabbbb"));
    }
}
