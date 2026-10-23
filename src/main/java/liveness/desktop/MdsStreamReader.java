package liveness.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MdsStreamReader {
    private final String streamUrl;
    private HttpURLConnection connection;
    private InputStream inputStream;

    public MdsStreamReader(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public void open() throws IOException {
        URL url = new URL(streamUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(40000);
        inputStream = connection.getInputStream();
    }

    public byte[] readNextFrame() throws IOException {
        if (inputStream == null) {
            open();
        }

        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        int prev = -1;
        int current;
        boolean inFrame = false;

        while ((current = inputStream.read()) != -1) {
            if (!inFrame) {
                // Look for JPEG SOI (Start of Image) marker: FF D8
                if (prev == 0xFF && current == 0xD8) {
                    inFrame = true;
                    frameBuffer.write(prev);
                    frameBuffer.write(current);
                }
            } else {
                frameBuffer.write(current);
                // Look for JPEG EOI (End of Image) marker: FF D9
                if (prev == 0xFF && current == 0xD9) {
                    return frameBuffer.toByteArray();
                }
            }
            prev = current;
        }
        return null; // Stream ended
    }

    public void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignore
            }
            inputStream = null;
        }
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }
}
