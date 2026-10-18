package liveness.evaluation;

/**
 * Computes the Wilson score interval for binomial proportions (§12.3).
 * <p>
 * The Wilson interval is preferred over the Wald (normal approximation) interval
 * because it has better coverage properties for small samples and proportions
 * near 0 or 1 — both common in PAD evaluation.
 * <p>
 * Formula (95% CI, z = 1.96):
 * <pre>
 *   center = (p_hat + z²/(2n)) / (1 + z²/n)
 *   margin = (z / (1 + z²/n)) * sqrt((p_hat*(1-p_hat) + z²/(4n)) / n)
 *   lower  = max(0, center - margin)
 *   upper  = min(1, center + margin)
 * </pre>
 */
public final class WilsonConfidenceInterval {

    /** z-value for 95% confidence interval. */
    private static final double Z = 1.96;
    private static final double Z_SQ = Z * Z;

    private WilsonConfidenceInterval() {
        // utility class
    }

    /**
     * Computes the Wilson score interval.
     *
     * @param successes number of "successes" (e.g. misclassifications)
     * @param n         total number of trials
     * @return interval with lower, center, and upper bounds
     * @throws IllegalArgumentException if n &lt;= 0 or successes &lt; 0 or successes &gt; n
     */
    public static Interval compute(int successes, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0, got: " + n);
        }
        if (successes < 0 || successes > n) {
            throw new IllegalArgumentException(
                    "successes must be in [0, n], got: " + successes + " with n=" + n);
        }

        double pHat = (double) successes / n;
        double denominator = 1.0 + Z_SQ / n;
        double center = (pHat + Z_SQ / (2.0 * n)) / denominator;
        double margin = (Z / denominator)
                * Math.sqrt((pHat * (1.0 - pHat) + Z_SQ / (4.0 * n)) / n);

        double lower = Math.max(0.0, center - margin);
        double upper = Math.min(1.0, center + margin);

        return new Interval(lower, center, upper);
    }

    /**
     * Immutable interval result.
     */
    public static final class Interval {
        private final double lower;
        private final double center;
        private final double upper;

        public Interval(double lower, double center, double upper) {
            this.lower = lower;
            this.center = center;
            this.upper = upper;
        }

        public double lower()  { return lower; }
        public double center() { return center; }
        public double upper()  { return upper; }

        @Override
        public String toString() {
            return String.format("[%.4f, %.4f, %.4f]", lower, center, upper);
        }
    }
}
