package liveness.device;

import liveness.android.IsoFaceRgbDecoder;
import liveness.android.MiniJson;
import liveness.android.SbiIntentDeviceAdapter;
import liveness.android.SbiJwtVerifier;
import liveness.core.FaceFrame;
import liveness.core.FrameQuality;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec-compliant MOSIP MDS HTTP device adapter for desktop environments.
 * Connects to MDS service via HTTP, validates /info, executes /capture (or /rcapture),
 * verifies RCAPTURE JWT using SbiJwtVerifier, decodes RGB dimensions with RgbDecoder,
 * and supports /stream endpoint.
 */
public class MdsHttpDeviceAdapter implements DeviceAdapter {

    private final String mdsHost;
    private final int mdsPort;
    private final SbiJwtVerifier verifier;
    private final SbiIntentDeviceAdapter.RgbDecoder decoder;

    private MdsDeviceInfo currentDevice;
    private boolean open;
    private String lastReturnedTxId;

    public MdsHttpDeviceAdapter(String mdsHost, int mdsPort, SbiJwtVerifier verifier, SbiIntentDeviceAdapter.RgbDecoder decoder) {
        this.mdsHost = mdsHost;
        this.mdsPort = mdsPort;
        this.verifier = verifier != null ? verifier : SbiJwtVerifier.disabledForDevelopment();
        this.decoder = decoder != null ? decoder : defaultDesktopDecoder();
        this.open = false;
    }

    public MdsHttpDeviceAdapter(String mdsHost, int mdsPort, SbiJwtVerifier verifier) {
        this(mdsHost, mdsPort, verifier, defaultDesktopDecoder());
    }

    public MdsHttpDeviceAdapter(String mdsHost, int mdsPort, String transactionId) {
        this(mdsHost, mdsPort, SbiJwtVerifier.disabledForDevelopment(), defaultDesktopDecoder());
    }

    public static SbiIntentDeviceAdapter.RgbDecoder defaultDesktopDecoder() {
        SbiIntentDeviceAdapter.RgbDecoder direct = IsoFaceRgbDecoder.desktopJpegDecoder();
        IsoFaceRgbDecoder iso = new IsoFaceRgbDecoder(direct, null);
        return encoded -> {
            try {
                return iso.decode(encoded);
            } catch (Exception e) {
                return direct.decode(encoded);
            }
        };
    }

    @Override
    public void open() throws DeviceException {
        try {
            String infoUrl = "http://" + mdsHost + ":" + mdsPort + "/info";
            String json = sendGetRequest(infoUrl);
            Object parsed = MiniJson.parse(json);
            Map<String, Object> infoMap;
            if (parsed instanceof List) {
                List<?> list = (List<?>) parsed;
                if (list.isEmpty()) throw new DeviceException("MDS_NO_DEVICE: /info returned empty list");
                infoMap = (Map<String, Object>) list.get(0);
            } else if (parsed instanceof Map) {
                infoMap = (Map<String, Object>) parsed;
            } else {
                throw new DeviceException("MDS_BAD_RESPONSE: /info returned non-object");
            }

            String status = str(infoMap.get("status") != null ? infoMap.get("status") : infoMap.get("deviceStatus"));
            String bioType = str(infoMap.get("bioType") != null ? infoMap.get("bioType") : infoMap.get("deviceType"));
            String deviceId = str(infoMap.get("deviceId"));
            String deviceSubId = str(infoMap.get("deviceSubId"));
            String deviceCode = str(infoMap.get("deviceCode"));
            String env = str(infoMap.get("env"));
            String purpose = str(infoMap.get("purpose"));

            if (status == null || !"Ready".equalsIgnoreCase(status)) {
                throw new DeviceException("MDS_DEVICE_NOT_READY: device status is " + status);
            }
            if (bioType != null && !"Face".equalsIgnoreCase(bioType)) {
                throw new DeviceException("MDS_BAD_DEVICE: bioType is not Face (" + bioType + ")");
            }

            this.currentDevice = new MdsDeviceInfo.Builder()
                .deviceId(deviceId != null ? deviceId : "mds-dev-1")
                .deviceCode(deviceCode)
                .deviceSubId(deviceSubId != null ? deviceSubId : "1")
                .bioType(bioType != null ? bioType : "Face")
                .status(status)
                .env(env != null ? env : "Target")
                .purpose(purpose != null ? purpose : "Registration")
                .specVersion(Collections.singletonList("0.9.5"))
                .build();

            this.open = true;
        } catch (DeviceException e) {
            throw e;
        } catch (Exception e) {
            throw new DeviceException("Failed to open device: " + e.getMessage(), e);
        }
    }

    @Override
    public FaceFrame captureFrame() throws DeviceException {
        if (!isOpen() || currentDevice == null) {
            throw new DeviceException("Device is not open.");
        }
        String streamUrl = "http://" + mdsHost + ":" + mdsPort + "/stream";
        byte[] frameBytes;
        try {
            frameBytes = sendGetRequestBytes(streamUrl);
        } catch (Exception e) {
            throw new DeviceException("MDS_STREAM_UNSUPPORTED: " + e.getMessage(), e);
        }
        if (frameBytes == null || frameBytes.length == 0) {
            throw new DeviceException("MDS_STREAM_EMPTY: no frame received from /stream");
        }
        SbiIntentDeviceAdapter.RgbDecoder.Decoded decoded;
        try {
            decoded = decoder.decode(frameBytes);
        } catch (Exception e) {
            throw new DeviceException("MDS_STREAM_DECODE_FAILED: " + e.getMessage(), e);
        }
        String cert = "mds:" + currentDevice.getDeviceId();
        return new FaceFrame(FrameQuality.GOOD, cert, cert.hashCode(),
                System.currentTimeMillis(), 0,
                decoded.width, decoded.height, decoded.rgb,
                0, 0, decoded.width, decoded.height,
                0.0, 0.0, 0.0, 1, 0.0, 0.0, null, 0L, frameBytes);
    }

    @Override
    public FaceFrame captureFinal(String boundTransactionId) throws DeviceException {
        if (!isOpen() || currentDevice == null) {
            throw new DeviceException("Device is not open.");
        }
        if (boundTransactionId == null || !boundTransactionId.contains(":")) {
            throw new DeviceException("MDS_BAD_TRANSACTION_ID: expected <base>:<nonce>");
        }
        String wireId = SbiIntentDeviceAdapter.wireTransactionId(boundTransactionId);
        lastReturnedTxId = null;

        Map<String, Object> bio = new LinkedHashMap<>();
        bio.put("type", "Face");
        bio.put("count", 1L);
        bio.put("requestedScore", 40L);
        bio.put("deviceId", currentDevice.getDeviceId() == null ? "" : currentDevice.getDeviceId());
        bio.put("deviceSubId", currentDevice.getDeviceSubId() == null ? "1" : currentDevice.getDeviceSubId());
        bio.put("previousHash", "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("env", currentDevice.getEnv() != null ? currentDevice.getEnv() : "Target");
        req.put("purpose", currentDevice.getPurpose() != null ? currentDevice.getPurpose() : "Registration");
        req.put("specVersion", "0.9.5");
        req.put("timeout", 10000L);
        req.put("captureTime", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        req.put("transactionId", wireId);
        req.put("bio", Collections.singletonList(bio));

        String requestBody = MiniJson.write(req);
        String captureUrl = "http://" + mdsHost + ":" + mdsPort + "/capture";
        byte[] responseBytes;
        try {
            responseBytes = sendPostRequest(captureUrl, requestBody);
        } catch (DeviceException e) {
            // Fallback to /rcapture
            String rcaptureUrl = "http://" + mdsHost + ":" + mdsPort + "/rcapture";
            responseBytes = sendPostRequest(rcaptureUrl, requestBody);
        }

        Map<String, Object> root;
        try {
            root = MiniJson.parseObject(new String(responseBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new DeviceException("MDS_BAD_RESPONSE: malformed response JSON", e);
        }

        String jwt = null;
        Object biometrics = root.get("biometrics");
        if (biometrics instanceof List && !((List<?>) biometrics).isEmpty()) {
            Object first = ((List<?>) biometrics).get(0);
            if (first instanceof Map) {
                Object data = ((Map<?, ?>) first).get("data");
                if (data instanceof String) {
                    jwt = (String) data;
                }
            }
        }
        if (jwt == null && root.get("data") instanceof String) {
            jwt = (String) root.get("data");
        }

        Map<String, Object> payload;
        byte[] imageBytes;
        String respTxId;
        String deviceCode = currentDevice.getDeviceCode();

        if (jwt != null) {
            String payloadJson;
            try {
                payloadJson = verifier.verify(jwt);
            } catch (SbiJwtVerifier.VerificationException e) {
                throw new DeviceException("MDS_SIGNATURE_INVALID: " + e.getMessage(), e);
            }
            try {
                payload = MiniJson.parseObject(payloadJson);
            } catch (Exception e) {
                throw new DeviceException("MDS_BAD_RESPONSE: payload is not JSON", e);
            }
            String bioType = (String) payload.get("bioType");
            if (bioType != null && !"Face".equalsIgnoreCase(bioType)) {
                throw new DeviceException("MDS_BAD_RESPONSE: bioType is not Face");
            }
            respTxId = (String) payload.get("transactionId");
            String bioVal = (String) payload.get("bioValue");
            if (bioVal == null || bioVal.isEmpty()) {
                throw new DeviceException("MDS_BAD_RESPONSE: no bioValue in payload");
            }
            imageBytes = decodeBase64Any(bioVal);
            if (payload.get("deviceCode") != null) {
                deviceCode = String.valueOf(payload.get("deviceCode"));
            }
        } else {
            if (!verifier.isDisabled()) {
                throw new DeviceException("MDS_SIGNATURE_INVALID: missing signed JWT in response");
            }
            payload = root;
            respTxId = (String) payload.get("transactionId");
            String bioVal = (String) payload.get("bioValue");
            if (bioVal == null || bioVal.isEmpty()) {
                throw new DeviceException("MDS_BAD_RESPONSE: no bioValue");
            }
            imageBytes = decodeBase64Any(bioVal);
        }

        if (respTxId == null || (!respTxId.equals(wireId) && !respTxId.equals(boundTransactionId))) {
            throw new DeviceException("MDS_NONCE_MISMATCH: expected " + wireId + " but got " + respTxId);
        }

        SbiIntentDeviceAdapter.RgbDecoder.Decoded decoded;
        try {
            decoded = decoder.decode(imageBytes);
        } catch (Exception e) {
            throw new DeviceException("MDS_DECODE_FAILED: failed to decode image: " + e.getMessage(), e);
        }

        lastReturnedTxId = wireId.equals(respTxId) ? boundTransactionId : respTxId;
        int width = decoded.width;
        int height = decoded.height;
        byte[] rgb = decoded.rgb;

        String cert = "mds:" + (deviceCode != null ? deviceCode : currentDevice.getDeviceId());
        return new FaceFrame(FrameQuality.GOOD, cert, cert.hashCode(),
                System.currentTimeMillis(), 0,
                width, height, rgb,
                0, 0, width, height,
                0.0, 0.0, 0.0, 1, 0.0, 0.0, null, 0L, imageBytes);
    }

    @Override
    public String lastReturnedTransactionId() {
        return lastReturnedTxId;
    }

    @Override
    public String captureMode() {
        return "MDS_HTTP";
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

    private String sendGetRequest(String urlStr) throws DeviceException {
        return new String(sendGetRequestBytes(urlStr), StandardCharsets.UTF_8);
    }

    private byte[] sendGetRequestBytes(String urlStr) throws DeviceException {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new DeviceException("HTTP GET " + urlStr + " failed with status " + status);
            }

            try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                return baos.toByteArray();
            }
        } catch (DeviceException e) {
            throw e;
        } catch (Exception e) {
            throw new DeviceException("HTTP GET error: " + e.getMessage(), e);
        }
    }

    private byte[] sendPostRequest(String urlStr, String jsonBody) throws DeviceException {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new DeviceException("HTTP POST " + urlStr + " failed with status " + status);
            }

            try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                return baos.toByteArray();
            }
        } catch (DeviceException e) {
            throw e;
        } catch (Exception e) {
            throw new DeviceException("HTTP POST error: " + e.getMessage(), e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static byte[] decodeBase64Any(String s) {
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return Base64.getDecoder().decode(s);
        }
    }
}
