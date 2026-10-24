package liveness.android;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Session-relative blur state (candidate rule, not fitted; see design-notes/blur-gate-note-v2.md).
 * Not wired into QualityGate. Pure Java. Not thread-safe: one instance per session.
 * Frames without a valid sample (norm < 0 or NaN) add nothing.
 */
public final class BlurWindowGate {
    public enum State { INSUFFICIENT, OK, BLUR, LOST }

    private final int window;
    private final double dropFactor;
    private final double yShiftFactor;
    private final Deque<double[]> buf = new ArrayDeque<>();
    private boolean haveBaseline;
    private double baseNorm;
    private double baseY;

    public BlurWindowGate(int window, double dropFactor, double yShiftFactor) {
        if (window < 1 || dropFactor <= 1.0 || yShiftFactor <= 1.0) {
            throw new IllegalArgumentException("bad gate parameters");
        }
        this.window = window;
        this.dropFactor = dropFactor;
        this.yShiftFactor = yShiftFactor;
    }

    /** True once at least one valid sample was seen (sessions with none bypass the check). */
    public boolean hasSamples() { return !buf.isEmpty() || haveBaseline; }

    public State update(double norm, double yMean) {
        if (Double.isNaN(norm) || Double.isNaN(yMean) || norm < 0) return state();
        buf.addLast(new double[]{norm, yMean});
        if (buf.size() > window) buf.removeFirst();
        return state();
    }

    public State state() {
        if (buf.size() < window) return State.INSUFFICIENT;
        double mn = median(0), my = median(1);
        if (!haveBaseline) {
            haveBaseline = true;
            baseNorm = mn;
            baseY = my;
        }
        double hi = Math.max(my, baseY), lo = Math.max(Math.min(my, baseY), 0.1);
        if (hi / lo > yShiftFactor) return State.LOST;
        if (mn > baseNorm) {
            baseNorm = mn;
            baseY = my;
        }
        if (mn < baseNorm / dropFactor) return State.BLUR;
        return State.OK;
    }

    private double median(int k) {
        List<Double> v = new ArrayList<>(buf.size());
        for (double[] a : buf) v.add(a[k]);
        Collections.sort(v);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }
}
