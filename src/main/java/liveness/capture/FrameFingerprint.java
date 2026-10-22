package liveness.capture;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class FrameFingerprint {
    private final long pHash;
    private final long timestampMs;
    private final int sequenceNum;
    private final int widthPx;
    private final byte[] contentDigest;

    public FrameFingerprint(long pHash, long timestampMs, int sequenceNum, int widthPx) {
        this(pHash, timestampMs, sequenceNum, widthPx, ByteBuffer.allocate(8).putLong(pHash).array());
    }

    public FrameFingerprint(long pHash, long timestampMs, int sequenceNum, int widthPx, byte[] contentDigest) {
        this.pHash = pHash;
        this.timestampMs = timestampMs;
        this.sequenceNum = sequenceNum;
        this.widthPx = widthPx;
        this.contentDigest = contentDigest != null ? Arrays.copyOf(contentDigest, contentDigest.length) : null;
    }

    public byte[] getContentDigest() {
        return contentDigest != null ? Arrays.copyOf(contentDigest, contentDigest.length) : null;
    }

    public boolean hasContentDigest() {
        return contentDigest != null;
    }

    public long getPHash() {
        return pHash;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public int getSequenceNum() {
        return sequenceNum;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public int hammingDistance(FrameFingerprint other) {
        long xor = this.pHash ^ other.pHash;
        return Long.bitCount(xor);
    }

    private static final double[][] COS = new double[32][32];
    static {
        for (int u = 0; u < 32; u++) {
            for (int x = 0; x < 32; x++) {
                COS[u][x] = Math.cos((2 * x + 1) * u * Math.PI / 64.0);
            }
        }
    }

    public static long computePHash(byte[] grayscalePixels, int width, int height) {
        if (grayscalePixels == null || width < 32 || height < 32 || grayscalePixels.length < width * height) {
            return 0L;
        }

        // 1. Downsample whole frame to 32x32
        double[][] img = new double[32][32];
        double xRatio = (double) width / 32.0;
        double yRatio = (double) height / 32.0;

        for (int y = 0; y < 32; y++) {
            int py = Math.min(height - 1, (int) (y * yRatio));
            int rowOffset = py * width;
            for (int x = 0; x < 32; x++) {
                int px = Math.min(width - 1, (int) (x * xRatio));
                img[y][x] = grayscalePixels[rowOffset + px] & 0xFF;
            }
        }

        // 2. 2D DCT (separable: rows then columns)
        double[][] rowDct = new double[32][32];
        for (int y = 0; y < 32; y++) {
            for (int u = 0; u < 32; u++) {
                double sum = 0.0;
                for (int x = 0; x < 32; x++) {
                    sum += img[y][x] * COS[u][x];
                }
                rowDct[y][u] = sum;
            }
        }

        // Extract top-left 8x8 lowest AC/DC frequencies across the whole image
        double[][] dct8x8 = new double[8][8];
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                double sum = 0.0;
                for (int y = 0; y < 32; y++) {
                    sum += rowDct[y][u] * COS[v][y];
                }
                dct8x8[v][u] = sum;
            }
        }

        // 3. Compute mean of top-left 8x8 low frequencies excluding DC term (0,0)
        double sumAc = 0.0;
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                if (v != 0 || u != 0) {
                    sumAc += dct8x8[v][u];
                }
            }
        }
        double meanAc = sumAc / 63.0;

        // 4. Build 64 bits thresholded against whole-image frequency mean
        long hash = 0L;
        for (int i = 0; i < 64; i++) {
            int v = i / 8;
            int u = i % 8;
            if (dct8x8[v][u] > meanAc) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    public static byte[] computeDownsampledGray(byte[] grayscalePixels, int width, int height) {
        if (grayscalePixels == null || width < 32 || height < 32 || grayscalePixels.length < width * height) {
            return new byte[0];
        }
        byte[] downsampled = new byte[32 * 32];
        double xRatio = (double) width / 32.0;
        double yRatio = (double) height / 32.0;

        for (int y = 0; y < 32; y++) {
            int py = Math.min(height - 1, (int) (y * yRatio));
            int rowOffset = py * width;
            for (int x = 0; x < 32; x++) {
                int px = Math.min(width - 1, (int) (x * xRatio));
                downsampled[y * 32 + x] = grayscalePixels[rowOffset + px];
            }
        }
        return downsampled;
    }

    public static byte[] computeContentDigest(byte[] grayscalePixels, int width, int height) {
        byte[] downsampled = computeDownsampledGray(grayscalePixels, width, height);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(downsampled);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 message digest unavailable", e);
        }
    }
}

