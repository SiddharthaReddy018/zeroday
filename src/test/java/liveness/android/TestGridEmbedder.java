package liveness.android;

/**
 * Test-only grid embedder (formerly ReferenceFaceEmbedder).
 * 8x8 spatial grid of mean luminance + gradient over the frame.
 * For testing/simulation only; not an identity model.
 */
public final class TestGridEmbedder implements FaceEmbedder {

    private static final int GRID_SIZE = 8;
    private static final int EMBEDDING_DIM = 128;

    @Override
    public float[] embed(byte[] rgb, int width, int height) {
        if (rgb == null || rgb.length == 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid image buffer for face embedding");
        }
        if (rgb.length < width * height * 3) {
            throw new IllegalArgumentException("RGB buffer too small for specified dimensions");
        }

        float[] embedding = new float[EMBEDDING_DIM];
        int cellW = Math.max(1, width / GRID_SIZE);
        int cellH = Math.max(1, height / GRID_SIZE);

        int idx = 0;
        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int startX = gx * cellW;
                int startY = gy * cellH;
                int endX = Math.min(width, startX + cellW);
                int endY = Math.min(height, startY + cellH);

                double lumaSum = 0.0;
                double gradSum = 0.0;
                int count = 0;

                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        int pixelOffset = (y * width + x) * 3;
                        int r = rgb[pixelOffset] & 0xFF;
                        int g = rgb[pixelOffset + 1] & 0xFF;
                        int b = rgb[pixelOffset + 2] & 0xFF;
                        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
                        lumaSum += luma;

                        if (x + 1 < endX) {
                            int rNext = rgb[pixelOffset + 3] & 0xFF;
                            int gNext = rgb[pixelOffset + 4] & 0xFF;
                            int bNext = rgb[pixelOffset + 5] & 0xFF;
                            double lumaNext = 0.299 * rNext + 0.587 * gNext + 0.114 * bNext;
                            gradSum += Math.abs(lumaNext - luma);
                        }
                        count++;
                    }
                }

                if (count > 0) {
                    embedding[idx++] = (float) (lumaSum / (count * 255.0));
                    embedding[idx++] = (float) (gradSum / (count * 255.0));
                }
            }
        }

        // L2-normalize
        double normSq = 0.0;
        for (float v : embedding) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        if (norm > 1e-6) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) (embedding[i] / norm);
            }
        }

        return embedding;
    }
}
