package liveness.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.Optional;

public class SpatialPadScorerTest {

    @Test
    public void testNotEnoughFrames() {
        SpatialPadScorer scorer = new SpatialPadScorer(3);
        scorer.addFrame(80, 1.0);
        scorer.addFrame(90, 1.0);
        Optional<Integer> comp = scorer.composite();
        Assertions.assertFalse(comp.isPresent());
    }

    @Test
    public void testWeightedAverage() {
        SpatialPadScorer scorer = new SpatialPadScorer(2);
        scorer.addFrame(50, 0.5);
        scorer.addFrame(100, 1.0);
        Optional<Integer> comp = scorer.composite();
        Assertions.assertTrue(comp.isPresent());
        Assertions.assertEquals(83, comp.get().intValue());
    }

    @Test
    public void testLowQualityExcluded() {
        SpatialPadScorer scorer = new SpatialPadScorer(2);
        scorer.addFrame(100, 1.0);
        scorer.addFrame(0, 0.0); // should have 0 weight
        scorer.addFrame(50, 1.0);
        Optional<Integer> comp = scorer.composite();
        Assertions.assertTrue(comp.isPresent());
        Assertions.assertEquals(75, comp.get().intValue());
    }

    @Test
    public void testReset() {
        SpatialPadScorer scorer = new SpatialPadScorer(2);
        scorer.addFrame(80, 1.0);
        scorer.addFrame(90, 1.0);
        scorer.reset();
        scorer.addFrame(100, 1.0);
        Assertions.assertFalse(scorer.composite().isPresent());
    }
}
