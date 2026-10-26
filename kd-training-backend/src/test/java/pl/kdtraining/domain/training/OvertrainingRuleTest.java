package pl.kdtraining.domain.training;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OvertrainingRuleTest {

    @Test
    void shouldFireOnExactlySixMaximalSessions() {
        assertTrue(OvertrainingRule.isOvertrained(List.of(9, 9, 10, 9, 9, 10)));
    }

    @Test
    void shouldNotFireOnFive() {
        // Five hard sessions is a hard week, not a pattern.
        assertFalse(OvertrainingRule.isOvertrained(List.of(10, 10, 10, 10, 10)));
    }

    @Test
    void shouldNotFireWhenOneSessionInTheWindowWasEasier() {
        assertFalse(OvertrainingRule.isOvertrained(List.of(9, 9, 8, 9, 9, 9)));
        // Boundary: 8 is below the threshold, 9 is at it
        assertFalse(OvertrainingRule.isOvertrained(List.of(8, 9, 9, 9, 9, 9)));
    }

    @Test
    void shouldLookOnlyAtTheMostRecentSix() {
        // Older easy sessions are irrelevant once six hard ones follow them.
        assertTrue(OvertrainingRule.isOvertrained(List.of(9, 9, 9, 9, 9, 9, 3, 2, 1)));
        // And a recent easy session clears it even if six hard ones sit behind it.
        assertFalse(OvertrainingRule.isOvertrained(List.of(4, 9, 9, 9, 9, 9, 9)));
    }

    @Test
    void shouldNeverFireWithoutEnoughData() {
        assertFalse(OvertrainingRule.isOvertrained(List.of()));
        assertFalse(OvertrainingRule.isOvertrained(null));
        assertFalse(OvertrainingRule.isOvertrained(List.of(10)));
    }
}
