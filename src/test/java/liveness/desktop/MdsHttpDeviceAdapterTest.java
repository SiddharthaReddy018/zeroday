package liveness.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import liveness.android.MiniJson;
import liveness.android.SbiIntentDeviceAdapter;
import liveness.android.SbiJwtVerifier;
import liveness.core.FaceFrame;
import liveness.device.DeviceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MdsHttpDeviceAdapterTest {

    // Same test certs from SbiJwtVerifierTest
    static final String ROOT_CERT = "MIIDFzCCAf+gAwIBAgIUGWlPk+DaD5IBMDuzRsUQICYSt+EwDQYJKoZIhvcNAQELBQAwGzEZMBcGA1UEAwwQVGVzdCBEZXZpY2UgUm9vdDAeFw0yNjEwMTkwNDAyMTRaFw00NjEwMTQwNDAyMTRaMBsxGTAXBgNVBAMMEFRlc3QgRGV2aWNlIFJvb3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCtpo0/k0OQsVNX/1/6PUB4QttHwod5E1s45KepOZsPQ2/qx6YpHiuI0DTQ43YmPmNn2zt8Zi9Yl2uAFGKGS0dYSGeB1MbY23pyYVjc/lvus4l2d4TW8C0ovyXJrpOBS0mm0XOxQHZ+NxSbwQ1bE3+B6zeJOQOwXzIJNi0kqVOkJLzhuacQsq0Ni0+Tuot5Nix+KQdYuALRSRPTph4UV4wlp+rltw3xvr/tkOYZgJ9oUrmMJxjfUN403dI/5PhnV2Vj7xvlcYY+Gr0KCm+9nCA/zeiauUW+8wfTtuAiJyTBoRTt8F6e6g2uo9l1vicV/7w3UXYek1weEKEW93MWTrCjAgMBAAGjUzBRMB0GA1UdDgQWBBQ1L+Of/7Wow8TU2W3XWwOXRbdUAzAfBgNVHSMEGDAWgBQ1L+Of/7Wow8TU2W3XWwOXRbdUAzAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCoSngEy8gkxpDYBqZfKf//oMqq55h8P/qppsKGWsOy4YseVeEWhRfMZhylR7AM+xRNY9/+I+c0mhNI20DcTqinDFkXjDHiKCUvAhIuup6+madU99cX0oJ45FxxRwdTTRyMiUu+qe0PzATt18dWb1swLfz0iSXyGoxR+av/za42AR3yiF9NapI9yuE+4OcxX/VldVQbPMUemS9OpPYy1ls6M3ROCvdkfRh750N2aytDUxKXyvgCZJxWS3zYKbhOJ9RZdVMj7/yj4DWBtZA6GS3Me/qLSW22wdjR931Yiw+/f0Le5fFqqaECU4qPvNMjzWQz6J1Nog2Yq9+aFyJsk2FI";
    static final String DEVICE_CERT = "MIICvDCCAaQCFDvldvZ2NwKPuFs9q5p5wQbQRDHQMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAMMEFRlc3QgRGV2aWNlIFJvb3QwHhcNMjYxMDE5MDQwMjE0WhcNNDYxMDE0MDQwMjE0WjAaMRgwFgYDVQQDDA9UZXN0IFNCSSBEZXZpY2UwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC24ohJvOl0oOFuW7dN/+0S+roQAz+ZSpwhHaaIId0aMWPiL8+VqBy8oQukLi8RBbTD8H+1WygWQ3AJ1r/1iQ9q4jZF9OCN5FwfD1P2retFQv7dsCiYzXgbSBmSZQU2/u6Kl5dJ3ugdpbipiLfKjRcghdhbTWg9QBTu6SMiL0CnLhHgdItQhaXrG63D18+5ZwAj42thlOjCj9NpIfFQBf55empbrUwzZTyUKUHtwdiGXLCIU1dQR4YpqL1di//EIS1M6PfqVLpD3UKUv2jUjfj3W2veFMrLD90ATbuek7JzhU4GPb5o8hhLUsDrYKhbZQ8LuC0pD+rTc9z/ERuk7v3dAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAD39lByzwbmzJsTPZbi89E8WiXkhnNRTvHmzzK3dlaDb03FZazjmTYBqnXgFaZKGr6FQsq4bGRSABy/YMf8MpbDQ+F7J5386GjheERY2sQtBB3Twe9ChZLQ4+bNYjScHM8pWuOliHXA9Z+6A5QORnL1nFOE9Vjm3w0MVAZryCYY7/rodBytOfpHJkuvqV2zanQVlIX0ISnvJc2Z5v/57VDGu+oFJpBJ9pqHlCvolvf+Ibqm9iaEn14ISPqvv7VuV8hbVQ5z4eZk2DxdTnK37u9EIWeTURdgx9XAFv2PD6pwy/uTDhbAcw6giMHNX35E4xnvB9O7jMAiwBo+7KXpC+IY=";
    static final String DEVICE_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC24ohJvOl0oOFuW7dN/+0S+roQAz+ZSpwhHaaIId0aMWPiL8+VqBy8oQukLi8RBbTD8H+1WygWQ3AJ1r/1iQ9q4jZF9OCN5FwfD1P2retFQv7dsCiYzXgbSBmSZQU2/u6Kl5dJ3ugdpbipiLfKjRcghdhbTWg9QBTu6SMiL0CnLhHgdItQhaXrG63D18+5ZwAj42thlOjCj9NpIfFQBf55empbrUwzZTyUKUHtwdiGXLCIU1dQR4YpqL1di//EIS1M6PfqVLpD3UKUv2jUjfj3W2veFMrLD90ATbuek7JzhU4GPb5o8hhLUsDrYKhbZQ8LuC0pD+rTc9z/ERuk7v3dAgMBAAECggEAGoekI6Y+64dHDH5LigodNwqeISKHgFI9ceReOxNGgr01cyNiZqBbgtWN3cRBWaPNRDtIG2Pc3dXsk9DOSe0XCndETkOmq467+ll74aX7IjIEzN34FaqAyNxdsUnsRr0Wl45ECRhH7b0Tm6xUlRKAj1w7NYt2GqSIJNw3F4dpRpb+gmsYMBcJAf+oM54x/y1CB9yeOiWQE7rB6U7qVHuszloE7EZPDRh9THOR1hyVnIVNBeW7wR6s3cYwPBg3sCsYhw4eEHbiKw/FczGWexAkLtn/dRXnwpGF00VpZc9nWVrlfpFsu8q4Np7GY0/sh8kbquu/Fe9T7NxrWcktnAkqNQKBgQDbV7IMvNwM0HnensMAFc/x+szbZNvzUGqdRFUErVIP4bOHQSEofIgTz03kzjUevO6I58/7UGmK9CMB0AvbgAgyEUJ9r9lfa1/keNmbOPrkuGgTUAmgMtfPNfE8CetH88UYxafby46V45LRfSmZu39rWDXPWRLDq0pYiT09wGPfQwKBgQDVcwo2imubtxAnHBOp4GmYl3k49rkrDPusB9up2DUtJRIB7YSy4Ih9DcDVGA1yX0jKqveyFzqIFPBI3FvuE9KFLb0DOFQ9My7p+LPOuJzR+joFuTnBUngTsXJtIR4BFZGnhSwNSgJGId+FYzhhfQkeI9AUQUntVqE7RAY8zngMXwKBgQC3dScADmrC8RAtyn5xMM1HqKz1h+uMHCPAPGobylfL3syJAUmZkmdCom6VRIAboIymx7Umj4Bnb4FkP73bLsAsGFGTzKQ0chu3ffkMImF/MloiZRxX+I6WyphlzioTeWnOKWwXo68z4S9otpY39iJCB3qiXe8a58b8PHWTk7o0/wKBgCtr36QAvKtMrkiGeamRMWoNRmfEKR8M4RW584G1rfulEui5fMk2zA1EgvnWjsW5hIFWDlV4TDvqpobr7PedqLHXACBrkRdyFhUattlSzPQaRztq1sjxEeRPsMtqdUQbMyo0+SUq7RN/SI1GBe4vFW/AMVAth/B0MxBvYiV0iw9ZAoGATRxgIZL+ne+xSqQOzcK6ZrUCOgO6yHTP1BuHuuos+cEXRDQ8ZeenT7u6wUfSZ2Xx+SJNHElnXFi/GjrqLJm4XW2XHjrdv9AVTz18RzmPwzuYCAPaYjOzWTeneHWwgLyK1ESxYIpsvozuOpJUiJSdCyo+Z1YfsYF3D8o5R9qQ6v8=";
    static final String ROGUE_CERT = "MIIDATCCAemgAwIBAgIUfkxRnKsxLxsnYk5IY2cIMbwGMpwwDQYJKoZIhvcNAQELBQAwEDEOMAwGA1UEAwwFUm9ndWUwHhcNMjYxMDE5MDQwMjE1WhcNNDYxMDE0MDQwMjE1WjAQMQ4wDAYDVQQDDAVSb2d1ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAPm3hyXs4EWOYxgJF4YRhnKTMdL7cAfz3GgIo/b3a3hk1YQo5EelqgkfefmdXCPztYgTpW7TRbx4jvxt6pQAl9XnNYBSBiFn6AmuQKaHX1gkGLfaPbtdcJgcy1PKMLQ3FLh/n7AwtlZb9ybX41sncvAgW0MKcOIlH4DG48Rgt78zzxe7Uzo0InJjzrac7/IqDBowfLyEBbgRXKTHvyn2mr6J275B7LtRd3G6VzLCghJMBvKgoXtTeRSLRNhrss67cfAxYq21HFGJ8vCDgzBUVly8QDL1BDfbK5xVCmPeBPq/9r8UDQclbKgZLNx5f/wc9zx1/B933JN/OWW6xoK1A+MCAwEAAaNTMFEwHQYDVR0OBBYEFMnTBNpUzrydQyAd/imKaewZ7usnMB8GA1UdIwQYMBaAFMnTBNpUzrydQyAd/imKaewZ7usnMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADJVdz6dQs2nNv9S3sVIx3Tf1uBrs0wtA4xOIqlBhsWpzrFlGSZZoPIi3G3P7vm9NOC/M/Z+TDzpZqxzuXkrpHot4vB968FeQD8jyyVUMzqzdCCzU4vcIMUqajKWa5tYBKyt6UjQecoY+5cPXZP494299bgDYJAh3FWJcsaVrpDbfku0heJs0dBj6mqbu+YDw3heGWGjYXpY+8slNaaFbNbdWhoL2vskPffjOU1PHEwgZV8cx4f2/LkeX7SQXjw74ZKcIOe4/SMen3Er+4OsFHgUXocZ5LiIHa4bBnMll5HBUEK/VSf0Bm2O+eCOJtQ47sVwMBswF8/P+EzXR4DmoSg=";
    static final String ROGUE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD5t4cl7OBFjmMYCReGEYZykzHS+3AH89xoCKP292t4ZNWEKORHpaoJH3n5nVwj87WIE6Vu00W8eI78beqUAJfV5zWAUgYhZ+gJrkCmh19YJBi32j27XXCYHMtTyjC0NxS4f5+wMLZWW/cm1+NbJ3LwIFtDCnDiJR+AxuPEYLe/M88Xu1M6NCJyY862nO/yKgwaMHy8hAW4EVykx78p9pq+idu+Qey7UXdxulcywoISTAbyoKF7U3kUi0TYa7LOu3HwMWKttRxRifLwg4MwVFZcvEAy9QQ32yucVQpj3gT6v/a/FA0HJWyoGSzceX/8HPc8dfwfd9yTfzllusaCtQPjAgMBAAECggEAZ2l9alNCPQvONbHuL/Mp/mC+jWTa7DAs4lhRroCf3OfFFEnq31DXrZd/Z8Om2KygTI32QIL1qisy646JSmlEd6ycF1vgdg0Vpoym8U+2SEYKhni2f9UeP6ANA9IDgV4eEpmng8todzL2kvtsh986Vf7QTF8SiFRfS3ER+c0jyuXUPtX4Rrf/5TaxFOvgI2KbWGGCAJ8spxZNWfXzvPQQdcEGCs2Tykpe+HAEXkEyq85xazWWzHUAIv769+3opmO01aEjnsvbzlYjSlaV9HJjp1rsPfPebiHFpC8i/Km3p8jBrCx4P7ohIRrHrRb4ycpi+yyBWS62e4ZYGtr8jZPs4QKBgQD+FDNUAa8ttlI4/+/K5bwpoBafc9gyV3Kn3AecnaUoX4vmoF0qiexoY6r7DP9vAvy/OBuP2o4Aj9WnjyGhUz0m0t8EqmW7SOwRZ4Bqtrh6rVt8rRFXKtqgBK+tZzhEMmI3ZVUBnIk9U4/2HYRfX3FAcKY1YO9MbylzFHrZaY3B1QKBgQD7muJejwhFPViSUk4ERJVwIEHWBQJ66pJdP1foaoOAY7tnNvzhqUf+naIfh/81MjzH4f66544nDwl4ZXcwMSlLXbVvaD5Wj8k3vpJHfwBB6u4Hni/GPFoLLYDZHxatQw+rdTs/q8kdE3O86oLTG1bQSX79qeT3umsYhTZ/yc1S1wKBgQD23i/FFuHbu4OGaH7YGIHLvAVfJpLQzxDTMkGfFblMUhG/ot8DHwG5p+WQil+BwFhNfzmge01wFFEWvkaB1jvbhhY1FU9FOqRMgNFQXns+j0QODqaDmFrWEdeT12/fK0ks4+NFbpAWKyP8kU91z2ldoFaOFMJN9NBERYjbx+h5hQKBgQDmBIDyxXxgIGc6zbe5OyILMdI+YLIVwSEx0lkBh27VD1giUij5/JJu+ZlfjisIbPcYHUmNgWd52ctkoA5IpokD95WJVhm3dRgVuElux3Jq0OXvXPR8HGyNT3o+BKfsmiRXMYOK//eKd2AV06bqbFH/dVF9gbZrq6eA8TgTt1/YXQKBgES1NqJYBHzCcvF8jtmm/Yvts155SB/yAFJV5I2/8VrCSmVHuf+DeX6fSDyuzohuxig21NYbU59K0MOD0em/3mWWeadr25G7j5YeZl+1n3H8N0bkuWbUfHrabni/2urjWmlM9OHrmT5R8pajzvwj1+iaElDVPOZLOROAnUfhLGg7";

    private HttpServer server;
    private int port;
    private byte[] validJpegBytes;

    private String infoResponseBody = "{\"status\":\"Ready\",\"bioType\":\"Face\",\"deviceId\":\"dev-mds-01\",\"deviceCode\":\"D001\",\"env\":\"Target\",\"purpose\":\"Registration\"}";
    private int infoResponseCode = 200;

    private String captureResponseBody = "";
    private int captureResponseCode = 200;

    private byte[] streamResponseBody = new byte[0];
    private int streamResponseCode = 200;

    @BeforeEach
    void setUp() throws Exception {
        // Create 4x4 test JPEG
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_3BYTE_BGR);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        validJpegBytes = baos.toByteArray();
        streamResponseBody = validJpegBytes;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/info", exchange -> {
            byte[] bytes = infoResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(infoResponseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/capture", exchange -> {
            byte[] bytes = captureResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(captureResponseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/stream", exchange -> {
            exchange.sendResponseHeaders(streamResponseCode, streamResponseBody.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(streamResponseBody);
            }
        });

        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String sign(String certB64, String keyB64, String payloadJson) throws Exception {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("alg", "RS256");
        h.put("typ", "JWT");
        h.put("x5c", Collections.singletonList(certB64));
        String head = Base64.getUrlEncoder().withoutPadding().encodeToString(MiniJson.write(h).getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        PrivateKey k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyB64)));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k);
        s.update((head + "." + body).getBytes(StandardCharsets.US_ASCII));
        return head + "." + body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private SbiJwtVerifier strictVerifier() throws Exception {
        java.security.cert.X509Certificate root = SbiJwtVerifier.parseCertificate(Base64.getDecoder().decode(ROOT_CERT));
        return new SbiJwtVerifier(Collections.singletonList(root), () -> Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void openSucceedsAndDiscoversReadyDevice() throws Exception {
        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        assertFalse(adapter.isOpen());
        adapter.open();
        assertTrue(adapter.isOpen());
        assertEquals("dev-mds-01", adapter.deviceInfo().getDeviceId());
        assertEquals("D001", adapter.deviceInfo().getDeviceCode());
        assertEquals("Face", adapter.deviceInfo().getBioType());
        adapter.close();
        assertFalse(adapter.isOpen());
    }

    @Test
    void openFailsWhenDeviceStatusNotReady() {
        infoResponseBody = "{\"status\":\"Busy\",\"bioType\":\"Face\"}";
        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, SbiJwtVerifier.disabledForDevelopment());
        DeviceException e = assertThrows(DeviceException.class, adapter::open);
        assertTrue(e.getMessage().startsWith("MDS_DEVICE_NOT_READY"), e.getMessage());
    }

    @Test
    void openFailsWhenBioTypeNotFace() {
        infoResponseBody = "{\"status\":\"Ready\",\"bioType\":\"Finger\"}";
        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, SbiJwtVerifier.disabledForDevelopment());
        DeviceException e = assertThrows(DeviceException.class, adapter::open);
        assertTrue(e.getMessage().startsWith("MDS_BAD_DEVICE"), e.getMessage());
    }

    @Test
    void captureFinalSucceedsWithSignedJwtAndEchosNonce() throws Exception {
        String boundTx = "sess-1:nonce-xyz";
        String wireId = SbiIntentDeviceAdapter.wireTransactionId(boundTx);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bioType", "Face");
        payload.put("transactionId", wireId);
        payload.put("timestamp", "2026-10-19T10:00:00Z");
        payload.put("bioValue", Base64.getUrlEncoder().withoutPadding().encodeToString(validJpegBytes));
        payload.put("deviceCode", "D001");

        String jwt = sign(DEVICE_CERT, DEVICE_KEY, MiniJson.write(payload));
        Map<String, Object> respMap = new LinkedHashMap<>();
        Map<String, Object> bioItem = new LinkedHashMap<>();
        bioItem.put("data", jwt);
        respMap.put("biometrics", Collections.singletonList(bioItem));
        captureResponseBody = MiniJson.write(respMap);

        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        adapter.open();

        FaceFrame frame = adapter.captureFinal(boundTx);
        assertNotNull(frame);
        assertEquals(4, frame.widthPx());
        assertEquals(4, frame.heightPx());
        assertNotNull(frame.pixelData());
        assertEquals(4 * 4 * 3, frame.pixelData().length);
        assertArrayEquals(validJpegBytes, frame.encodedBytes());
        assertEquals(boundTx, adapter.lastReturnedTransactionId());
        assertEquals("MDS_HTTP", adapter.captureMode());
    }

    @Test
    void captureFinalRejectsForgedJwt() throws Exception {
        String boundTx = "sess-1:nonce-xyz";
        String wireId = SbiIntentDeviceAdapter.wireTransactionId(boundTx);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bioType", "Face");
        payload.put("transactionId", wireId);
        payload.put("timestamp", "2026-10-19T10:00:00Z");
        payload.put("bioValue", Base64.getUrlEncoder().withoutPadding().encodeToString(validJpegBytes));

        String rogueJwt = sign(ROGUE_CERT, ROGUE_KEY, MiniJson.write(payload));
        captureResponseBody = "{\"data\":\"" + rogueJwt + "\"}";

        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        adapter.open();

        DeviceException e = assertThrows(DeviceException.class, () -> adapter.captureFinal(boundTx));
        assertTrue(e.getMessage().startsWith("MDS_SIGNATURE_INVALID"), e.getMessage());
    }

    @Test
    void captureFinalRejectsMismatchedNonce() throws Exception {
        String boundTx = "sess-1:nonce-xyz";
        String otherWireId = SbiIntentDeviceAdapter.wireTransactionId("sess-1:nonce-other");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bioType", "Face");
        payload.put("transactionId", otherWireId);
        payload.put("timestamp", "2026-10-19T10:00:00Z");
        payload.put("bioValue", Base64.getUrlEncoder().withoutPadding().encodeToString(validJpegBytes));

        String jwt = sign(DEVICE_CERT, DEVICE_KEY, MiniJson.write(payload));
        captureResponseBody = "{\"data\":\"" + jwt + "\"}";

        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        adapter.open();

        DeviceException e = assertThrows(DeviceException.class, () -> adapter.captureFinal(boundTx));
        assertTrue(e.getMessage().startsWith("MDS_NONCE_MISMATCH"), e.getMessage());
    }

    @Test
    void captureFrameStreamsFromEndpoint() throws Exception {
        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        adapter.open();

        FaceFrame frame = adapter.captureFrame();
        assertNotNull(frame);
        assertEquals(4, frame.widthPx());
        assertEquals(4, frame.heightPx());
        assertNotNull(frame.pixelData());
        assertEquals(4 * 4 * 3, frame.pixelData().length);
    }

    @Test
    void captureFrameFailsWhenStreamReturns404() throws Exception {
        streamResponseCode = 404;
        MdsHttpDeviceAdapter adapter = new MdsHttpDeviceAdapter("127.0.0.1", port, strictVerifier());
        adapter.open();

        DeviceException e = assertThrows(DeviceException.class, adapter::captureFrame);
        assertTrue(e.getMessage().startsWith("MDS_STREAM_UNSUPPORTED"), e.getMessage());
    }
}
