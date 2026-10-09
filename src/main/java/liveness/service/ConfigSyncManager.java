package liveness.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigSyncManager {
    private final String serverUrl;
    private final LivenessServiceConfig config;
    private final long syncIntervalMs;
    private ScheduledExecutorService executor;
    private boolean online = false;

    public ConfigSyncManager(String serverUrl, LivenessServiceConfig config, long syncIntervalMs) {
        this.serverUrl = serverUrl;
        this.config = config;
        this.syncIntervalMs = syncIntervalMs;
    }

    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConfigSyncDaemon");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::syncNow, 0, syncIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public void syncNow() {
        try {
            URL url = new URL(serverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    String json = out.toString("UTF-8");
                    config.syncFromServer(json);
                    online = true;
                }
            } else {
                handleFailure();
            }
        } catch (Exception e) {
            handleFailure();
        }
    }

    private void handleFailure() {
        online = false;
        if (config.isStale(syncIntervalMs * 3)) {
            config.fallbackToLastKnownGood();
        }
    }

    public boolean isOnline() {
        return online;
    }
}
