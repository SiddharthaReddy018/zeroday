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

    private final int windowSize;
    private final long expectedIntervalMs;
    private final int loopK;
    private final int loopHammingThreshold;
    private final int freezeThreshold;
    private final List<FrameFingerprint> buffer;

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs) {
        this(windowSize, expectedIntervalMs, 5, LOOP_HAMMING_THRESHOLD, windowSize);
    }

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs, LivenessConfig config) {
        this(windowSize, expectedIntervalMs,
                config != null ? config.loopSequenceLengthK() : 5,
                config != null ? config.loopHammingThreshold() : LOOP_HAMMING_THRESHOLD,
                config != null ? config.freezeConsecutiveFrames() : windowSize);
    }

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs, int loopK, int loopHammingThreshold, int freezeThreshold) {
        this.windowSize = windowSize;
        this.expectedIntervalMs = expectedIntervalMs;
        this.loopK = Math.max(5, loopK);
        this.loopHammingThreshold = loopHammingThreshold;
        this.freezeThreshold = Math.max(2, freezeThreshold);
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
                return Optional.of(IntegrityViolation.TIMESTAMP_GAP);
            }
        }

        buffer.add(fp);
        if (buffer.size() > windowSize) {
            buffer.remove(0);
        }

        if (buffer.size() > 1) {
            // 1. Freeze detection: byte-identical consecutive frames
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
            int n = buffer.size();
            // Compare last K frames with earlier K-frame segment separated by at least K frames and non-adjacent
            for (int s = 0; s <= n - 2 * loopK; s++) {
                int gap = (n - loopK) - (s + loopK);
                if (gap < 1) {
                    continue; // Must be non-adjacent (touching/contiguous segments do not count as loop replay)
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
                    return Optional.of(IntegrityViolation.LOOP_REPLAY);
                }
            }

        }

        return Optional.empty();
    }

    private static boolean isByteIdentical(FrameFingerprint f1, FrameFingerprint f2) {
        if (f1 == null || f2 == null) return false;
        byte[] d1 = f1.getContentDigest();
        byte[] d2 = f2.getContentDigest();
        if (d1 != null && d2 != null) {
            return Arrays.equals(d1, d2);
        }
        return f1.getPHash() == f2.getPHash();
    }

    public void reset() {
        buffer.clear();
    }

    public int getWindowSize() { return windowSize; }
    public int getLoopK() { return loopK; }
    public int getLoopHammingThreshold() { return loopHammingThreshold; }
    public int getFreezeThreshold() { return freezeThreshold; }
}

