package liveness.engine;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Model qualification test per S26.11 and HANDOFF-2 R1 (§15.2):
 * - Verifies input shape, dimensions, and types directly from the shipped ONNX model
 * - Proves model loading via ONNX Runtime
 * - Verifies outputs (logits / class probabilities)
 * - Tests both FP32 and INT8 quantized model variants
 */
public class ModelQualificationTest {

    @Test
    public void qualifyFp32Model() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        assumeTrue(Files.exists(modelPath), "model missing: " + modelPath);

        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions())) {

            assertNotNull(session);
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            assertFalse(inputInfo.isEmpty(), "Model must have at least one input");

            // Verify input tensor specification
            String inputName = inputInfo.keySet().iterator().next();
            NodeInfo info = inputInfo.get(inputName);
            assertTrue(info.getInfo() instanceof TensorInfo);
            TensorInfo tensorInfo = (TensorInfo) info.getInfo();

            long[] shape = tensorInfo.getShape();
            System.out.println("Model Input Name: " + inputName + ", Shape: " + java.util.Arrays.toString(shape));
            assertEquals(4, shape.length, "Input tensor must be 4D (batch, channels, height, width)");
            assertEquals(3, shape[1], "Input channels must be 3 (RGB)");

            // Verify outputs
            Map<String, NodeInfo> outputInfo = session.getOutputInfo();
            assertFalse(outputInfo.isEmpty(), "Model must have at least one output");
            String outputName = outputInfo.keySet().iterator().next();
            NodeInfo outInfo = outputInfo.get(outputName);
            assertTrue(outInfo.getInfo() instanceof TensorInfo);
            TensorInfo outTensorInfo = (TensorInfo) outInfo.getInfo();

            long[] outShape = outTensorInfo.getShape();
            System.out.println("Model Output Name: " + outputName + ", Shape: " + java.util.Arrays.toString(outShape));
            assertTrue(outShape.length >= 2, "Output tensor must be at least 2D [batch, classes]");
        }
    }

    @Test
    public void qualifyInt8QuantizedModel() throws Exception {
        Path modelPath = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");
        assumeTrue(Files.exists(modelPath), "model missing: " + modelPath);

        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions())) {

            assertNotNull(session);
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            assertFalse(inputInfo.isEmpty());

            Map<String, NodeInfo> outputInfo = session.getOutputInfo();
            assertFalse(outputInfo.isEmpty());
        }
    }

    @Test
    public void modelFileBudgetCompliant() {
        Path fp32 = Paths.get("datasets/models/MiniFASNetV2-SE.onnx");
        Path int8 = Paths.get("datasets/models/MiniFASNetV2-SE-int8.onnx");
        assumeTrue(Files.exists(fp32) || Files.exists(int8), "model files missing");

        if (Files.exists(fp32)) {
            long bytes = fp32.toFile().length();
            assertTrue(bytes <= 5 * 1024 * 1024,
                    "FP32 model budget <= 5 MB constraint (§16); actual: " + (bytes / 1024) + " KB");
        }

        if (Files.exists(int8)) {
            long bytes = int8.toFile().length();
            assertTrue(bytes <= 1 * 1024 * 1024,
                    "INT8 model target <= 1 MB constraint; actual: " + (bytes / 1024) + " KB");
        }
    }

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
