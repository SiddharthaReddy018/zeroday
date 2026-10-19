package liveness.android.glue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import liveness.android.SbiIntentDeviceAdapter;

/**
 * Decodes JPEG/PNG to packed RGB for the same-face embedding. SBI bioValue is an ISO 19794-5 record, so wrap this
 * in {@code liveness.android.IsoFaceRgbDecoder} (which unwraps the record). BitmapFactory cannot decode JPEG 2000:
 * pass a Jpeg2000Decoder (e.g. OpenJPEG via JNI) to IsoFaceRgbDecoder if your SBI returns it. Unsupported input
 * throws, so the binder fails CLOSED (EMBEDDING_FAILED), never open.
 */
public final class AndroidRgbDecoder implements SbiIntentDeviceAdapter.RgbDecoder {
    @Override
    public Decoded decode(byte[] encoded) {
        Bitmap bmp = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        if (bmp == null) throw new IllegalArgumentException("unsupported image encoding");
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            byte[] rgb = new byte[w * h * 3];
            for (int i = 0; i < px.length; i++) {
                rgb[i * 3] = (byte) ((px[i] >> 16) & 0xFF);
                rgb[i * 3 + 1] = (byte) ((px[i] >> 8) & 0xFF);
                rgb[i * 3 + 2] = (byte) (px[i] & 0xFF);
            }
            return new Decoded(rgb, w, h);
        } finally {
            bmp.recycle();
        }
    }
}
