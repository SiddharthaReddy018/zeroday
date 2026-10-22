package liveness.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for PAD metrics computation (§12.1, §24.1).
 */
public class PadMetricsTest {

    private List<ScoredSample> buildSamples() {
        List<ScoredSample> samples = new ArrayList<>();
        // Bona fide: mostly high scores
        for (int i = 0; i < 50; i++) {
            samples.add(new ScoredSample(75 + (i % 25), "bona_fide", "", "bf_" + i));
        }
        // Print attacks: mostly low scores
        for (int i = 0; i < 30; i++) {
            samples.add(new ScoredSample(10 + (i % 20), "attack", "print", "ap_" + i));
        }
        // Screen attacks: mixed scores (some break through)
        for (int i = 0; i < 20; i++) {
            int score = (i < 5) ? 75 : 15 + (i % 10); // 5 break through threshold
            samples.add(new ScoredSample(score, "attack", "screen", "as_" + i));
        }
        // Video attacks: low scores
        for (int i = 0; i < 10; i++) {
            samples.add(new ScoredSample(5 + (i % 10), "attack", "video", "av_" + i));
        }
        return samples;
    }

    @Test
    public void apcerPerSpecies() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        PadMetrics.SpeciesResult printApcer = metrics.apcer("print");
        assertNotNull(printApcer);
        assertEquals("print", printApcer.species());
        assertEquals(30, printApcer.count());
        // All print scores are 10-29, threshold is 70 → all correctly classified
        assertEquals(0.0, printApcer.rate(), 0.001);

        PadMetrics.SpeciesResult screenApcer = metrics.apcer("screen");
        assertNotNull(screenApcer);
        assertEquals(20, screenApcer.count());
        // 5 screen attacks have score 75 (>= 70) → APCER = 5/20 = 0.25
        assertEquals(0.25, screenApcer.rate(), 0.001);

        PadMetrics.SpeciesResult videoApcer = metrics.apcer("video");
        assertEquals(0.0, videoApcer.rate(), 0.001);
    }

    @Test
    public void apcerAllSpecies() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        Map<String, PadMetrics.SpeciesResult> all = metrics.apcerAllSpecies();
        assertEquals(3, all.size());
        assertTrue(all.containsKey("print"));
        assertTrue(all.containsKey("screen"));
        assertTrue(all.containsKey("video"));
    }

    @Test
    public void worstSpecies() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        PadMetrics.SpeciesResult worst = metrics.worstSpeciesApcer();
        assertNotNull(worst);
        assertEquals("screen", worst.species()); // screen has highest APCER (0.25)
    }

    @Test
    public void bpcer() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        PadMetrics.RateResult bpcer = metrics.bpcer();
        assertEquals(50, bpcer.count());
        // Bona fide scores are 75-99; at threshold 70 all pass → BPCER = 0
        assertEquals(0.0, bpcer.rate(), 0.001);
    }

    @Test
    public void bpcerWithSomeMisclassified() {
        List<ScoredSample> samples = new ArrayList<>();
        // 10 bona fide, 3 below threshold
        for (int i = 0; i < 7; i++) {
            samples.add(new ScoredSample(80, "bona_fide", "", "bf_" + i));
        }
        for (int i = 0; i < 3; i++) {
            samples.add(new ScoredSample(50, "bona_fide", "", "bf_low_" + i));
        }
        PadMetrics metrics = new PadMetrics(samples, 70);
        assertEquals(0.3, metrics.bpcer().rate(), 0.001);
    }

    @Test
    public void detCurve() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        List<PadMetrics.DetPoint> curve = metrics.detCurve();
        assertEquals(101, curve.size()); // thresholds 0-100

        // At threshold 0: all accepted → APCER=1.0, BPCER=0.0
        assertEquals(1.0, curve.get(0).apcer(), 0.001);
        assertEquals(0.0, curve.get(0).bpcer(), 0.001);

        // At threshold 100: all rejected → APCER~0, BPCER~1
        PadMetrics.DetPoint last = curve.get(100);
        assertTrue(last.bpcer() > 0.5);
    }

    @Test
    public void deer() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        PadMetrics.DetPoint deer = metrics.deer();
        assertNotNull(deer);
        // At D-EER, APCER ≈ BPCER
        assertTrue(Math.abs(deer.apcer() - deer.bpcer()) < 0.15,
                "D-EER should have APCER ≈ BPCER");
    }

    @Test
    public void wilsonCIsPresent() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        PadMetrics.SpeciesResult r = metrics.apcer("print");
        assertNotNull(r.ci());
        assertTrue(r.ci().lower() >= 0.0);
        assertTrue(r.ci().upper() <= 1.0);
    }

    @Test
    public void ruleOf30() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);

        assertEquals(50, metrics.bonafideSubjectCount());
        assertTrue(metrics.ruleOf30Satisfied());
    }

    @Test
    public void deerHasCorrectLabel() {
        List<ScoredSample> samples = buildSamples();
        PadMetrics metrics = new PadMetrics(samples, 70);
        PadMetrics.DetPoint deer = metrics.deer();
        assertNotNull(deer);
        assertEquals("D-EER", deer.label());
    }

    @Test
    public void emptyDatasetReturnsNaNForRates() {
        PadMetrics emptyMetrics = new PadMetrics(new ArrayList<>(), 70);
        assertTrue(Double.isNaN(emptyMetrics.bpcer().rate()));
        assertTrue(Double.isNaN(emptyMetrics.apcer("print").rate()));
        assertNull(emptyMetrics.worstSpeciesApcer());
        assertEquals(0, emptyMetrics.bonafideSubjectCount());
        assertFalse(emptyMetrics.ruleOf30Satisfied());
    }
}
