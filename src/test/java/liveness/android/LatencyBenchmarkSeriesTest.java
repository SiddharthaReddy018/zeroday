package liveness.android;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LatencyBenchmarkSeriesTest {

    /** Fake clock: each op() call consumes the next scripted duration. */
    private static double[] sample(final long[] durationsMs, int warmup) throws Exception {
        final AtomicLong now = new AtomicLong();
        final int[] idx = {0};
        LatencyBenchmark.Op op = () -> {
            long d = idx[0] < warmup ? 1 : durationsMs[idx[0] - warmup];
            idx[0]++;
            now.addAndGet(d * 1_000_000L);
        };
        return LatencyBenchmark.sampleMs(op, warmup, durationsMs.length, now::get);
    }

    @Test
    void sampleMsKeepsCallOrderAndSkipsWarmup() throws Exception {
        double[] ms = sample(new long[]{30, 10, 20}, 2);
        assertArrayEquals(new double[]{30, 10, 20}, ms, 1e-9);
    }

    @Test
    void sampleMsRejectsZeroIterations() {
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.sampleMs(() -> { }, 0, 0));
    }

    @Test
    void summarizeDoesNotModifyItsInputAndMatchesNearestRank() {
        double[] in = {5, 1, 4, 2, 3};
        LatencyBenchmark.Stats s = LatencyBenchmark.summarize(in);
        assertArrayEquals(new double[]{5, 1, 4, 2, 3}, in, 0.0, "input must keep call order");
        assertEquals(5, s.n);
        assertEquals(3.0, s.meanMs, 1e-9);
        assertEquals(3.0, s.p50Ms, 1e-9);   // rank ceil(0.5*5)=3 -> 3rd smallest
        assertEquals(5.0, s.p95Ms, 1e-9);   // rank ceil(0.95*5)=5
        assertEquals(5.0, s.maxMs, 1e-9);
    }

    @Test
    void summarizeRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.summarize(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.summarize(null));
    }

    @Test
    void measureStillEqualsSummarizeOfSampleMs() throws Exception {
        long[] d = {7, 3, 9, 4, 8, 2};
        final AtomicLong now = new AtomicLong();
        final int[] i = {0};
        LatencyBenchmark.Op op = () -> now.addAndGet(d[i[0]++] * 1_000_000L);
        LatencyBenchmark.Stats m = LatencyBenchmark.measure(op, 0, d.length, now::get);
        LatencyBenchmark.Stats s = LatencyBenchmark.summarize(new double[]{7, 3, 9, 4, 8, 2});
        assertEquals(s.toJson(), m.toJson());
    }

    @Test
    void blockP50ExposesSlowdownOverTheRun() {
        // a device that throttles: 10 ms for the first half of the run, 30 ms for the second half
        double[] ms = new double[100];
        for (int i = 0; i < 100; i++) ms[i] = i < 50 ? 10 : 30;
        double[] b = LatencyBenchmark.blockP50(ms, 5);
        assertEquals(5, b.length);
        assertEquals(10.0, b[0], 1e-9);
        assertEquals(30.0, b[4], 1e-9);
    }

    @Test
    void blockP50GivesTheRemainderToTheLastBlockAndRejectsTooFewSamples() {
        double[] ms = {1, 1, 1, 9, 9, 9, 9};            // 7 samples, 3 blocks: [1,1] [1,9] [9,9,9]
        double[] b = LatencyBenchmark.blockP50(ms, 3);
        assertEquals(1.0, b[0], 1e-9);
        assertEquals(1.0, b[1], 1e-9);                   // nearest-rank p50 of {1,9} is the 1st
        assertEquals(9.0, b[2], 1e-9);
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.blockP50(new double[]{1, 2}, 3));
        assertThrows(IllegalArgumentException.class, () -> LatencyBenchmark.blockP50(new double[]{1, 2}, 0));
    }
}
