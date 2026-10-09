package liveness.engine;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelRegistry {
    
    private final Map<String, Path> registeredModels = new ConcurrentHashMap<>();
    private int rollbackCounter = 1;

    public void registerModel(String name, Path path, String expectedSha256) {
        registeredModels.put(name, path);
    }

    public Path getModelPath(String name) {
        return registeredModels.get(name);
    }

    public String modelVersion() {
        return "1.0.0";
    }

    public int antiRollbackCounter() {
        return rollbackCounter;
    }
}
