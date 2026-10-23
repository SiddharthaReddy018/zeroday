package liveness.desktop;

import liveness.android.SbiIntentDeviceAdapter;

/**
 * Desktop-only JPEG decoder built on java.desktop (ImageIO / BufferedImage). It lives in its own package so the
 * Android source sets can exclude the whole package: java.awt and javax.imageio do not exist on Android. The
 * Android path decodes through AndroidRgbDecoder (BitmapFactory) instead.
 *
 * <p>Moved unchanged from IsoFaceRgbDecoder.desktopJpegDecoder().
 */
public final class DesktopJpegDecoder {

    private DesktopJpegDecoder() {}

    /** Platform/desktop ImageIO-based JPEG decoder. */
    public static SbiIntentDeviceAdapter.RgbDecoder create() {
        return encoded -> {
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(encoded));
                if (img == null) {
                    throw new IllegalArgumentException("Failed to decode image: unsupported or corrupted format");
                }
                int w = img.getWidth();
                int h = img.getHeight();
                byte[] rgb = new byte[w * h * 3];
                int[] pixels = new int[w * h];
                img.getRGB(0, 0, w, h, pixels, 0, w);
                for (int i = 0; i < pixels.length; i++) {
                    int p = pixels[i];
                    rgb[i * 3] = (byte) ((p >> 16) & 0xFF);
                    rgb[i * 3 + 1] = (byte) ((p >> 8) & 0xFF);
                    rgb[i * 3 + 2] = (byte) (p & 0xFF);
                }
                return new SbiIntentDeviceAdapter.RgbDecoder.Decoded(rgb, w, h);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("JPEG decode error", e);
            }
        };
    }
}
