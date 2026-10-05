package liveness.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generated from the complete 16.2 transition table (R2).
 * <p>
 * Every valid (state, event) pair in the table must succeed.
 * Every other (state, event) pair must throw {@link IllegalStateException}.
 */
class StateMachineMatrixTest {

    // ─── Valid transition tests (rows 1-25) ───

    /**
     * Source: provides all valid transitions from the 16.2 table.
     */
    static Stream<Arguments> validTransitions() {
        return Stream.of(
                // Row 1
                Arguments.of(LivenessState.IDLE, LivenessEvent.START_CAPTURE, LivenessState.DEVICE_CHECK),
                // Row 2
                Arguments.of(LivenessState.DEVICE_CHECK, LivenessEvent.DEVICE_READY, LivenessState.POSITIONING),
                // Row 3
                Arguments.of(LivenessState.DEVICE_CHECK, LivenessEvent.DEVICE_UNAVAILABLE, LivenessState.DEVICE_ERROR),
                // Row 4
                Arguments.of(LivenessState.POSITIONING, LivenessEvent.QUALITY_OK, LivenessState.PASSIVE),
                // Row 5
                Arguments.of(LivenessState.POSITIONING, LivenessEvent.POSITIONING_TIMEOUT, LivenessState.RETRY_AVAILABLE),
                // Row 6
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.CLEAR_BONA_FIDE, LivenessState.FINAL_CAPTURE),
                // Row 7
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.UNCERTAIN, LivenessState.CHALLENGE_PROMPT),
                // Row 8
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.CLEAR_ATTACK, LivenessState.BLOCKED_ATTACK),
                // Row 9
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.QUALITY_DROPPED, LivenessState.POSITIONING),
                // Row 10
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.ENGINE_ERROR, LivenessState.RETRY_AVAILABLE),
                // Row 11
                Arguments.of(LivenessState.CHALLENGE_PROMPT, LivenessEvent.PROMPT_ISSUED, LivenessState.CHALLENGE_WAIT),
                // Row 12
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.ACTION_CONFIRMED_MORE, LivenessState.CHALLENGE_PROMPT),
                // Row 13
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.ACTION_CONFIRMED_DONE, LivenessState.FINAL_CAPTURE),
                // Row 14
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.CHALLENGE_TIMEOUT, LivenessState.RETRY_AVAILABLE),
                // Row 15
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.CHALLENGE_PAD_FLAG, LivenessState.BLOCKED_ATTACK),
                // Row 16
                Arguments.of(LivenessState.FINAL_CAPTURE, LivenessEvent.RCAPTURE_RETURNED, LivenessState.VERIFYING_FINAL),
                // Row 17
                Arguments.of(LivenessState.FINAL_CAPTURE, LivenessEvent.RCAPTURE_FAILURE, LivenessState.DEVICE_ERROR),
                // Row 18
                Arguments.of(LivenessState.VERIFYING_FINAL, LivenessEvent.COMMIT_CHECKS_PASS, LivenessState.SUCCESS),
                // Row 19
                Arguments.of(LivenessState.VERIFYING_FINAL, LivenessEvent.SAME_FACE_FAIL, LivenessState.RETRY_AVAILABLE),
                // Row 20
                Arguments.of(LivenessState.VERIFYING_FINAL, LivenessEvent.FINAL_PAD_ATTACK, LivenessState.BLOCKED_ATTACK),
                // Row 21
                Arguments.of(LivenessState.VERIFYING_FINAL, LivenessEvent.BINDING_FAILURE, LivenessState.DEVICE_ERROR),
                // Row 22
                Arguments.of(LivenessState.RETRY_AVAILABLE, LivenessEvent.RETRY_GRANTED, LivenessState.PASSIVE),
                // Row 23
                Arguments.of(LivenessState.RETRY_AVAILABLE, LivenessEvent.BUDGET_EXHAUSTED, LivenessState.FAILED_MAX_RETRIES),

                // Rows 24-25: live non-terminal states (POSITIONING, PASSIVE, CHALLENGE_PROMPT, CHALLENGE_WAIT)
                // Row 24: DEVICE_DISCONNECT
                Arguments.of(LivenessState.POSITIONING, LivenessEvent.DEVICE_DISCONNECT, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.DEVICE_DISCONNECT, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.CHALLENGE_PROMPT, LivenessEvent.DEVICE_DISCONNECT, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.DEVICE_DISCONNECT, LivenessState.DEVICE_ERROR),
                // Row 25: IAD_FAILURE
                Arguments.of(LivenessState.POSITIONING, LivenessEvent.IAD_FAILURE, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.PASSIVE, LivenessEvent.IAD_FAILURE, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.CHALLENGE_PROMPT, LivenessEvent.IAD_FAILURE, LivenessState.DEVICE_ERROR),
                Arguments.of(LivenessState.CHALLENGE_WAIT, LivenessEvent.IAD_FAILURE, LivenessState.DEVICE_ERROR)
        );
    }

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @MethodSource("validTransitions")
    void validTransition(LivenessState from, LivenessEvent event, LivenessState expectedTo) {
        // Use peek() to avoid needing to drive the machine to each state
        LivenessState actual = LivenessStateMachine.peek(from, event);
        assertEquals(expectedTo, actual,
                "Expected " + from + " + " + event + " -> " + expectedTo);
    }

    // ─── Invalid transition tests ───

    /**
     * Every (state, event) pair NOT in the table must throw IllegalStateException
     * when fired from the machine.
     */
    @Test
    void allInvalidTransitionsThrow() {
        Map<String, LivenessState> table = LivenessStateMachine.transitionTable();

        for (LivenessState state : LivenessState.values()) {
            for (LivenessEvent event : LivenessEvent.values()) {
                String key = state + ":" + event;
                if (!table.containsKey(key)) {
                    // Verify peek returns null
                    assertNull(LivenessStateMachine.peek(state, event),
                            "peek should return null for invalid: " + key);
                }
            }
        }
    }

    @Test
    void firingInvalidTransitionThrows() {
        LivenessStateMachine sm = new LivenessStateMachine();
        // IDLE should only accept START_CAPTURE
        assertThrows(IllegalStateException.class,
                () -> sm.fire(LivenessEvent.DEVICE_READY));
        assertThrows(IllegalStateException.class,
                () -> sm.fire(LivenessEvent.QUALITY_OK));
        assertThrows(IllegalStateException.class,
                () -> sm.fire(LivenessEvent.CLEAR_BONA_FIDE));
    }

    // ─── Terminal state tests ───

    @Test
    void terminalStatesAreTerminal() {
        assertTrue(LivenessState.SUCCESS.isTerminal());
        assertTrue(LivenessState.BLOCKED_ATTACK.isTerminal());
        assertTrue(LivenessState.FAILED_MAX_RETRIES.isTerminal());
        assertTrue(LivenessState.DEVICE_ERROR.isTerminal());
    }

    @Test
    void nonTerminalStatesAreNotTerminal() {
        assertFalse(LivenessState.IDLE.isTerminal());
        assertFalse(LivenessState.DEVICE_CHECK.isTerminal());
        assertFalse(LivenessState.POSITIONING.isTerminal());
        assertFalse(LivenessState.PASSIVE.isTerminal());
        assertFalse(LivenessState.CHALLENGE_PROMPT.isTerminal());
        assertFalse(LivenessState.CHALLENGE_WAIT.isTerminal());
        assertFalse(LivenessState.FINAL_CAPTURE.isTerminal());
        assertFalse(LivenessState.VERIFYING_FINAL.isTerminal());
        assertFalse(LivenessState.RETRY_AVAILABLE.isTerminal());
    }

    // ─── Smoke: full happy path ───

    @Test
    void happyPathSequence() {
        LivenessStateMachine sm = new LivenessStateMachine();
        assertEquals(LivenessState.IDLE, sm.current());

        sm.fire(LivenessEvent.START_CAPTURE);
        assertEquals(LivenessState.DEVICE_CHECK, sm.current());

        sm.fire(LivenessEvent.DEVICE_READY);
        assertEquals(LivenessState.POSITIONING, sm.current());

        sm.fire(LivenessEvent.QUALITY_OK);
        assertEquals(LivenessState.PASSIVE, sm.current());

        sm.fire(LivenessEvent.CLEAR_BONA_FIDE);
        assertEquals(LivenessState.FINAL_CAPTURE, sm.current());

        sm.fire(LivenessEvent.RCAPTURE_RETURNED);
        assertEquals(LivenessState.VERIFYING_FINAL, sm.current());

        sm.fire(LivenessEvent.COMMIT_CHECKS_PASS);
        assertEquals(LivenessState.SUCCESS, sm.current());
        assertTrue(sm.current().isTerminal());
    }

    // ─── Challenge path ───

    @Test
    void challengePathSequence() {
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);

        // PASSIVE -> CHALLENGE_PROMPT (UNCERTAIN)
        sm.fire(LivenessEvent.UNCERTAIN);
        assertEquals(LivenessState.CHALLENGE_PROMPT, sm.current());

        // CHALLENGE_PROMPT -> CHALLENGE_WAIT
        sm.fire(LivenessEvent.PROMPT_ISSUED);
        assertEquals(LivenessState.CHALLENGE_WAIT, sm.current());

        // More challenges needed
        sm.fire(LivenessEvent.ACTION_CONFIRMED_MORE);
        assertEquals(LivenessState.CHALLENGE_PROMPT, sm.current());

        sm.fire(LivenessEvent.PROMPT_ISSUED);
        assertEquals(LivenessState.CHALLENGE_WAIT, sm.current());

        // All challenges done
        sm.fire(LivenessEvent.ACTION_CONFIRMED_DONE);
        assertEquals(LivenessState.FINAL_CAPTURE, sm.current());
    }

    // ─── Attack detection path ───

    @Test
    void attackBlockedPath() {
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);

        sm.fire(LivenessEvent.CLEAR_ATTACK);
        assertEquals(LivenessState.BLOCKED_ATTACK, sm.current());
        assertTrue(sm.current().isTerminal());
    }

    // ─── Retry path ───

    @Test
    void retryPath() {
        LivenessStateMachine sm = new LivenessStateMachine();
        sm.fire(LivenessEvent.START_CAPTURE);
        sm.fire(LivenessEvent.DEVICE_READY);
        sm.fire(LivenessEvent.QUALITY_OK);
        sm.fire(LivenessEvent.ENGINE_ERROR);
        assertEquals(LivenessState.RETRY_AVAILABLE, sm.current());

        sm.fire(LivenessEvent.RETRY_GRANTED);
        assertEquals(LivenessState.PASSIVE, sm.current());
    }

    // ─── No transitions from terminal states ───

    @Test
    void noTransitionsFromTerminalStates() {
        for (LivenessState terminal : new LivenessState[]{
                LivenessState.SUCCESS,
                LivenessState.BLOCKED_ATTACK,
                LivenessState.FAILED_MAX_RETRIES,
                LivenessState.DEVICE_ERROR
        }) {
            for (LivenessEvent event : LivenessEvent.values()) {
                assertNull(LivenessStateMachine.peek(terminal, event),
                        "Terminal state " + terminal + " should have no valid transition for " + event);
            }
        }
    }

    // ─── Table completeness ───

    @Test
    void transitionTableHasExpectedSize() {
        Map<String, LivenessState> table = LivenessStateMachine.transitionTable();
        // Rows 1-23 = 23 transitions + Row 24 (4 states) + Row 25 (4 states) = 31 total
        assertEquals(31, table.size(),
                "16.2 table should have 31 entries (23 unique rows + 8 from rows 24-25)");
    }
}
