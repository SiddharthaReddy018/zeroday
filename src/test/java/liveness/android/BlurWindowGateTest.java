package liveness.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlurWindowGateTest {
    private static BlurWindowGate.State feed(BlurWindowGate g, int n, double norm, double y) {
        BlurWindowGate.State s = null;
        for (int i = 0; i < n; i++) s = g.update(norm, y);
        return s;
    }

    @Test
    void insufficientUntilWindowFull() {
        BlurWindowGate g = new BlurWindowGate(30, 2.0, 1.5);
        assertEquals(BlurWindowGate.State.INSUFFICIENT, feed(g, 29, 0.2, 10));
        assertEquals(BlurWindowGate.State.OK, feed(g, 1, 0.2, 10));
    }

    @Test
    void dropAgainstBaselineIsBlur() {
        BlurWindowGate g = new BlurWindowGate(30, 2.0, 1.5);
        assertEquals(BlurWindowGate.State.OK, feed(g, 30, 0.20, 10));
        assertEquals(BlurWindowGate.State.BLUR, feed(g, 30, 0.06, 10));
        assertEquals(BlurWindowGate.State.OK, feed(g, 30, 0.19, 10));
    }

    @Test
    void lightJumpIsLostNotBlur() {
        BlurWindowGate g = new BlurWindowGate(30, 2.0, 1.5);
        feed(g, 30, 0.023, 80);
        assertEquals(BlurWindowGate.State.LOST, feed(g, 30, 0.061, 9));
    }

    @Test
    void invalidSamplesAddNothing() {
        BlurWindowGate g = new BlurWindowGate(30, 2.0, 1.5);
        assertFalse(g.hasSamples());
        for (int i = 0; i < 100; i++) g.update(-1.0, 10);
        g.update(Double.NaN, 10);
        assertFalse(g.hasSamples());
        assertEquals(BlurWindowGate.State.INSUFFICIENT, g.state());
        g.update(0.2, 10);
        assertTrue(g.hasSamples());
    }

    @Test
    void badParametersRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BlurWindowGate(0, 2.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new BlurWindowGate(30, 1.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new BlurWindowGate(30, 2.0, 1.0));
    }
}
