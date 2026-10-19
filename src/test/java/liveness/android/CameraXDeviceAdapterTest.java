package liveness.android;

import liveness.core.FaceFrame;
import liveness.device.DeviceException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static liveness.android.AndroidTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraXDeviceAdapterTest {

    private CameraXDeviceAdapter adapter(int cap, long timeoutMs) throws DeviceException {
        CameraXDeviceAdapter a = new CameraXDeviceAdapter("cam0", oneFace(), cap, timeoutMs);
        a.open();
        return a;
    }

    @Test
    void convertsYuvToFaceFrameWithMetadata() throws Exception {
        CameraXDeviceAdapter a = adapter(3, 500);
        assertTrue(a.onYuvFrame(texturedFrame(128, 96, 1000L, 0, 1)));
        FaceFrame f = a.captureFrame();
        assertEquals(128, f.widthPx());
        assertEquals(96, f.heightPx());
        assertEquals(128 * 96 * 3, f.pixelData().length);
        assertEquals(1000L, f.timestampMs());
        assertEquals(1, f.sequenceNum());
        assertEquals(1, f.faceCount());
        assertTrue(f.quality().isAcceptable());
        assertTrue(f.laplacianVariance() > 100.0, "textured frame must have high blur variance");
        assertTrue(f.brightnessMean() > 100 && f.brightnessMean() < 160);
        assertNotEquals(0L, f.pHash(), "pHash must be computed for freeze/loop detection");
        assertEquals(64, f.faceBboxWidth());
    }

    @Test
    void neutralChromaGivesGrayRgb() throws Exception {
        CameraXDeviceAdapter a = adapter(3, 500);
        a.onYuvFrame(paddedGray(64, 64, 100, 0, 0, 1L));
        byte[] rgb = a.captureFrame().pixelData();
        for (int i = 0; i < rgb.length; i++) assertEquals(100, rgb[i] & 0xFF, "pixel byte " + i);
    }

    @Test
    void rowStridePaddingIsIgnored() throws Exception {
        CameraXDeviceAdapter a = adapter(3, 500);
        a.onYuvFrame(paddedGray(64, 48, 200, 16, 0, 1L));
        byte[] rgb = a.captureFrame().pixelData();
        assertEquals(64 * 48 * 3, rgb.length);
        for (byte b : rgb) assertEquals(200, b & 0xFF, "padding garbage (0x7F) must not leak into pixels");
    }

    @Test
    void rotation90SwapsDimensionsAndMovesPixels() throws Exception {
        // 4x2 luma gradient, rotate 90 -> 2x4. Pixel (row0,col0) must land at (row0,col last).
        int w = 4, h = 2;
        byte[] y = {10, 20, 30, 40, 50, 60, 70, (byte) 80};
        byte[] uv = new byte[2];
        java.util.Arrays.fill(uv, (byte) 128);
        YuvFrame f = new YuvFrame(w, h, y, uv, uv, w, 2, 1, 90, 1L);
        assertEquals(h, YuvConverter.outputWidth(f));
        assertEquals(w, YuvConverter.outputHeight(f));
        byte[] rgb = YuvConverter.toRgb(f);
        // output is 2 wide x 4 high; src(row0,col0)=10 -> dst(row0, col h-1-0 = 1)
        assertEquals(10, rgb[(0 * 2 + 1) * 3] & 0xFF);
        // src(row1,col0)=50 -> dst(row0, col 0)
        assertEquals(50, rgb[(0 * 2 + 0) * 3] & 0xFF);
        // src(row0,col3)=40 -> dst(row3, col1)
        assertEquals(40, rgb[(3 * 2 + 1) * 3] & 0xFF);
    }

    @Test
    void sequenceIncrementsAndSignatureIsStableAcrossFrames() throws Exception {
        CameraXDeviceAdapter a = adapter(3, 500);
        a.onYuvFrame(texturedFrame(64, 64, 1L, 0, 1));
        a.onYuvFrame(texturedFrame(64, 64, 2L, 0, 2));
        FaceFrame f1 = a.captureFrame();
        FaceFrame f2 = a.captureFrame();
        assertEquals(1, f1.sequenceNum());
        assertEquals(2, f2.sequenceNum());
        // The session feeds signature to DeviceIdentityValidator; it must not change between frames.
        assertEquals(f1.signature(), f2.signature());
        assertEquals(f1.deviceId(), f2.deviceId());
        assertSame(f2, a.latestFrame());
    }

    @Test
    void differentContentGivesDifferentPHash() throws Exception {
        CameraXDeviceAdapter a = adapter(3, 500);
        a.onYuvFrame(texturedFrame(128, 128, 1L, 0, 1));
        a.onYuvFrame(texturedFrame(128, 128, 2L, 0, 2));
        assertNotEquals(a.captureFrame().pHash(), a.captureFrame().pHash());
    }

    @Test
    void boundedQueueDropsOldestWithoutBlocking() throws Exception {
        CameraXDeviceAdapter a = adapter(2, 200);
        for (int i = 1; i <= 5; i++) assertTrue(a.onYuvFrame(texturedFrame(64, 64, i, 0, i)));
        assertEquals(3, a.droppedFrames());
        assertEquals(4L, a.captureFrame().timestampMs());
        assertEquals(5L, a.captureFrame().timestampMs());
    }

    @Test
    void noFaceAndMultiFaceAreFlaggedNotAcceptable() throws Exception {
        CameraXDeviceAdapter none = new CameraXDeviceAdapter("c", noFace(), 2, 200);
        none.open();
        none.onYuvFrame(texturedFrame(64, 64, 1L, 0, 1));
        FaceFrame f = none.captureFrame();
        assertEquals(0, f.faceCount());
        assertFalse(f.quality().isAcceptable());

        CameraXDeviceAdapter multi = new CameraXDeviceAdapter("c",
                (rgb, w, h) -> new FaceDetector.Observation(2, 0, 0, 10, 10, 0, 0, 0, null), 2, 200);
        multi.open();
        multi.onYuvFrame(texturedFrame(64, 64, 1L, 0, 1));
        assertEquals(2, multi.captureFrame().faceCount());
        assertFalse(multi.latestFrame().quality().isAcceptable());
    }

    @Test
    void timesOutWhenNoFrameArrives() throws Exception {
        CameraXDeviceAdapter a = adapter(2, 50);
        DeviceException e = assertThrows(DeviceException.class, a::captureFrame);
        assertTrue(e.getMessage().contains("no frame"));
    }

    @Test
    void notOpenAndClosedBehaviour() throws Exception {
        CameraXDeviceAdapter a = new CameraXDeviceAdapter("c", oneFace());
        assertFalse(a.isOpen());
        assertThrows(DeviceException.class, a::captureFrame);
        assertFalse(a.onYuvFrame(texturedFrame(64, 64, 1L, 0, 1)), "frames before open are rejected");
        a.open();
        assertTrue(a.isOpen());
        a.onYuvFrame(texturedFrame(64, 64, 1L, 0, 1));
        a.close();
        a.close(); // idempotent
        assertFalse(a.isOpen());
        assertFalse(a.onYuvFrame(texturedFrame(64, 64, 2L, 0, 2)));
        assertThrows(DeviceException.class, a::captureFrame);
    }

    @Test
    void closeWakesBlockedConsumerPromptly() throws Exception {
        final CameraXDeviceAdapter a = adapter(2, 10000);
        final AtomicInteger failures = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try { a.captureFrame(); } catch (DeviceException e) { failures.incrementAndGet(); }
            done.countDown();
        });
        t.start();
        Thread.sleep(100);
        a.close();
        assertTrue(done.await(1, java.util.concurrent.TimeUnit.SECONDS), "consumer must wake without interrupt");
        assertEquals(1, failures.get());
    }

    @Test
    void producerConsumerKeepsOrderUnderConcurrency() throws Exception {
        final CameraXDeviceAdapter a = adapter(4, 1000);
        final int total = 200;
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= total; i++) {
                a.onYuvFrame(texturedFrame(32, 32, i, 0, i));
                if (i % 10 == 0) Thread.yield();
            }
        });
        producer.start();
        long last = 0;
        int got = 0;
        while (producer.isAlive() || got + a.droppedFrames() < total) {
            try {
                FaceFrame f = a.captureFrame();
                assertTrue(f.timestampMs() > last, "timestamps must stay strictly increasing");
                last = f.timestampMs();
                got++;
            } catch (DeviceException e) {
                break; // queue drained and producer finished
            }
        }
        producer.join();
        assertEquals(total, got + a.droppedFrames(), "every frame is either delivered or counted as dropped");
    }

    @Test
    void reportsConfiguredFrameInterval() {
        assertEquals(CameraXDeviceAdapter.DEFAULT_EXPECTED_INTERVAL_MS,
                new CameraXDeviceAdapter("c", oneFace()).expectedFrameIntervalMs());
        assertEquals(333L, new CameraXDeviceAdapter("c", oneFace(), 2, 100, 333L).expectedFrameIntervalMs());
    }
}
