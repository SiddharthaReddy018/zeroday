package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The in-memory SFace constructor used by the Android bootstrap must pin the model hash and fail closed. */
class SFaceEmbedderPinTest {

    private static final String ZERO_PIN = "0000000000000000000000000000000000000000000000000000000000000000";

    private static String sha(byte[] b) {
        return liveness.util.Sha256.hex(liveness.util.Sha256.digest(b));
    }

    @Test
    void nullBytesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new SFaceEmbedder((byte[]) null, ZERO_PIN, null));
    }

    @Test
    void emptyBytesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new SFaceEmbedder(new byte[0], ZERO_PIN, null));
    }

    @Test
    void pinIsMandatory() {
        byte[] b = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> new SFaceEmbedder(b, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SFaceEmbedder(b, "", null));
        assertThrows(IllegalArgumentException.class, () -> new SFaceEmbedder(b, "   ", null));
    }

    @Test
    void wrongPinThrowsSecurityExceptionBeforeAnyModelLoad() {
        SecurityException e = assertThrows(SecurityException.class,
                () -> new SFaceEmbedder(new byte[]{1, 2, 3}, ZERO_PIN, null));
        assertTrue(e.getMessage().contains("mismatch"), e.getMessage());
    }

    @Test
    void correctPinButCorruptBytesThrowsIllegalState() {
        byte[] bad = {1, 2, 3};
        assertThrows(IllegalStateException.class, () -> new SFaceEmbedder(bad, sha(bad), null));
    }

    @Test
    void pinMatchIsCaseInsensitiveAndTrimmed() {
        byte[] bad = {1, 2, 3};
        // pin accepted (upper-case, padded) so the failure is the unloadable model, not a pin mismatch
        assertThrows(IllegalStateException.class,
                () -> new SFaceEmbedder(bad, "  " + sha(bad).toUpperCase() + " ", null));
    }

    @Test
    void pinnedBytesGiveSameEmbeddingAsPathConstructor() throws Exception {
        Path model = Paths.get("datasets/models/face_recognition_sface.onnx");
        assumeTrue(Files.exists(model), "SFace model not downloaded: " + model + "; test SKIPPED (not passed)");
        byte[] bytes = Files.readAllBytes(model);
        byte[] crop = new byte[SFaceEmbedder.INPUT_SIZE * SFaceEmbedder.INPUT_SIZE * 3];
        for (int i = 0; i < crop.length; i++) crop[i] = (byte) ((i * 17) % 256);
        try (SFaceEmbedder fromPath = new SFaceEmbedder(model);
             SFaceEmbedder fromBytes = new SFaceEmbedder(bytes, sha(bytes), null)) {
            float[] a = fromPath.embedAligned(crop);
            float[] b = fromBytes.embedAligned(crop);
            assertEquals(SFaceEmbedder.EMBEDDING_DIM, b.length);
            assertArrayEquals(a, b, 1e-6f);
        }
    }
}
