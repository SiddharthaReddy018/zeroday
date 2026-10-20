package liveness.evaluation;

import liveness.engine.MiniFasNetScorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * M5 evaluation on the benchmark manifest using the REAL ONNX model. Reported as SKIPPED (not passed) when the
 * manifest or model is not on disk. Model path: -Dpad.model=... or datasets/models/MiniFASNetV2-SE.onnx.
 * IAD rows are NOT populated here: they are measured by FrameSequenceMonitorTest / SbiIntentBridgeTest and must
 * be reported from those runs, never typed in.
 */
public class BenchmarkReportRunnerTest {

    @Test
    public void generateEvaluationReportFromManifest() throws Exception {
        Path manifestPath = Paths.get("datasets/manifests/evaluation_benchmark_manifest.csv");
        Path modelPath = Paths.get(System.getProperty("pad.model", "datasets/models/MiniFASNetV2-SE.onnx"));
        assumeTrue(Files.exists(manifestPath), "benchmark manifest not present: " + manifestPath);
        assumeTrue(Files.exists(modelPath), "PAD model not present: " + modelPath);

        List<ManifestEntry> entries = ManifestLoader.load(manifestPath);
        assertTrue(entries.size() > 1000, "Manifest should have >1000 entries");

        try (MiniFasNetScorer model = new MiniFasNetScorer(modelPath)) {
            Path sidecar = Paths.get(manifestPath.toString() + ".bbox.csv");
            // baseDir is datasets/ (one level up from manifests/) so manifest paths like onot/digital/... resolve
            Path datasetsDir = manifestPath.getParent().getParent();
            OnnxManifestScorer onnx = new OnnxManifestScorer(model, datasetsDir, sidecar);
            EvaluationHarness harness = new EvaluationHarness(onnx, 70);
            EvaluationHarness.EvaluationResult result = harness.run(entries);

            result.latency().mergeFrom(onnx.psPd());

            // R9: provenance is generated from what actually ran, never typed in.
            long bf = entries.stream().filter(ManifestEntry::isBonafide).count();
            long atk = entries.stream().filter(ManifestEntry::isAttack).count();
            result.report()
                    .addProvenance("Scorer: real MiniFASNetV2-SE via ONNX Runtime (model " + modelPath.getFileName() + ").")
                    .addProvenance("Manifest: " + manifestPath.getFileName() + " with " + bf + " bona fide and "
                            + atk + " attack entries; scored split = test only.")
                    .addProvenance(onnx.localisationSummary())
                    .addProvenance("Bona fide images in this manifest are STILL images (ADR-008: ONOT synthetic ICAO "
                            + "set if used). BPCER on stills is NOT a live-presentation BPCER; do not quote it as one.")
                    .addProvenance("A 0 observed errors result is bounded by its CI upper limit, not equal to zero risk.")
                    .addProvenance("PS-PD = rows ps_pd_* in the latency table: PAD preprocessing + ONNX inference per image, "
                            + "measured on this machine (" + System.getProperty("os.name") + " "
                            + System.getProperty("os.arch") + ", Java " + System.getProperty("java.version")
                            + "), not on a phone. Row pad_scoring additionally includes image decoding and is not PS-PD. "
                            + "Warm-up samples are included, not discarded.")
                    .addNotMeasured("APNRR / BPNRR",
                            "Need live presentations through the capture subsystem (device timeout, no response). "
                            + "A still-image manifest has no capture step, so no response can occur or be observed.")
                    .addNotMeasured("APAR",
                            "Needs live attack presentations through the real capture pipeline (quality gate on device "
                            + "landmarks/frames). Stills cannot show whether a sufficient-quality sample would have been acquired.");
            assertTrue(result.report().validateProvenance().isEmpty(), "provenance missing");

            assertTrue(result.padMetrics().ruleOf30Satisfied(), "Rule of 30 must hold");
            List<String> violations = result.report().validateContract();
            assertTrue(violations.isEmpty(), "Report contract violations: " + violations);

            String markdown = result.report().generate();
            Path out = Paths.get("target/TEST_REPORT.md");
            Files.createDirectories(out.getParent());
            Files.write(out, markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("Evaluation report written to: " + out.toAbsolutePath());
        }
    }
}
