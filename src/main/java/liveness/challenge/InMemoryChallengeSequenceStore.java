package liveness.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory implementation of {@link ChallengeSequenceStore}.
 * <p>
 * TEST ONLY — production uses encrypted local DB (§6.3, §6.8).
 * Restarting the process resets all stored sequences.
 */
public final class InMemoryChallengeSequenceStore implements ChallengeSequenceStore {

    private final Map<String, List<List<String>>> store = new HashMap<>();

    @Override
    public List<List<String>> getPreviousSequences(String subjectId) {
        List<List<String>> sequences = store.get(subjectId);
        if (sequences == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(sequences);
    }

    @Override
    public void recordSequence(String subjectId, List<String> sequence) {
        store.computeIfAbsent(subjectId, k -> new ArrayList<>())
             .add(Collections.unmodifiableList(new ArrayList<>(sequence)));
    }

    @Override
    public void clear(String subjectId) {
        store.remove(subjectId);
    }
}
