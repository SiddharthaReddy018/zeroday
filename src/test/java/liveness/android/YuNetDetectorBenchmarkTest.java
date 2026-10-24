package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;

class YuNetDetectorBenchmarkTest {

    @Test
    void rejectsInvalidArgumentsBeforeLoadingModel() {
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 640, 480, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 0, 480, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> YuNetDetectorBenchmark.run(Paths.get("missing.onnx"), 640, 480, -1, 10));
    }
}
