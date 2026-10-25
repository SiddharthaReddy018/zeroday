package liveness.capture;

import liveness.policy.LivenessConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class FrameSequenceMonitor {

    public enum IntegrityViolation {
        FROZEN, LOOP_REPLAY, TIMESTAMP_GAP, TIMESTAMP_NONMONOTONIC, RESOLUTION_CHANGE
    }

    public static final int FREEZE_HAMMING_THRESHOLD = 2;
    public static final int LOOP_HAMMING_THRESHOLD = 6;
    /** Default minimum motion bits within a segment for loop detection. */
    public static final int DEFAULT_LOOP_MIN_SEGMENT_MOTION_BITS = 3;

    private final int windowSize;
    private final long expectedIntervalMs;
    private final int loopK;
    private final int loopHammingThreshold;
    private final int freezeThreshold;
    private final int loopMinSegmentMotionBits;
    private final List<FrameFingerprint> buffer;
    /** True until the first valid inter-frame interval is seen. Skips the TIMESTAMP_GAP check on
     *  the very first pair so a cold-start camera (whose first two frames arrive after a long idle)
     *  does not immediately fail with IAD_FAILURE: TIMESTAMP_GAP. */
    private boolean warmingUp = true;

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs) {
        this(windowSize, expectedIntervalMs, 5, LOOP_HAMMING_THRESHOLD, Math.min(windowSize, 5),
                DEFAULT_LOOP_MIN_SEGMENT_MOTION_BITS);
    }

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs, LivenessConfig config) {
        this(windowSize, expectedIntervalMs,
                config != null ? config.loopSequenceLengthK() : 5,
                config != null ? config.loopHammingThreshold() : LOOP_HAMMING_THRESHOLD,
                config != null ? config.freezeConsecutiveFrames() : Math.min(windowSize, 5),
                config != null ? config.loopMinSegmentMotionBits() : DEFAULT_LOOP_MIN_SEGMENT_MOTION_BITS);
    }

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs, int loopK,
                                int loopHammingThreshold, int freezeThreshold) {
        this(windowSize, expectedIntervalMs, loopK, loopHammingThreshold, freezeThreshold,
                DEFAULT_LOOP_MIN_SEGMENT_MOTION_BITS);
    }

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs, int loopK,
                                int loopHammingThreshold, int freezeThreshold,
                                int loopMinSegmentMotionBits) {
        this.windowSize = windowSize;
        this.expectedIntervalMs = expectedIntervalMs;
        this.loopK = Math.max(5, loopK);
        this.loopHammingThreshold = loopHammingThreshold;
        this.freezeThreshold = Math.max(2, freezeThreshold);
        this.loopMinSegmentMotionBits = Math.max(0, loopMinSegmentMotionBits);
        this.buffer = new ArrayList<>(windowSize + 1);
    }

    public Optional<IntegrityViolation> addFrame(FrameFingerprint fp) {
        if (!buffer.isEmpty()) {
            FrameFingerprint last = buffer.get(buffer.size() - 1);

            if (last.getWidthPx() != fp.getWidthPx()) {
                return Optional.of(IntegrityViolation.RESOLUTION_CHANGE);
            }

            if (fp.getTimestampMs() < last.getTimestampMs()) {
                return Optional.of(IntegrityViolation.TIMESTAMP_NONMONOTONIC);
            }

            if (fp.getTimestampMs() - last.getTimestampMs() > 2 * expectedIntervalMs) {
                // Allow a one-time large gap on the very first inter-frame check when the camera
                // was idle for more than 5 seconds (cold-start). Mid-session gaps are always rejected.
                boolean coldStart = warmingUp
                        && (fp.getTimestampMs() - last.getTimestampMs() > 5000L);
                if (!coldStart) {
                    return Optional.of(IntegrityViolation.TIMESTAMP_GAP);
                }
            }
            warmingUp = false;
        }

        buffer.add(fp);
        if (buffer.size() > windowSize) {
            buffer.remove(0);
        }

        if (buffer.size() > 1) {
            // 1. Freeze detection: byte-identical consecutive frames (content digest only)
            int consecutiveFrozen = 1;
            for (int i = buffer.size() - 1; i > 0; i--) {
                FrameFingerprint curr = buffer.get(i);
                FrameFingerprint prev = buffer.get(i - 1);
                if (isByteIdentical(curr, prev)) {
                    consecutiveFrozen++;
                } else {
                    break;
                }
            }

            if (consecutiveFrozen >= freezeThreshold && buffer.size() >= freezeThreshold) {
                return Optional.of(IntegrityViolation.FROZEN);
            }

            // 2. Loop replay detection: sequence repetition of K frames (K >= 5)
            // Redesigned per R2: a candidate repeated segment must contain real motion,
            // and the intermediate frames must differ from the matched segment.
            int n = buffer.size();
            for (int s = 0; s <= n - 2 * loopK; s++) {
                int gap = (n - loopK) - (s + loopK);
                if (gap < 1) {
                    continue; // Must be non-adjacent
                }
                int matchedPairs = 0;
                for (int i = 0; i < loopK; i++) {
                    FrameFingerprint currFrame = buffer.get(n - loopK + i);
                    FrameFingerprint prevFrame = buffer.get(s + i);
                    int dist = currFrame.hammingDistance(prevFrame);
                    if (dist <= loopHammingThreshold) {
                        matchedPairs++;
                    }
                }
                if (matchedPairs > loopK / 2) {
                    // Check 1: The matched segment must contain real motion.
                    // Measure the max consecutive Hamming distance within the earlier segment.
                    if (!segmentHasMotion(s, s + loopK)) {
                        continue; // Static scene -- not a loop
                    }

                    // Check 2: At least one intermediate frame must differ from the matched
                    // segment (i.e., be farther than loopHammingThreshold from every frame in
                    // the segment). A scene that matches everywhere is not a loop.
                    if (!intermediateFramesDiffer(s, s + loopK, n - loopK)) {
                        continue; // Scene matches everywhere -- not a loop
                    }

                    return Optional.of(IntegrityViolation.LOOP_REPLAY);
                }
            }

        }

        return Optional.empty();
    }

    /**
     * Returns true if the segment [start, end) contains real motion: the maximum
     * pairwise Hamming distance between consecutive frames exceeds
     * loopMinSegmentMotionBits.
     */
    private boolean segmentHasMotion(int start, int end) {
        int maxDist = 0;
        for (int i = start; i < end - 1; i++) {
            int d = buffer.get(i).hammingDistance(buffer.get(i + 1));
            if (d > maxDist) {
                maxDist = d;
            }
        }
        return maxDist > loopMinSegmentMotionBits;
    }

    /**
     * Returns true if at least one frame between the two matched segments differs
     * from every frame in the first segment by more than loopHammingThreshold.
     * The intermediate region is [segEnd, replayStart).
     */
    private boolean intermediateFramesDiffer(int segStart, int segEnd, int replayStart) {
        for (int i = segEnd; i < replayStart; i++) {
            boolean differFromAll = true;
            for (int j = segStart; j < segEnd; j++) {
                if (buffer.get(i).hammingDistance(buffer.get(j)) <= loopHammingThreshold) {
                    differFromAll = false;
                    break;
                }
            }
            if (differFromAll) {
                return true;
            }
        }
        return false;
    }

    /**
     * Freeze detection uses byte-identical content digest only.
     * If contentDigest is null on either frame, returns false (not decidable).
     * Does NOT fall back to pHash equality for FROZEN decisions.
     */
    private static boolean isByteIdentical(FrameFingerprint f1, FrameFingerprint f2) {
        if (f1 == null || f2 == null) return false;
        byte[] d1 = f1.getContentDigest();
        byte[] d2 = f2.getContentDigest();
        if (d1 != null && d2 != null) {
            return Arrays.equals(d1, d2);
        }
        // contentDigest null: not decidable, do NOT fall back to pHash equality
        return false;
    }

    public void reset() {
        buffer.clear();
    }

    public int getWindowSize() { return windowSize; }
    public int getLoopK() { return loopK; }
    public int getLoopHammingThreshold() { return loopHammingThreshold; }
    public int getFreezeThreshold() { return freezeThreshold; }
    public int getLoopMinSegmentMotionBits() { return loopMinSegmentMotionBits; }
}
