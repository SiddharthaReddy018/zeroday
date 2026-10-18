package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Wilson score interval computation.
 */
public class WilsonCITest {

    @Test
    public void basicComputation() {
        // 10 successes out of 100 trials → p_hat = 0.1
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(10, 100);
        assertTrue(ci.lower() > 0.0);
        assertTrue(ci.center() > ci.lower());
        assertTrue(ci.upper() > ci.center());
        assertTrue(ci.upper() < 1.0);
        // Center should be close to 0.1 but pulled toward 0.5 by the Wilson correction
        assertTrue(ci.center() > 0.05 && ci.center() < 0.20);
    }

    @Test
    public void zeroSuccesses() {
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(0, 100);
        assertEquals(0.0, ci.lower(), 0.001);
        assertTrue(ci.center() > 0.0, "Wilson center is nonzero even with 0 successes");
        assertTrue(ci.upper() > 0.0);
    }

    @Test
    public void allSuccesses() {
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(100, 100);
        assertTrue(ci.lower() > 0.9);
        assertTrue(ci.center() < 1.0, "Wilson center is less than 1.0 even with all successes");
        assertEquals(1.0, ci.upper(), 0.001);
    }

    @Test
    public void smallSample() {
        // 1 success out of 5 → wider interval
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(1, 5);
        assertTrue(ci.upper() - ci.lower() > 0.1, "Small sample should produce wide interval");
    }

    @Test
    public void knownValue() {
        // 30 out of 100: p_hat = 0.3
        // Wilson center ≈ (0.3 + 1.96²/(2*100)) / (1 + 1.96²/100)
        //               ≈ (0.3 + 0.019208) / (1 + 0.038416)
        //               ≈ 0.319208 / 1.038416 ≈ 0.3074
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(30, 100);
        assertEquals(0.3074, ci.center(), 0.01);
    }

    @Test
    public void throwsOnInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> WilsonConfidenceInterval.compute(5, 0));
        assertThrows(IllegalArgumentException.class,
                () -> WilsonConfidenceInterval.compute(-1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> WilsonConfidenceInterval.compute(11, 10));
    }

    @Test
    public void intervalAlwaysBounded() {
        // Test many values to ensure bounds are always [0,1]
        for (int n = 1; n <= 50; n++) {
            for (int k = 0; k <= n; k++) {
                WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(k, n);
                assertTrue(ci.lower() >= 0.0, "lower must be >= 0");
                assertTrue(ci.upper() <= 1.0, "upper must be <= 1");
                assertTrue(ci.lower() <= ci.center(), "lower <= center");
                assertTrue(ci.center() <= ci.upper(), "center <= upper");
            }
        }
    }

    @Test
    public void toStringFormat() {
        WilsonConfidenceInterval.Interval ci = WilsonConfidenceInterval.compute(10, 100);
        String str = ci.toString();
        assertTrue(str.startsWith("["), "toString should start with [");
        assertTrue(str.endsWith("]"), "toString should end with ]");
    }
}
