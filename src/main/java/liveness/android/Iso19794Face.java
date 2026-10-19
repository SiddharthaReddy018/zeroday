package liveness.android;

/**
 * Extracts the face image from an ISO/IEC 19794-5 facial record as returned in SBI bioValue.
 * <p>
 * The 2005 layout ("FAC\0" + "010\0") is parsed by offsets: 14-byte general header, then per face a facial
 * information block (20 bytes + 8 bytes per feature point) and a 12-byte image information block, then the image.
 * Other versions (e.g. "030\0") fall back to scanning for a JPEG / JPEG 2000 signature after the header, because
 * their field layout is not reproduced here. Raw JPEG/JPEG 2000 payloads (no ISO wrapper) are also accepted.
 * Validated only against records built to the 2005 layout in unit tests, not against real SBI output.
 */
public final class Iso19794Face {

    public enum Format { JPEG, JPEG2000 }

    public static final class Image {
        public final byte[] data;
        public final Format format;
        public final int width;   // 0 if unknown (fallback path)
        public final int height;

        Image(byte[] data, Format format, int width, int height) {
            this.data = data;
            this.format = format;
            this.width = width;
            this.height = height;
        }
    }

    public static final class FormatException extends Exception {
        public FormatException(String m) { super(m); }
    }

    private Iso19794Face() {}

    public static Image extract(byte[] rec) throws FormatException {
        if (rec == null || rec.length < 4) throw new FormatException("TOO_SHORT");
        Format raw = sniff(rec, 0);
        if (raw != null) return new Image(rec, raw, 0, 0);
        if (!(rec[0] == 'F' && rec[1] == 'A' && rec[2] == 'C' && rec[3] == 0)) throw new FormatException("NOT_ISO_FACE_RECORD");
        if (rec.length < 14) throw new FormatException("TOO_SHORT");

        boolean v2005 = rec[4] == '0' && rec[5] == '1' && rec[6] == '0' && rec[7] == 0;
        if (v2005) {
            try {
                int faces = u16(rec, 12);
                if (faces < 1) throw new FormatException("NO_FACE_RECORD");
                int p = 14;                               // first facial record
                int blockLen = (int) u32(rec, p);
                int featurePoints = u16(rec, p + 4);
                int imgInfo = p + 20 + 8 * featurePoints; // facial information + feature points
                int imageType = rec[imgInfo + 1] & 0xFF;  // 0 = JPEG, 1 = JPEG 2000
                int w = u16(rec, imgInfo + 2);
                int h = u16(rec, imgInfo + 4);
                int imgStart = imgInfo + 12;
                int imgEnd = Math.min(rec.length, p + blockLen);
                if (blockLen <= 0 || imgStart >= imgEnd) throw new FormatException("BAD_LENGTH");
                byte[] img = java.util.Arrays.copyOfRange(rec, imgStart, imgEnd);
                Format sniffed = sniff(img, 0);
                if (sniffed == null) throw new FormatException("IMAGE_SIGNATURE_MISMATCH");
                return new Image(img, sniffed, w, h);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new FormatException("TRUNCATED");
            }
        }
        for (int i = 14; i < rec.length - 4; i++) {
            Format f = sniff(rec, i);
            if (f != null) return new Image(java.util.Arrays.copyOfRange(rec, i, rec.length), f, 0, 0);
        }
        throw new FormatException("NO_IMAGE_FOUND");
    }

    private static Format sniff(byte[] b, int o) {
        if (b.length - o >= 3 && (b[o] & 0xFF) == 0xFF && (b[o + 1] & 0xFF) == 0xD8 && (b[o + 2] & 0xFF) == 0xFF) return Format.JPEG;
        if (b.length - o >= 12 && b[o] == 0 && b[o + 1] == 0 && b[o + 2] == 0 && b[o + 3] == 0x0C
                && b[o + 4] == 'j' && b[o + 5] == 'P' && b[o + 6] == ' ' && b[o + 7] == ' ') return Format.JPEG2000;
        if (b.length - o >= 4 && (b[o] & 0xFF) == 0xFF && (b[o + 1] & 0xFF) == 0x4F
                && (b[o + 2] & 0xFF) == 0xFF && (b[o + 3] & 0xFF) == 0x51) return Format.JPEG2000;
        return null;
    }

    private static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }

    private static long u32(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}
