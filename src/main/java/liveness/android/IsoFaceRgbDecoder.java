package liveness.android;

/**
 * RgbDecoder for SBI bioValue: unwraps the ISO 19794-5 record, then decodes JPEG with the platform decoder and
 * JPEG 2000 with an injected decoder (Android has none built in; wrap OpenJPEG or similar). Any failure throws,
 * and the cross-stream binder then fails closed.
 */
public final class IsoFaceRgbDecoder implements SbiIntentDeviceAdapter.RgbDecoder {

    /** JPEG 2000 codestream/JP2 -> RGB. Optional. */
    public interface Jpeg2000Decoder { Decoded decode(byte[] jp2); }

    private final SbiIntentDeviceAdapter.RgbDecoder jpeg;
    private final Jpeg2000Decoder jp2; // nullable

    public IsoFaceRgbDecoder(SbiIntentDeviceAdapter.RgbDecoder jpeg, Jpeg2000Decoder jp2) {
        this.jpeg = jpeg;
        this.jp2 = jp2;
    }

    @Override
    public Decoded decode(byte[] encoded) {
        Iso19794Face.Image img;
        try {
            img = Iso19794Face.extract(encoded);
        } catch (Iso19794Face.FormatException e) {
            throw new IllegalArgumentException("ISO face record: " + e.getMessage());
        }
        if (img.format == Iso19794Face.Format.JPEG) return jpeg.decode(img.data);
        if (jp2 == null) throw new IllegalArgumentException("JPEG 2000 decoder not configured");
        return jp2.decode(img.data);
    }

    /** Platform/desktop ImageIO-based JPEG decoder. */
    public static SbiIntentDeviceAdapter.RgbDecoder desktopJpegDecoder() {
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
