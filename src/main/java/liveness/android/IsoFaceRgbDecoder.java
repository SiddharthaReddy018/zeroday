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
}
