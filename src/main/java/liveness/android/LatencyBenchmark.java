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
        if (iterations < 1) throw new IllegalArgumentException("iterations");
        for (int i = 0; i < warmup; i++) op.run();
        double[] ms = new double[iterations];
        double sum = 0;
        for (int i = 0; i < iterations; i++) {
            long t0 = nanoClock.getAsLong();
            op.run();
            ms[i] = (nanoClock.getAsLong() - t0) / 1_000_000.0;
            sum += ms[i];
        }
        Arrays.sort(ms);
        return new Stats(iterations, sum / iterations, pct(ms, 50), pct(ms, 95), pct(ms, 99), ms[iterations - 1]);
    }

    /** Nearest-rank percentile on a sorted array. */
    static double pct(double[] sorted, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }
}
