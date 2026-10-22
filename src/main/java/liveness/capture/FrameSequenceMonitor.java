package liveness.capture;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;

public class FrameSequenceMonitor {

    public enum IntegrityViolation {
        FROZEN, LOOP_REPLAY, TIMESTAMP_GAP, TIMESTAMP_NONMONOTONIC, RESOLUTION_CHANGE
    }

    public static final int FREEZE_HAMMING_THRESHOLD = 2;
    public static final int LOOP_HAMMING_THRESHOLD = 6;

    private final int windowSize;
    private final long expectedIntervalMs;
    private final ArrayDeque<FrameFingerprint> buffer;

    public FrameSequenceMonitor(int windowSize, long expectedIntervalMs) {
        this.windowSize = windowSize;
        this.expectedIntervalMs = expectedIntervalMs;
        this.buffer = new ArrayDeque<>(windowSize);
    }

    public Optional<IntegrityViolation> addFrame(FrameFingerprint fp) {
        if (!buffer.isEmpty()) {
            FrameFingerprint last = buffer.peekLast();
            
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
        
        buffer.addLast(fp);
        if (buffer.size() > windowSize) {
            buffer.removeFirst();
        }
        
        if (buffer.size() > 1) {
            int consecutiveFrozen = 1;
            boolean foundLoop = false;
            
            Iterator<FrameFingerprint> it = buffer.descendingIterator();
            FrameFingerprint current = it.next(); // newly added frame
            
            boolean continuous = true;
            while (it.hasNext()) {
                FrameFingerprint prev = it.next();
                int dist = current.hammingDistance(prev);
                if (dist <= FREEZE_HAMMING_THRESHOLD) {
                    if (continuous) {
                        consecutiveFrozen++;
                    }
                } else {
                    continuous = false;
                }
                if (!continuous && dist <= LOOP_HAMMING_THRESHOLD) {
                    foundLoop = true;
                }
            }
            
            if (consecutiveFrozen >= windowSize && buffer.size() >= windowSize) {
                return Optional.of(IntegrityViolation.FROZEN);
            }
            if (foundLoop) {
                return Optional.of(IntegrityViolation.LOOP_REPLAY);
            }
        }

        return Optional.empty();
    }

    public void reset() {
        buffer.clear();
    }
}
