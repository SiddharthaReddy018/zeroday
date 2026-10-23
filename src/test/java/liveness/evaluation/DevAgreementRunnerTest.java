package liveness.evaluation;

import liveness.engine.MiniFasNetScorer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Scores the DEV split with two model files and writes target/DEV_AGREEMENT_REPORT.md. The TEST split is never
 * scored: entries are filtered to dev BEFORE any image is opened, and DevAgreementReport rejects non-dev entries.
 * <p>
 * Reported as SKIPPED (not passed) when the manifest or either model is not on disk. Models:
 * -Dpad.model=... (reference, default datasets/models/MiniFASNetV2-SE.onnx) and
 * -Dpad.model.alt=... (alternative, default datasets/models/MiniFASNetV2-SE-int8.onnx). Threshold: -Dpad.threshold
 * (default 70, the frozen ADR-011 value).
 */
public class DevAgreementRunnerTest {

    @Test
    public void generateDevAgreementReport() throws Exception {
        Path manifestPath = Paths.get("datasets/manifests/evaluation_benchmark_manifest.csv");
        Path refPath = Paths.get(System.getProperty("pad.model", "datasets/models/MiniFASNetV2-SE.onnx"));
        Path altPath = Paths.get(System.getProperty("pad.model.alt", "datasets/models/MiniFASNetV2-SE-int8.onnx"));
        int threshold = Integer.parseInt(System.getProperty("pad.threshold", "70"));
        assumeTrue(Files.exists(manifestPath), "benchmark manifest not present: " + manifestPath);
        assumeTrue(Files.exists(refPath), "reference PAD model not present: " + refPath);
        assumeTrue(Files.exists(altPath), "alternative PAD model not present: " + altPath);

        List<ManifestEntry> dev = DevAgreementReport.devOnly(ManifestLoader.load(manifestPath));
        assertFalse(dev.isEmpty(), "manifest has no dev entries");
        for (ManifestEntry e : dev) {
            assertTrue("dev".equals(e.split()), "non-dev entry leaked into the dev list: " + e);
        }

        Path sidecar = Paths.get(manifestPath.toString() + ".bbox.csv");
        Path datasetsDir = manifestPath.getParent().getParent();
        List<DevAgreementReport.Pair> pairs = new ArrayList<>();
        String locRef;
        String locAlt;
        try (MiniFasNetScorer ref = new MiniFasNetScorer(refPath);
             MiniFasNetScorer alt = new MiniFasNetScorer(altPath)) {
            OnnxManifestScorer refScorer = new OnnxManifestScorer(ref, datasetsDir, sidecar);
            OnnxManifestScorer altScorer = new OnnxManifestScorer(alt, datasetsDir, sidecar);
            for (ManifestEntry e : dev) {
                pairs.add(new DevAgreementReport.Pair(e, refScorer.score(e), altScorer.score(e)));
            }
            locRef = refScorer.localisationSummary();
            locAlt = altScorer.localisationSummary();
        }

        DevAgreementReport report = DevAgreementReport.compute("FP32", "INT8", pairs, threshold);
        String md = report.generate()
                + "\n## Provenance\n\n"
                + "- Reference model file: " + refPath.getFileName() + ", SHA-256 " + sha256(refPath) + "\n"
                + "- Alternative model file: " + altPath.getFileName() + ", SHA-256 " + sha256(altPath) + "\n"
                + "- Manifest: " + manifestPath.getFileName() + ", dev split only (" + dev.size() + " entries).\n"
                + "- Reference " + locRef + "\n"
                + "- Alternative " + locAlt + "\n"
                + "- Host: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + ", Java " + System.getProperty("java.version") + ", " + MiniFasNetScorer.RUNTIME_DESCRIPTION + ".\n";

        Path out = Paths.get("target/DEV_AGREEMENT_REPORT.md");
        Files.createDirectories(out.getParent());
        Files.write(out, md.getBytes(StandardCharsets.UTF_8));
        System.out.println("DEV agreement report written to: " + out.toAbsolutePath());
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(Files.readAllBytes(p));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
