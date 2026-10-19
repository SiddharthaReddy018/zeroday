package liveness.android.glue;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

import liveness.android.CameraXDeviceAdapter;
import liveness.android.YuvFrame;

/**
 * CameraX analyzer feeding CameraXDeviceAdapter. Copies the three YUV_420_888 planes and ALWAYS closes the
 * ImageProxy in finally, so the camera pipeline never stalls or leaks buffers.
 * <p>
 * Timestamps are wall-clock epoch ms taken at analyzer entry (not the sensor clock), because the session
 * compares them with the SBI response timestamp (epoch) in the cross-stream time-gap check.
 * Bind with ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST and OUTPUT_IMAGE_FORMAT_YUV_420_888.
 */
public final class CameraXAnalyzer implements ImageAnalysis.Analyzer {

    private final CameraXDeviceAdapter adapter;

    public CameraXAnalyzer(CameraXDeviceAdapter adapter) { this.adapter = adapter; }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!adapter.isOpen()) return;
            ImageProxy.PlaneProxy[] p = image.getPlanes();
            if (p.length < 3) return;
            if (p[1].getRowStride() != p[2].getRowStride() || p[1].getPixelStride() != p[2].getPixelStride()) {
                return; // unsupported chroma layout: drop the frame rather than mis-decode it
            }
            YuvFrame f = new YuvFrame(image.getWidth(), image.getHeight(),
                    copy(p[0].getBuffer()), copy(p[1].getBuffer()), copy(p[2].getBuffer()),
                    p[0].getRowStride(), p[1].getRowStride(), p[1].getPixelStride(),
                    image.getImageInfo().getRotationDegrees(), System.currentTimeMillis());
            adapter.onYuvFrame(f);
        } catch (RuntimeException ignored) {
            // a bad frame must never crash the camera thread; the engine's timeout handles starvation
        } finally {
            image.close();
        }
    }

    private static byte[] copy(ByteBuffer b) {
        ByteBuffer d = b.duplicate();
        d.rewind();
        byte[] out = new byte[d.remaining()];
        d.get(out);
        return out;
    }
}
