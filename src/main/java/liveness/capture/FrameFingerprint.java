package liveness.capture;

public class FrameFingerprint {
    private final long pHash;
    private final long timestampMs;
    private final int sequenceNum;
    private final int widthPx;

    public FrameFingerprint(long pHash, long timestampMs, int sequenceNum, int widthPx) {
        this.pHash = pHash;
        this.timestampMs = timestampMs;
        this.sequenceNum = sequenceNum;
        this.widthPx = widthPx;
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

    public static long computePHash(byte[] grayscalePixels, int width, int height) {
        if (width < 32 || height < 32) return 0L;
        
        int[] downsampled = new int[32 * 32];
        float xRatio = (float) width / 32;
        float yRatio = (float) height / 32;
        
        long sum = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int px = (int) (x * xRatio);
                int py = (int) (y * yRatio);
                int idx = py * width + px;
                int val = grayscalePixels[idx] & 0xFF;
                downsampled[y * 32 + x] = val;
                sum += val;
            }
        }
        
        long mean = sum / (32 * 32);
        long hash = 0;
        
        for (int i = 0; i < 64; i++) {
            int y = i / 8;
            int x = i % 8;
            if (downsampled[y * 32 + x] >= mean) {
                hash |= (1L << i);
            }
        }
        return hash;
    }
}
