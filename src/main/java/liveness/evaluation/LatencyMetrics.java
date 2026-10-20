package liveness.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Component-level performance instrumentation (§26.3).
 * <p>
 * Tracks p50/p90/p95/p99/max latencies per component.
 * Lightweight, in-memory, no external dependencies.
 */
public final class LatencyMetrics {

    private final Map<String, List<Long>> measurements = new HashMap<>();

    /**
     * Records a latency measurement for a component.
     *
     * @param component component name (e.g. "decode", "face_detect", "minifasnet", "quality_gate")
     * @param durationNanos duration in nanoseconds
     */
    public void record(String component, long durationNanos) {
        if (!measurements.containsKey(component)) {
            measurements.put(component, new ArrayList<>());
        }
        measurements.get(component).add(durationNanos);
    }

    /** Appends all raw measurements of {@code other} (used to attach scorer-side timings to a run). */
    public void mergeFrom(LatencyMetrics other) {
        for (Map.Entry<String, List<Long>> e : other.measurements.entrySet()) {
            for (long v : e.getValue()) record(e.getKey(), v);
        }
    }

    /**
     * Returns the specified percentile for a component in milliseconds.
     *
     * @param component component name
     * @param pct       percentile (0-100, e.g. 50 for p50, 95 for p95)
     * @return latency at the given percentile in milliseconds, or -1 if no data
     */
    public double percentile(String component, int pct) {
        List<Long> data = measurements.get(component);
        if (data == null || data.isEmpty()) return -1;

        List<Long> sorted = new ArrayList<>(data);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx) / 1_000_000.0; // nanos to ms
    }

    /**
     * Maximum latency for a component in milliseconds.
     */
    public double max(String component) {
        List<Long> data = measurements.get(component);
        if (data == null || data.isEmpty()) return -1;
        long maxVal = Long.MIN_VALUE;
        for (long v : data) {
            if (v > maxVal) maxVal = v;
        }
        return maxVal / 1_000_000.0;
    }

    /**
     * Count of measurements for a component.
     */
    public int count(String component) {
        List<Long> data = measurements.get(component);
        return data == null ? 0 : data.size();
    }

    /**
     * All measured components.
     */
    public java.util.Set<String> components() {
        return Collections.unmodifiableSet(measurements.keySet());
    }

    /**
     * Generates a formatted latency report.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Latency Percentiles (§26.3)\n\n");
        sb.append("| Component | N | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) | max (ms) |\n");
        sb.append("|---|---|---|---|---|---|---|\n");

        List<String> sorted = new ArrayList<>(measurements.keySet());
        Collections.sort(sorted);
        for (String comp : sorted) {
            sb.append(String.format("| %s | %d | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                    comp, count(comp),
                    percentile(comp, 50),
                    percentile(comp, 90),
                    percentile(comp, 95),
                    percentile(comp, 99),
                    max(comp)));
        }
        return sb.toString();
    }
}
