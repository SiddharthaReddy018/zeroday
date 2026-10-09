package liveness.challenge;

import java.util.List;

/**
 * Interface for persisted challenge sequence history (§6.3).
 * <p>
 * Production: encrypted local DB.
 * Test: {@link InMemoryChallengeSequenceStore}.
 */
public interface ChallengeSequenceStore {

    /**
     * Returns all previous session challenge sequences for a subject.
     *
     * @param subjectId the subject identifier
     * @return list of sequences (each sequence is a list of action strings)
     */
    List<List<String>> getPreviousSequences(String subjectId);

    /**
     * Records a completed session's challenge sequence.
     *
     * @param subjectId the subject identifier
     * @param sequence  the challenge actions performed in this session
     */
    void recordSequence(String subjectId, List<String> sequence);

    /**
     * Clears all stored sequences for a subject.
     * Used only in tests or data-retention cleanup.
     */
    void clear(String subjectId);
}
