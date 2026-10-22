package liveness.engine;

import liveness.util.Sha256;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for PAD and biometric models with integrity verification and anti-rollback protection (§15.2 R1, S7).
 */
public final class ModelRegistry {

    public static final class ModelMetadata {
        private final String name;
        private final Path path;
        private final String sha256;
        private final int versionOrdinal;
        private final String versionString;

        public ModelMetadata(String name, Path path, String sha256, int versionOrdinal, String versionString) {
            this.name = name;
            this.path = path;
            this.sha256 = sha256;
            this.versionOrdinal = versionOrdinal;
            this.versionString = versionString;
        }

        public String name() { return name; }
        public Path path() { return path; }
        public String sha256() { return sha256; }
        public int versionOrdinal() { return versionOrdinal; }
        public String versionString() { return versionString; }
    }

    private final Map<String, ModelMetadata> registeredModels = new ConcurrentHashMap<>();
    private final AtomicInteger rollbackCounter = new AtomicInteger(1);

    public void registerModel(String name, Path path, String expectedSha256) {
        registerModel(name, path, expectedSha256, 1, "1.0.0");
    }

    public void registerModel(String name, Path path, String expectedSha256, int versionOrdinal, String versionString) {
        Objects.requireNonNull(name, "model name must not be null");
        Objects.requireNonNull(path, "model path must not be null");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Model file does not exist: " + path);
        }

        // Anti-rollback check
        if (versionOrdinal < rollbackCounter.get()) {
            throw new SecurityException("Model rollback detected: model version " + versionOrdinal
                    + " is lower than minimum counter " + rollbackCounter.get());
        }

        // SHA-256 verification
        String actualSha;
        try {
            actualSha = Sha256.hex(Sha256.digest(Files.readAllBytes(path)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read model file for SHA-256 calculation: " + path, e);
        }

        if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
            if (!actualSha.equalsIgnoreCase(expectedSha256.trim())) {
                throw new SecurityException("Model SHA-256 mismatch for " + name
                        + ": expected " + expectedSha256 + " but calculated " + actualSha);
            }
        }

        // Monotonic advance of anti-rollback counter
        rollbackCounter.accumulateAndGet(versionOrdinal, Math::max);

        registeredModels.put(name, new ModelMetadata(
                name,
                path,
                actualSha,
                versionOrdinal,
                versionString != null ? versionString : "1.0.0"
        ));
    }

    public Path getModelPath(String name) {
        ModelMetadata meta = registeredModels.get(name);
        return meta != null ? meta.path() : null;
    }

    public ModelMetadata getMetadata(String name) {
        return registeredModels.get(name);
    }

    public String modelVersion() {
        return modelVersion("MiniFASNetV2-SE");
    }

    public String modelVersion(String name) {
        ModelMetadata meta = registeredModels.get(name);
        return meta != null ? meta.versionString() : "1.0.0";
    }

    public int antiRollbackCounter() {
        return rollbackCounter.get();
    }
}
