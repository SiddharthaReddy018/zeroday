package liveness.engine;

import java.util.Optional;

public final class SpatialPadScorer {
    
    private final int minQualifyingFrames;
    private final double minQualityThreshold;
    private double sumWeightedScores = 0.0;
    private double sumQualityWeights = 0.0;
    private int totalFrames = 0;
    private int qualifyingFrames = 0;

    public SpatialPadScorer(int minQualifyingFrames, double minQualityThreshold) {
        this.minQualifyingFrames = minQualifyingFrames;
        this.minQualityThreshold = minQualityThreshold;
    }

    /** Convenience constructor with default quality threshold of 0.1. */
    public SpatialPadScorer(int minQualifyingFrames) {
        this(minQualifyingFrames, 0.1);
    }

    public void addFrame(int padScore, double qualityWeight) {
        totalFrames++;
        if (qualityWeight >= minQualityThreshold) {
            qualifyingFrames++;
            sumWeightedScores += (padScore * qualityWeight);
            sumQualityWeights += qualityWeight;
        }
    }

    public Optional<Integer> composite() {
        if (qualifyingFrames < minQualifyingFrames || sumQualityWeights <= 0.0) {
            return Optional.empty();
        }
        return Optional.of((int) Math.round(sumWeightedScores / sumQualityWeights));
    }

    public void reset() {
        sumWeightedScores = 0.0;
        sumQualityWeights = 0.0;
        totalFrames = 0;
        qualifyingFrames = 0;
    }

    public int frameCount() {
        return totalFrames;
    }

    public int qualifyingFrameCount() {
        return qualifyingFrames;
    }
}
