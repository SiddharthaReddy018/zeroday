package liveness.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the final ISO/IEC 30107-3 evaluation report (§12, §24, R6).
 * <p>
 * Report includes:
 * - Per-species APCER table with Wilson CIs
 * - Worst-species headline APCER
 * - BPCER with CI
 * - DET/D-EER
 * - IAD metrics (separate table)
 * - Fairness Bias Index
 * - Latency percentiles
 * - Honest coverage statement
 * <p>
 * <b>Never reports ACER</b>.
 */
public final class EvaluationReport {

    private final PadMetrics padMetrics;
    private final IadMetrics iadMetrics;
    private final FairnessAnalyzer fairness;
    private final LatencyMetrics latency;

    private final java.util.List<String> provenance = new java.util.ArrayList<>();

    /**
     * R9 (ADR-010): adds a provenance/limits statement (data source, synthetic vs live, face localisation,
     * scorer identity). Printed in its own section near the top of the report.
     */
    public EvaluationReport addProvenance(String statement) {
        if (statement != null && !statement.trim().isEmpty()) provenance.add(statement.trim());
        return this;
    }

    private final java.util.Map<String, String> notMeasured = new java.util.LinkedHashMap<>();

    /**
     * R9: a metric that this run could not measure is listed with the reason. It is never estimated,
     * defaulted to 0 or derived from other numbers.
     */
    public EvaluationReport addNotMeasured(String metric, String reason) {
        notMeasured.put(metric, reason);
        return this;
    }

    /** R9 check, separate from {@link #validateContract()} so existing contract tests are unaffected. */
    public java.util.List<String> validateProvenance() {
        java.util.List<String> v = new ArrayList<>();
        if (provenance.isEmpty()) v.add("Report has no provenance/limits statement (R9)");
        return v;
    }

    public EvaluationReport(PadMetrics padMetrics, IadMetrics iadMetrics,
                            FairnessAnalyzer fairness, LatencyMetrics latency) {
        this.padMetrics = padMetrics;
        this.iadMetrics = iadMetrics;
        this.fairness = fairness;
        this.latency = latency;
    }

    /**
     * Generates the complete report as Markdown.
     */
    public String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Face Liveness PAD — Evaluation Report\n\n");
        sb.append("**ISO/IEC 30107-3 aligned (metrics and reporting structure; not a conformance claim) · MOSIP Decode 2026**\n\n");
        sb.append(String.format("**Threshold:** %d (score 0-100, higher = more live)\n\n", padMetrics.threshold()));
        sb.append(String.format("**Total samples:** %d\n\n", padMetrics.sampleCount()));

        // Rule of 30 validation
        int bfSubjects = padMetrics.bonafideSubjectCount();
        sb.append(String.format("**Bona fide subjects:** %d %s\n\n",
                bfSubjects,
                padMetrics.ruleOf30Satisfied()
                        ? "(Rule of 30 satisfied)"
                        : "(**WARNING: Rule of 30 NOT satisfied — BPCER is exploratory**)"));

        if (!provenance.isEmpty()) {
            sb.append("## Evaluation Provenance and Limits\n\n");
            for (String p : provenance) sb.append("- ").append(p).append("\n");
            sb.append("\n");
        }

        sb.append("---\n\n");

        // Per-species APCER table
        appendApcerTable(sb);

        sb.append("---\n\n");

        // BPCER
        appendBpcer(sb);

        sb.append("---\n\n");

        // D-EER
        appendDeer(sb);

        sb.append("---\n\n");

        // IAD (separate table per §12.2)
        if (iadMetrics != null) {
            sb.append(iadMetrics.report());
            sb.append("\n---\n\n");
        }

        // Fairness
        if (fairness != null) {
            sb.append(fairness.report());
            sb.append("\n---\n\n");
        }

        // Latency
        if (latency != null) {
            sb.append(latency.report());
            sb.append("\n---\n\n");
        }

        if (!notMeasured.isEmpty()) {
            sb.append("## Metrics Not Measured in This Run\n\n");
            sb.append("| Metric | Why it is not reported |\n|---|---|\n");
            for (java.util.Map.Entry<String, String> e : notMeasured.entrySet()) {
                sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n---\n\n");
        }

        // Honest coverage statement
        appendCoverageStatement(sb);

        return sb.toString();
    }

    private void appendApcerTable(StringBuilder sb) {
        sb.append("## Per-Species APCER (ISO/IEC 30107-3, §24.1)\n\n");
        sb.append("| PAI Species | APCER | N (attacks) | 95% CI |\n");
        sb.append("|---|---|---|---|\n");

        Map<String, PadMetrics.SpeciesResult> results = padMetrics.apcerAllSpecies();
        List<String> sortedSpecies = new ArrayList<>(results.keySet());
        java.util.Collections.sort(sortedSpecies);

        for (String species : sortedSpecies) {
            PadMetrics.SpeciesResult r = results.get(species);
            sb.append(String.format("| %s | %.4f | %d | %s |\n",
                    species, r.rate(), r.count(), r.ci()));
        }

        // Worst-species headline
        PadMetrics.SpeciesResult worst = padMetrics.worstSpeciesApcer();
        if (worst != null) {
            sb.append(String.format("\n**Worst-species headline APCER:** %s = %.4f %s\n\n",
                    worst.species(), worst.rate(), worst.ci()));
        }
    }

    private void appendBpcer(StringBuilder sb) {
        sb.append("## BPCER\n\n");
        PadMetrics.RateResult bpcer = padMetrics.bpcer();
        sb.append(String.format("**BPCER:** %.4f (N=%d) %s\n\n",
                bpcer.rate(), bpcer.count(), bpcer.ci()));
    }

    private void appendDeer(StringBuilder sb) {
        sb.append("## D-EER (Detection Equal Error Rate)\n\n");
        PadMetrics.DetPoint deer = padMetrics.deer();
        sb.append(String.format("**D-EER:** ~%.4f at threshold %d (APCER=%.4f, BPCER=%.4f)\n\n",
                (deer.apcer() + deer.bpcer()) / 2.0,
                deer.threshold(), deer.apcer(), deer.bpcer()));
    }

    private void appendCoverageStatement(StringBuilder sb) {
        sb.append("## Honest Coverage Statement (§15.5)\n\n");
        sb.append("| Threat | Treatment | What We Claim |\n");
        sb.append("|---|---|---|\n");
        sb.append("| Printed photo | **Primary detection target** | Targeted detection with APCER measured across artefact intensities |\n");
        sb.append("| Screen display | **Primary detection target** | Targeted detection |\n");
        sb.append("| Video replay | **Primary detection target** | Targeted detection |\n");
        sb.append("| Cut-out mask | **Secondary detection target** | Increased resistance; detection under favourable conditions |\n");
        sb.append("| Curved print | **Secondary detection target** | Increased resistance; detection under favourable conditions |\n");
        sb.append("| 2D rigid mask | **Secondary detection target** | Increased resistance; detection under favourable conditions |\n");
        sb.append("| 3D silicone/resin mask | **Experimental resistance** | Multi-cue analysis; no standalone detection claim |\n");
        sb.append("| Real-time deepfake | **Mitigation only** | Challenge-response binding + latency + temporal analysis; no detection guarantee |\n");
        sb.append("| Injection attacks | **Architectural control (IAD)** | CaptureIntegrity module per CEN/TS 18099 concepts |\n");
        sb.append("| Frozen frame | **Detection** | High-reliability via frame sequence monitor |\n");
        sb.append("| Frame loop replay | **Detection** | Reliable for non-adaptive replay |\n");
    }

    /**
     * Validates the report content per R6 contract.
     *
     * @return list of violations (empty = valid)
     */
    public List<String> validateContract() {
        List<String> violations = new ArrayList<>();
        String report = generate();

        // Must have per-species APCER rows
        Set<String> species = padMetrics.species();
        if (species.isEmpty()) {
            violations.add("No per-species APCER rows found");
        }
        for (String s : species) {
            if (!report.contains(s)) {
                violations.add("Missing per-species APCER row for: " + s);
            }
        }

        // Must have BPCER
        if (!report.contains("BPCER")) {
            violations.add("Report missing BPCER");
        }

        // Must have worst-species headline
        if (!report.contains("Worst-species headline")) {
            violations.add("Report missing worst-species headline");
        }

        // Must NOT contain ACER
        if (report.contains("ACER") && !report.contains("worst-species headline APCER")
                && !report.contains("APCER")) {
            violations.add("Report contains ACER (forbidden by §24.1)");
        }
        // Explicit ACER check — look for standalone "ACER" metric
        String[] lines = report.split("\n");
        for (String line : lines) {
            // Match "ACER" not part of "APCER" or "BPCER"
            String stripped = line.replaceAll("APCER", "").replaceAll("BPCER", "");
            if (stripped.contains("ACER")) {
                violations.add("Report contains standalone ACER metric: " + line.trim());
            }
        }

        // IAD must be in a separate table
        if (iadMetrics != null) {
            if (!report.contains("Injection Attack Detection")) {
                violations.add("IAD metrics not in a separate section");
            }
        }

        return violations;
    }
}
