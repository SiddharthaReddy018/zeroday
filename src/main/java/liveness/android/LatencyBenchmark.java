package liveness.android;

import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Per-call latency measurement with nearest-rank percentiles. Pure Java; the Android glue
 * (ScorerBenchmark) feeds it the real INT8 model so the p95 target can be measured ON the target device.
 */
public final class LatencyBenchmark {

    public interface Op { void run() throws Exception; }

    public static final class Stats {
        public final int n;
        public final double meanMs, p50Ms, p95Ms, p99Ms, maxMs;

        Stats(int n, double mean, double p50, double p95, double p99, double max) {
            this.n = n; this.meanMs = mean; this.p50Ms = p50; this.p95Ms = p95; this.p99Ms = p99; this.maxMs = max;
        }

        public boolean meetsP95(double targetMs) { return p95Ms <= targetMs; }

        public String toJson() {
            return String.format(java.util.Locale.ROOT,
                    "{\"n\":%d,\"meanMs\":%.2f,\"p50Ms\":%.2f,\"p95Ms\":%.2f,\"p99Ms\":%.2f,\"maxMs\":%.2f}",
                    n, meanMs, p50Ms, p95Ms, p99Ms, maxMs);
        }
    }

    private LatencyBenchmark() {}

    public static Stats measure(Op op, int warmup, int iterations) throws Exception {
        return measure(op, warmup, iterations, System::nanoTime);
    }

    /** {@code nanoClock} is injectable for deterministic tests. */
    public static Stats measure(Op op, int warmup, int iterations, LongSupplier nanoClock) throws Exception {
        return summarize(sampleMs(op, warmup, iterations, nanoClock));
    }

    /** Per-call durations in milliseconds, in call order (so drift over time stays visible). */
    public static double[] sampleMs(Op op, int warmup, int iterations) throws Exception {
        return sampleMs(op, warmup, iterations, System::nanoTime);
    }

    public static double[] sampleMs(Op op, int warmup, int iterations, LongSupplier nanoClock) throws Exception {
        if (iterations < 1) throw new IllegalArgumentException("iterations");
        for (int i = 0; i < warmup; i++) op.run();
        double[] ms = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            long t0 = nanoClock.getAsLong();
            op.run();
            ms[i] = (nanoClock.getAsLong() - t0) / 1_000_000.0;
        }
        return ms;
    }

    /** Summary of durations in any order; the input array is not modified. */
    public static Stats summarize(double[] msInAnyOrder) {
        if (msInAnyOrder == null || msInAnyOrder.length < 1) throw new IllegalArgumentException("no samples");
        double[] ms = msInAnyOrder.clone();
        double sum = 0;
        for (double d : ms) sum += d;
        Arrays.sort(ms);
        return new Stats(ms.length, sum / ms.length, pct(ms, 50), pct(ms, 95), pct(ms, 99), ms[ms.length - 1]);
    }

    /**
     * Median (nearest-rank p50) of each of {@code blocks} consecutive slices of the samples IN CALL ORDER; any
     * remainder goes to the last block. A last block clearly above the first means the device slowed down during
     * the run (thermal throttling, governor change), which a single p50/p95 over the whole run hides.
     */
    public static double[] blockP50(double[] msInCallOrder, int blocks) {
        if (blocks < 1 || msInCallOrder == null || msInCallOrder.length < blocks) {
            throw new IllegalArgumentException("need at least one sample per block");
        }
        double[] out = new double[blocks];
        int per = msInCallOrder.length / blocks;
        for (int b = 0; b < blocks; b++) {
            int from = b * per;
            int to = (b == blocks - 1) ? msInCallOrder.length : from + per;
            double[] slice = Arrays.copyOfRange(msInCallOrder, from, to);
            Arrays.sort(slice);
            out[b] = pct(slice, 50);
        }
        return out;
    }

    /** Nearest-rank percentile on a sorted array. */
    static double pct(double[] sorted, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }
}
