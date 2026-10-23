package liveness.desktop;

import liveness.engine.MiniFasNetScorer;
import liveness.engine.PadPreprocessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Score-direction qualification (S26.11, HANDOFF-2 R1), split out of
 * liveness.engine.ModelQualificationTest unchanged. It decodes PNGs through javax.imageio, so it
 * lives in the desktop-only liveness.desktop package and is excluded from the Android unit-test
 * source set. The shape/type qualification tests stay in ModelQualificationTest. Still runs under Maven.
 */
public class ModelScoreDirectionQualificationTest {

    @Test
    public void qualifyScoreDirectionAndClassOrder() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        assumeTrue(Files.exists(modelPath), "model missing: " + modelPath);

        try (MiniFasNetScorer scorer = new MiniFasNetScorer(modelPath)) {
            // Check that uniform black tensor produces a low/spoof score (not high live)
            float[] blackInput = new float[3 * 128 * 128];
            int blackScore = scorer.score(blackInput);
            assertTrue(blackScore < 50, "Black frame must not score as live: " + blackScore);

            // Check that real bona fide face scores higher than attack presentation
            Path bfPath = Paths.get("datasets/onot/digital/S457681878/S457681878-GF-A95-EEA-T14-S1-LH-C1-I0051-F00.png");
            Path atkPath = Paths.get("datasets/attacks/cutout/cutout_000_f0.png");
            if (Files.exists(bfPath) && Files.exists(atkPath)) {
                java.awt.image.BufferedImage bfImg = javax.imageio.ImageIO.read(bfPath.toFile());
                byte[] bfRgb = imageToRgbBytes(bfImg);
                float[] bfTensor = PadPreprocessor.prepare(bfRgb, bfImg.getWidth(), bfImg.getHeight(), 0, 0, 0, 0);
                int bfScore = scorer.score(bfTensor);

                java.awt.image.BufferedImage atkImg = javax.imageio.ImageIO.read(atkPath.toFile());
                byte[] atkRgb = imageToRgbBytes(atkImg);
                float[] atkTensor = PadPreprocessor.prepare(atkRgb, atkImg.getWidth(), atkImg.getHeight(), 0, 0, 0, 0);
                int atkScore = scorer.score(atkTensor);

                assertTrue(bfScore > atkScore, "Bona fide (" + bfScore + ") must score higher than attack (" + atkScore + ")");
                assertTrue(bfScore >= 50, "Bona fide face must achieve >= 50, got " + bfScore);
                assertTrue(atkScore <= 50, "Attack presentation must score <= 50, got " + atkScore);
            }
        }
    }

    private static byte[] imageToRgbBytes(java.awt.image.BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] rgb = new byte[w * h * 3];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 3;
                rgb[o] = (byte) (row[x] >> 16);
                rgb[o + 1] = (byte) (row[x] >> 8);
                rgb[o + 2] = (byte) row[x];
            }
        }
        return rgb;
    }
}
