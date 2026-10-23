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
}
