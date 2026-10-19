package liveness.android;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LatencyBenchmarkTest {

    /** Fake clock: each op advances time by the next scripted duration. */
    private static LatencyBenchmark.Stats run(final long[] durationsMs, int warmup) throws Exception {
        final AtomicLong now = new AtomicLong();
        final AtomicInteger i = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        LatencyBenchmark.Op op = () -> {
            int c = calls.getAndIncrement();
            if (c >= warmup) now.addAndGet(durationsMs[i.getAndIncrement()] * 1_000_000L);
        };
        return LatencyBenchmark.measure(op, warmup, durationsMs.length, now::get);
    }

    @Test
    void nearestRankPercentiles() throws Exception {
        long[] d = new long[100];
        for (int k = 0; k < 100; k++) d[k] = k + 1; // 1..100 ms
        LatencyBenchmark.Stats s = run(d, 0);
        assertEquals(100, s.n);
        assertEquals(50.0, s.p50Ms, 1e-9);
        assertEquals(95.0, s.p95Ms, 1e-9);
        assertEquals(99.0, s.p99Ms, 1e-9);
        assertEquals(100.0, s.maxMs, 1e-9);
        assertEquals(50.5, s.meanMs, 1e-9);
    }

    @Test
    void warmupCallsAreExcluded() throws Exception {
        LatencyBenchmark.Stats s = run(new long[]{10, 10, 10, 10}, 5);
        assertEquals(4, s.n);
        assertEquals(10.0, s.maxMs, 1e-9);
    }

    @Test
    void targetCheckUsesP95NotMean() throws Exception {
        long[] d = new long[20];
        java.util.Arrays.fill(d, 50L);
        d[19] = 900L; // single outlier is p100, not p95
        assertTrue(run(d, 0).meetsP95(200));
        java.util.Arrays.fill(d, 50L);
        d[18] = 500L; d[19] = 500L; // two slow calls out of 20 push p95 over
        assertFalse(run(d, 0).meetsP95(200));
    }

    @Test
    void measuresRealCodeAndSerializesJson() throws Exception {
        LatencyBenchmark.Stats s = LatencyBenchmark.measure(() -> Thread.sleep(2), 1, 5);
        assertTrue(s.p50Ms >= 1.5, "p50=" + s.p50Ms);
        String j = s.toJson();
        assertEquals(5L, MiniJson.parseObject(j).get("n"));
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.measure(() -> { }, 0, 0));
    }

    @Test
    void operationFailuresPropagate() {
        assertThrows(IllegalStateException.class, () -> LatencyBenchmark.measure(() -> { throw new IllegalStateException("x"); }, 0, 3));
    }
}
