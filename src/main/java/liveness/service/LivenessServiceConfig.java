package liveness.service;

import liveness.policy.LivenessConfig;

public class LivenessServiceConfig {
    private LivenessConfig activeConfig;
    private LivenessConfig lastKnownGoodConfig;
    private String configVersion;
    private long lastSyncTimestampMs;

    public LivenessServiceConfig(LivenessConfig initialConfig, String configVersion) {
        this.activeConfig = initialConfig;
        this.lastKnownGoodConfig = initialConfig;
        this.configVersion = configVersion;
        this.lastSyncTimestampMs = System.currentTimeMillis();
    }

    public synchronized LivenessConfig active() {
        return activeConfig;
    }

    public synchronized void syncFromServer(String serverConfigJson) {
        // In a real implementation, we would parse JSON to a new LivenessConfig.
        // We'll simulate it here by keeping the object but updating metadata.
        this.lastKnownGoodConfig = this.activeConfig;
        // Stub parse: this.activeConfig = parse(serverConfigJson);
        this.lastSyncTimestampMs = System.currentTimeMillis();
        this.configVersion = "synced-" + System.currentTimeMillis();
    }

    public synchronized void fallbackToLastKnownGood() {
        this.activeConfig = this.lastKnownGoodConfig;
    }

    public synchronized boolean isStale(long maxAgeMs) {
        return (System.currentTimeMillis() - lastSyncTimestampMs) > maxAgeMs;
    }

    public synchronized String configVersion() {
        return configVersion;
    }
}
