package liveness.device;

import liveness.core.FaceFrame;
import liveness.core.FrameQuality;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MdsHttpDeviceAdapter implements DeviceAdapter {

    private final String mdsHost;
    private final int mdsPort;
    private final String transactionId;
    
    private MdsDeviceInfo currentDevice;
    private boolean open;

    public MdsHttpDeviceAdapter(String mdsHost, int mdsPort, String transactionId) {
        this.mdsHost = mdsHost;
        this.mdsPort = mdsPort;
        this.transactionId = transactionId;
        this.open = false;
    }

    @Override
    public void open() throws DeviceException {
        try {
            // GET MOSIPDINFO
            String infoUrl = "http://" + mdsHost + ":" + mdsPort + "/info";
            String json = sendGetRequest(infoUrl);
            
            String status = extractJsonValue(json, "status");
            String bioType = extractJsonValue(json, "bioType");
            String deviceId = extractJsonValue(json, "deviceId");
            String deviceSubId = extractJsonValue(json, "deviceSubId");
            String env = extractJsonValue(json, "env");
            String purpose = extractJsonValue(json, "purpose");

            // For SBIDISC, normally it's POST /device on port+1, but we fallback to manually extracting basic info here.
            
            if (bioType != null && !"Face".equalsIgnoreCase(bioType)) {
                throw new DeviceException("Device is not a Face biometric device.");
            }

            this.currentDevice = new MdsDeviceInfo.Builder()
                .deviceId(deviceId)
                .deviceSubId(deviceSubId != null ? deviceSubId : "1")
                .bioType(bioType != null ? bioType : "Face")
                .status(status)
                .env(env)
                .purpose(purpose)
                .specVersion(Collections.singletonList("1.0"))
                .build();
            
            this.open = true;

        } catch (Exception e) {
            throw new DeviceException("Failed to open device: " + e.getMessage(), e);
        }
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        if (!isOpen() || currentDevice == null) {
            throw new DeviceException("Device is not open.");
        }

        try {
            String captureUrl = "http://" + mdsHost + ":" + mdsPort + "/capture";
            
            String env = currentDevice.getEnv() != null ? currentDevice.getEnv() : "Developer";
            String purpose = currentDevice.getPurpose() != null ? currentDevice.getPurpose() : "Registration";
            String deviceId = currentDevice.getDeviceId() != null ? currentDevice.getDeviceId() : "";
            String deviceSubId = currentDevice.getDeviceSubId() != null ? currentDevice.getDeviceSubId() : "1";
            
            String isoTimestamp = Instant.now().toString();

            String requestBody = "{" +
                "\"env\": \"" + env + "\"," +
                "\"purpose\": \"" + purpose + "\"," +
                "\"specVersion\": \"1.0\"," +
                "\"timeout\": 10000," +
                "\"captureTime\": \"" + isoTimestamp + "\"," +
                "\"transactionId\": \"" + transactionId + "\"," +
                "\"bio\": [{" +
                    "\"type\": \"Face\"," +
                    "\"count\": 1," +
                    "\"requestedScore\": 9," +
                    "\"deviceId\": \"" + deviceId + "\"," +
                    "\"deviceSubId\": \"" + deviceSubId + "\"," +
                    "\"previousHash\": \"\"" +
                "}]" +
            "}";

            String responseJson = sendPostRequest(captureUrl, requestBody);
            
            String bioValue = extractJsonValue(responseJson, "bioValue");
            if (bioValue == null || bioValue.isEmpty()) {
                // Check if it's a JWT
                String[] parts = responseJson.split("\\.");
                if (parts.length == 3) {
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    bioValue = extractJsonValue(payloadJson, "bioValue");
                }
            }

            if (bioValue == null || bioValue.isEmpty()) {
                throw new DeviceException("No biometric data returned from device.");
            }

            byte[] imageBytes = Base64.getDecoder().decode(bioValue);
            return new FaceFrame(FrameQuality.GOOD, "rcapture", 0,
                    System.currentTimeMillis(), 0,
                    0, 0, imageBytes, 0, 0, 0, 0,
                    0.0, 0.0, 0.0, 1, 0.0, 0.0, null);

        } catch (Exception e) {
            throw new DeviceException("Capture failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        this.open = false;
        this.currentDevice = null;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public MdsDeviceInfo deviceInfo() {
        return currentDevice;
    }

    private String sendGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new DeviceException("HTTP GET failed with status " + status);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String sendPostRequest(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(40000); // 40 seconds read timeout as per requirements
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new DeviceException("HTTP POST failed with status " + status);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String extractJsonValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
