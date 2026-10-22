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

    private boolean exploratory;

    public EvaluationReport setExploratory(boolean exploratory) {
        this.exploratory = exploratory;
        return this;
    }

    public boolean isExploratory() {
        return exploratory;
    }

    public EvaluationReport(PadMetrics padMetrics, IadMetrics iadMetrics,
                            FairnessAnalyzer fairness, LatencyMetrics latency) {
        this(padMetrics, iadMetrics, fairness, latency, false);
    }

    public EvaluationReport(PadMetrics padMetrics, IadMetrics iadMetrics,
                            FairnessAnalyzer fairness, LatencyMetrics latency,
                            boolean exploratory) {
        this.padMetrics = padMetrics;
        this.iadMetrics = iadMetrics;
        this.fairness = fairness;
        this.latency = latency;
        this.exploratory = exploratory;
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
            String rateStr = Double.isNaN(r.rate()) ? "not measured" : String.format("%.4f", r.rate());
            sb.append(String.format("| %s | %s | %d | %s |\n",
                    species, rateStr, r.count(), r.ci()));
        }

        // Worst-species headline
        PadMetrics.SpeciesResult worst = padMetrics.worstSpeciesApcer();
        if (worst != null) {
            String worstRateStr = Double.isNaN(worst.rate()) ? "not measured" : String.format("%.4f", worst.rate());
            boolean isExploratory = exploratory || padMetrics.totalAttackCount() < 30 || padMetrics.bonafideCount() < 30 || !padMetrics.ruleOf30Satisfied() || worst.count() < 30;
            String tag = isExploratory ? " [EXPLORATORY]" : "";
            sb.append(String.format("\n**Worst-species headline APCER%s:** %s = %s (N=%d) %s\n\n",
                    tag, worst.species(), worstRateStr, worst.count(), worst.ci()));
        }
    }

    private void appendBpcer(StringBuilder sb) {
        sb.append("## BPCER\n\n");
        PadMetrics.RateResult bpcer = padMetrics.bpcer();
        String rateStr = Double.isNaN(bpcer.rate()) ? "not measured" : String.format("%.4f", bpcer.rate());
        boolean isExploratory = exploratory || padMetrics.totalAttackCount() < 30 || padMetrics.bonafideCount() < 30 || !padMetrics.ruleOf30Satisfied() || bpcer.count() < 30;
        String tag = isExploratory ? " [EXPLORATORY]" : "";
        sb.append(String.format("**BPCER%s:** %s (N=%d) %s\n\n",
                tag, rateStr, bpcer.count(), bpcer.ci()));
    }

    private void appendDeer(StringBuilder sb) {
        sb.append("## D-EER (Detection Equal Error Rate)\n\n");
        PadMetrics.DetPoint deer = padMetrics.deer();
        if (Double.isNaN(deer.apcer()) || Double.isNaN(deer.bpcer())) {
            sb.append(String.format("**D-EER:** not computable (operating point: %s)\n\n", deer.label()));
        } else {
            sb.append(String.format("**D-EER:** ~%.4f at threshold %d (APCER=%.4f, BPCER=%.4f, operating point: %s)\n\n",
                    (deer.apcer() + deer.bpcer()) / 2.0,
                    deer.threshold(), deer.apcer(), deer.bpcer(), deer.label()));
        }
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
        return validateContract(generate());
    }

    /**
     * Validates an arbitrary report text against the contract.
     */
    public List<String> validateContract(String report) {
        List<String> violations = new ArrayList<>();

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

        // Must include sample size N and 95% Wilson CI in table
        if (!report.contains("N (attacks)")) {
            violations.add("APCER table missing sample size N column");
        }
        if (!report.contains("95% CI")) {
            violations.add("APCER table missing 95% CI column");
        }

        // Must have BPCER with N and Wilson CI
        if (!report.contains("BPCER")) {
            violations.add("Report missing BPCER");
        } else {
            if (!report.matches("(?s).*\\*\\*BPCER.*\\(N=\\d+\\).*")) {
                violations.add("BPCER missing sample size N");
            }
            if (!report.matches("(?s).*\\*\\*BPCER.*\\[[0-9.]+,\\s*[0-9.]+\\].*")) {
                violations.add("BPCER missing 95% Wilson score confidence interval");
            }
        }

        // Must have worst-species headline APCER with N, Wilson CI, and NOT pooled
        if (!report.contains("Worst-species headline APCER")) {
            violations.add("Report missing worst-species headline APCER (ISO/IEC 30107-3)");
        } else {
            if (!report.matches("(?s).*\\*\\*Worst-species headline APCER.*\\(N=\\d+\\).*")) {
                violations.add("Worst-species headline APCER missing sample size N");
            }
            if (!report.matches("(?s).*\\*\\*Worst-species headline APCER.*\\[[0-9.]+,\\s*[0-9.]+\\].*")) {
                violations.add("Worst-species headline APCER missing 95% Wilson score confidence interval");
            }
        }

        // Headline APCER/BPCER must NOT be pooled across heterogeneous attack types
        if (report.contains("Pooled APCER") && report.contains("headline")) {
            violations.add("Headline APCER must not be pooled across heterogeneous attack types (Section 24.1)");
        }

        // Must NOT contain ACER
        String[] lines = report.split("\n");
        for (String line : lines) {
            // Match "ACER" not part of "APCER" or "BPCER"
            String stripped = line.replaceAll("APCER", "").replaceAll("BPCER", "");
            if (stripped.contains("ACER")) {
                violations.add("Report contains standalone ACER metric: " + line.trim());
            }
        }

        // Fails if unmeasured
        if (padMetrics.sampleCount() > 0) {
            PadMetrics.SpeciesResult worst = padMetrics.worstSpeciesApcer();
            if (worst == null || Double.isNaN(worst.rate()) || report.contains("Worst-species headline APCER: not measured")
                    || report.contains("Worst-species headline APCER [EXPLORATORY]: not measured")) {
                violations.add("Headline APCER is unmeasured");
            }
            PadMetrics.RateResult bpcer = padMetrics.bpcer();
            if (Double.isNaN(bpcer.rate()) || report.contains("BPCER: not measured")
                    || report.contains("BPCER [EXPLORATORY]: not measured")) {
                violations.add("Headline BPCER is unmeasured");
            }
        }

        // Fails if missing [EXPLORATORY] label when N < 30
        boolean nUnder30 = padMetrics.bonafideCount() < 30 || padMetrics.totalAttackCount() < 30 || !padMetrics.ruleOf30Satisfied();
        if (nUnder30 && !report.contains("[EXPLORATORY]")) {
            violations.add("Sample size is under Rule of 30 threshold (N < 30: bonafide=" + padMetrics.bonafideCount()
                    + ", attacks=" + padMetrics.totalAttackCount() + ", subjects=" + padMetrics.bonafideSubjectCount()
                    + ") but report is missing [EXPLORATORY] label");
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
