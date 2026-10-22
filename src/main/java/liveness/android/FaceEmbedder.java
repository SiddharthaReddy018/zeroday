package liveness.android;

/**
 * Produces an identity embedding for a face image (RGB, row-major). Plug an on-device
 * face-recognition model here; the repo ships none. Must return the same vector length always.
 */
public interface FaceEmbedder {
    float[] embed(byte[] rgb, int width, int height);

    /** True only for production-grade deep models (e.g. SFaceEmbedder). Test grids return false. */
    default boolean productionGrade() {
        return false;
    }
}

