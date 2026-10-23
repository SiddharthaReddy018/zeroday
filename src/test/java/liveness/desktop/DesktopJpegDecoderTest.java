package liveness.desktop;

import liveness.android.SbiIntentDeviceAdapter;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DesktopJpegDecoderTest {

    private static byte[] jpeg(int w, int h, int rgb) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(img, "jpg", out));
        return out.toByteArray();
    }

    @Test
    void decodesJpegToRgbWithCorrectDimensionsAndChannelOrder() throws Exception {
        SbiIntentDeviceAdapter.RgbDecoder.Decoded d = DesktopJpegDecoder.create().decode(jpeg(16, 8, 0xFF0000));
        assertEquals(16, d.width);
        assertEquals(8, d.height);
        assertEquals(16 * 8 * 3, d.rgb.length);
        int r = d.rgb[0] & 0xFF;
        int g = d.rgb[1] & 0xFF;
        int b = d.rgb[2] & 0xFF;
        assertTrue(r > 200, "red channel first, got " + r);
        assertTrue(g < 60 && b < 60, "g=" + g + " b=" + b);
    }

    @Test
    void undecodableInputThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> DesktopJpegDecoder.create().decode(new byte[]{1, 2, 3, 4}));
    }
}
