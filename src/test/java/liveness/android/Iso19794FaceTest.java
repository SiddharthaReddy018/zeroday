package liveness.android;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class Iso19794FaceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6};
    private static final byte[] JP2 = {0, 0, 0, 0x0C, 'j', 'P', ' ', ' ', 0x0D, 0x0A, (byte) 0x87, 0x0A, 9, 9, 9};

    private static void u16(ByteArrayOutputStream o, int v) { o.write(v >> 8); o.write(v); }
    private static void u32(ByteArrayOutputStream o, long v) { o.write((int) (v >> 24)); o.write((int) (v >> 16)); o.write((int) (v >> 8)); o.write((int) v); }

    /** ISO/IEC 19794-5:2005 record: 14-byte header, 20-byte facial info (+8/feature point), 12-byte image info, image. */
    private static byte[] record2005(byte[] image, int imageType, int w, int h, int featurePoints) {
        ByteArrayOutputStream face = new ByteArrayOutputStream();
        int blockLen = 20 + 8 * featurePoints + 12 + image.length;
        u32(face, blockLen);
        u16(face, featurePoints);
        for (int i = 0; i < 14; i++) face.write(0);            // gender..pose uncertainty (to 20 bytes)
        for (int i = 0; i < 8 * featurePoints; i++) face.write(0x55);
        face.write(1);                                          // facial image type
        face.write(imageType);                                  // 0 JPEG, 1 JPEG 2000
        u16(face, w);
        u16(face, h);
        face.write(0); face.write(0); u16(face, 0); u16(face, 0); // colour space, source, device, quality
        face.write(image, 0, image.length);

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write('F'); o.write('A'); o.write('C'); o.write(0);
        o.write('0'); o.write('1'); o.write('0'); o.write(0);
        u32(o, 14 + face.size());
        u16(o, 1);
        byte[] f = face.toByteArray();
        o.write(f, 0, f.length);
        return o.toByteArray();
    }

    @Test
    void extractsJpegFromA2005Record() throws Exception {
        Iso19794Face.Image img = Iso19794Face.extract(record2005(JPEG, 0, 640, 480, 0));
        assertEquals(Iso19794Face.Format.JPEG, img.format);
        assertArrayEquals(JPEG, img.data);
        assertEquals(640, img.width);
        assertEquals(480, img.height);
    }

    @Test
    void extractsJpeg2000AndSkipsFeaturePoints() throws Exception {
        Iso19794Face.Image img = Iso19794Face.extract(record2005(JP2, 1, 413, 531, 3));
        assertEquals(Iso19794Face.Format.JPEG2000, img.format);
        assertArrayEquals(JP2, img.data);
        assertEquals(413, img.width);
    }

    @Test
    void acceptsRawJpegAndRawJp2WithoutWrapper() throws Exception {
        assertEquals(Iso19794Face.Format.JPEG, Iso19794Face.extract(JPEG).format);
        assertEquals(Iso19794Face.Format.JPEG2000, Iso19794Face.extract(JP2).format);
    }

    @Test
    void otherVersionsFallBackToSignatureScan() throws Exception {
        byte[] rec = record2005(JPEG, 0, 10, 10, 0);
        rec[4] = '0'; rec[5] = '3'; rec[6] = '0'; // pretend 2011 ("030")
        Iso19794Face.Image img = Iso19794Face.extract(rec);
        assertEquals(Iso19794Face.Format.JPEG, img.format);
        assertEquals(0, img.width, "dimensions unknown on the fallback path");
    }

    @Test
    void rejectsGarbageTruncationAndMismatchedImage() {
        for (byte[] bad : new byte[][]{null, new byte[0], {1, 2, 3}, "NOPE-not-iso-record".getBytes()}) {
            assertThrows(Iso19794Face.FormatException.class, () -> Iso19794Face.extract(bad));
        }
        byte[] rec = record2005(JPEG, 0, 10, 10, 0);
        byte[] cut = java.util.Arrays.copyOf(rec, 30);
        assertThrows(Iso19794Face.FormatException.class, () -> Iso19794Face.extract(cut));
        byte[] notImage = record2005(new byte[]{1, 2, 3, 4, 5, 6}, 0, 10, 10, 0);
        assertEquals("IMAGE_SIGNATURE_MISMATCH", assertThrows(Iso19794Face.FormatException.class, () -> Iso19794Face.extract(notImage)).getMessage());
    }

    @Test
    void decoderUnwrapsThenDelegatesAndFailsClosedWithoutJp2Decoder() {
        SbiIntentDeviceAdapter.RgbDecoder jpegDec = b -> new SbiIntentDeviceAdapter.RgbDecoder.Decoded(b, 1, 1);
        IsoFaceRgbDecoder noJp2 = new IsoFaceRgbDecoder(jpegDec, null);
        assertArrayEquals(JPEG, noJp2.decode(record2005(JPEG, 0, 10, 10, 0)).rgb);
        assertThrows(IllegalArgumentException.class, () -> noJp2.decode(record2005(JP2, 1, 10, 10, 0)));
        IsoFaceRgbDecoder withJp2 = new IsoFaceRgbDecoder(jpegDec, b -> new SbiIntentDeviceAdapter.RgbDecoder.Decoded(b, 2, 2));
        assertEquals(2, withJp2.decode(record2005(JP2, 1, 10, 10, 0)).width);
        assertThrows(IllegalArgumentException.class, () -> withJp2.decode(new byte[]{1, 2, 3, 4}));
    }
}
