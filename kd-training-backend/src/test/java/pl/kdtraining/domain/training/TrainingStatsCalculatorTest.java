package pl.kdtraining.domain.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Every window is tested against a fixed "today", so these do not drift with the calendar. */
class TrainingStatsCalculatorTest {

    /** A Wednesday, mid-month, mid-year — no boundary is accidentally special. */
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 16);

    @Test
    void shouldCountOnlyNonZeroDaysInTheHeatmap() {
        var heatmap = TrainingStatsCalculator.heatmap(
                List.of(TODAY, TODAY, TODAY.minusDays(3)), TODAY);

        assertEquals(2, heatmap.size());
        assertEquals(2, heatmap.get(TODAY));
        assertEquals(1, heatmap.get(TODAY.minusDays(3)));
    }

    @Test
    void shouldDropActivitiesOlderThanTheHeatmapWindow() {
        var heatmap = TrainingStatsCalculator.heatmap(
                List.of(TODAY.minusDays(364), TODAY.minusDays(365)), TODAY);

        assertEquals(1, heatmap.size());
        assertTrue(heatmap.containsKey(TODAY.minusDays(364)));
    }

    @Test
    void shouldNotBreakTheStreakForAnEmptyCurrentWeek() {
        // Monday morning: nobody has trained this week yet. Zeroing the streak for that punishes
        // somebody for the calendar rather than for their training.
        List<LocalDate> dates = List.of(
                TODAY.minusWeeks(1), TODAY.minusWeeks(2), TODAY.minusWeeks(3));

        assertEquals(3, TrainingStatsCalculator.currentStreakWeeks(dates, TODAY));
    }

    @Test
    void shouldCountTheCurrentWeekWhenItHasActivity() {
        List<LocalDate> dates = List.of(TODAY, TODAY.minusWeeks(1), TODAY.minusWeeks(2));

        assertEquals(3, TrainingStatsCalculator.currentStreakWeeks(dates, TODAY));
    }

    @Test
    void shouldEndTheStreakAtTheFirstFullyMissedWeek() {
        // Grace covers the current week only — a gap further back genuinely ends the run.
        List<LocalDate> dates = List.of(
                TODAY.minusWeeks(1), TODAY.minusWeeks(2), TODAY.minusWeeks(4));

        assertEquals(2, TrainingStatsCalculator.currentStreakWeeks(dates, TODAY));
    }

    @Test
    void shouldReportZeroStreakAfterTwoEmptyWeeks() {
        assertEquals(0, TrainingStatsCalculator.currentStreakWeeks(
                List.of(TODAY.minusWeeks(2)), TODAY));
    }

    @Test
    void shouldFindTheLongestRunEver() {
        List<LocalDate> dates = List.of(
                TODAY.minusWeeks(1),
                TODAY.minusWeeks(10), TODAY.minusWeeks(11), TODAY.minusWeeks(12), TODAY.minusWeeks(13));

        assertEquals(4, TrainingStatsCalculator.bestStreakWeeks(dates));
    }

    @Test
    void shouldCountSeveralActivitiesInOneWeekAsOneWeek() {
        List<LocalDate> dates = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        assertEquals(1, TrainingStatsCalculator.bestStreakWeeks(dates));
    }

    @Test
    void shouldAverageOverCompleteMonthsOnly() {
        // The current month is partial: including it would make the average sag every 1st and creep
        // back up over the month, which reads as a decline that never happened.
        List<LocalDate> dates = List.of(
                LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 2), LocalDate.of(2027, 6, 3),
                LocalDate.of(2027, 5, 4), LocalDate.of(2027, 5, 5),
                LocalDate.of(2027, 4, 6), LocalDate.of(2027, 4, 7));

        // April + May = 4 activities over 2 full months of history
        assertEquals(2.0, TrainingStatsCalculator.averagePerMonth(dates, TODAY));
    }

    @Test
    void shouldNotAverageSomebodyAgainstMonthsBeforeTheyStarted() {
        // One month of history must not be divided by the full six-month window.
        List<LocalDate> dates = List.of(
                LocalDate.of(2027, 5, 4), LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 6));

        assertEquals(3.0, TrainingStatsCalculator.averagePerMonth(dates, TODAY));
    }

    @Test
    void shouldReportNoAverageBeforeTheFirstCompleteMonth() {
        assertNull(TrainingStatsCalculator.averagePerMonth(List.of(LocalDate.of(2027, 6, 2)), TODAY));
        assertNull(TrainingStatsCalculator.averagePerMonth(List.of(), TODAY));
    }

    @Test
    void shouldReportNoAttendanceRateWhenNothingWasPlanned() {
        // 0% would claim a failure that never happened.
        assertNull(TrainingStatsCalculator.attendancePercent(0, 0));
        assertEquals(100, TrainingStatsCalculator.attendancePercent(4, 0));
        assertEquals(75, TrainingStatsCalculator.attendancePercent(3, 1));
    }

    @Test
    void shouldSplitRpeIntoBandsOnTheDocumentedBoundaries() {
        var bands = TrainingStatsCalculator.rpeDistribution(List.of(1, 4, 5, 7, 8, 10));

        assertEquals(2, bands.get("light"));   // 1, 4
        assertEquals(2, bands.get("medium"));  // 5, 7
        assertEquals(2, bands.get("hard"));    // 8, 10
    }

    @Test
    void shouldRoundAveragesToOneDecimal() {
        assertEquals(6.7, TrainingStatsCalculator.average(List.of(6, 7, 7)));
        assertNull(TrainingStatsCalculator.average(List.of()));
    }

    @Test
    void shouldHandleTheTurnOfTheYearInStreaks() {
        // ISO week-based year: 2027-01-01 is a Friday and belongs to the week that started in 2026.
        LocalDate january = LocalDate.of(2027, 1, 6);
        List<LocalDate> dates = List.of(january, january.minusWeeks(1), january.minusWeeks(2));

        assertEquals(3, TrainingStatsCalculator.currentStreakWeeks(dates, january));
    }
}
