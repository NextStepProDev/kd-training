package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The arithmetic behind the client's statistics — pure, so every window and threshold can be tested
 * against a fixed "today" instead of whatever day the suite happens to run on.
 * <p>
 * One activity = one completed personal training on a date. Group sessions are counted separately by
 * the caller, because attendance there is not something anyone ticks off.
 */
public final class TrainingStatsCalculator {

    /** A year of squares. Longer would not fit on a screen; shorter loses the seasonal shape. */
    public static final int HEATMAP_DAYS = 365;

    /** Only FULL months average honestly — including the current, partial one drags it down daily. */
    public static final int AVG_WINDOW_MONTHS = 6;

    public static final int RPE_RECENT_DAYS = 30;
    public static final int RPE_DISTRIBUTION_DAYS = 90;

    /**
     * Attendance over a rolling window rather than all time. "62% since forever" is both
     * discouraging and unfixable — a bad month a year ago would weigh on it permanently.
     */
    public static final int ATTENDANCE_DAYS = 90;

    private TrainingStatsCalculator() {}

    /** Activities per day, for the heatmap. Only non-zero days, so the payload stays small. */
    public static Map<LocalDate, Integer> heatmap(List<LocalDate> completedDates, LocalDate today) {
        LocalDate from = today.minusDays(HEATMAP_DAYS - 1L);
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (LocalDate date : completedDates) {
            if (!date.isBefore(from) && !date.isAfter(today)) {
                counts.merge(date, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Consecutive ISO weeks with at least one activity, counting back from this week.
     * <p>
     * An empty CURRENT week does not break the run: on a Monday morning nobody has trained yet, and
     * resetting someone's streak to zero for that is punishing them for the calendar.
     */
    public static int currentStreakWeeks(List<LocalDate> completedDates, LocalDate today) {
        Set<String> weeks = weekKeys(completedDates);
        int streak = 0;
        LocalDate cursor = today;
        if (!weeks.contains(weekKey(cursor))) {
            // Grace: skip the current week and measure the run that ended last week.
            cursor = cursor.minusWeeks(1);
        }
        while (weeks.contains(weekKey(cursor))) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    /**
     * Longest run of consecutive active ISO weeks among the dates it is given.
     * <p>
     * The caller passes a rolling year, so in practice this is the best streak of the last twelve
     * months rather than of all time — a run that ended two years ago has already fallen out. Said
     * plainly here because the shorter phrasing ("ever recorded") claimed something the input
     * cannot support; making it truly lifetime means loading every completed date, which is a
     * separate decision from the one that fixed the lifetime total.
     */
    public static int bestStreakWeeks(List<LocalDate> completedDates) {
        TreeSet<LocalDate> mondays = new TreeSet<>();
        for (LocalDate date : completedDates) {
            mondays.add(date.with(java.time.DayOfWeek.MONDAY));
        }
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate monday : mondays) {
            run = (previous != null && previous.plusWeeks(1).equals(monday)) ? run + 1 : 1;
            previous = monday;
            best = Math.max(best, run);
        }
        return best;
    }

    /**
     * Average activities per month over the last {@link #AVG_WINDOW_MONTHS} COMPLETE months, or null
     * until there is at least one such month. The window shrinks to the client's own history, so
     * somebody two months in is not averaged against four months of zeroes.
     */
    @Nullable
    public static Double averagePerMonth(List<LocalDate> completedDates, LocalDate today) {
        if (completedDates.isEmpty()) {
            return null;
        }
        YearMonth lastFull = YearMonth.from(today).minusMonths(1);
        YearMonth firstActivity = YearMonth.from(completedDates.stream().min(LocalDate::compareTo).orElseThrow());
        YearMonth requested = lastFull.minusMonths(AVG_WINDOW_MONTHS - 1L);
        // Shrink the window to the client's own history, so somebody two months in is not averaged
        // against four months of zeroes they were never around for.
        final YearMonth windowStart = requested.isBefore(firstActivity) ? firstActivity : requested;
        if (windowStart.isAfter(lastFull)) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(windowStart, lastFull) + 1;
        long count = completedDates.stream()
                .map(YearMonth::from)
                .filter(m -> !m.isBefore(windowStart) && !m.isAfter(lastFull))
                .count();
        return Math.round((count * 10.0) / months) / 10.0;
    }

    /**
     * Share of planned sessions actually done, in the trailing window. Counts only 1-on-1 trainings:
     * a group session nobody ticks off would otherwise read as a miss.
     *
     * @return null when nothing was planned in the window — 0% would imply a failure that never happened
     */
    @Nullable
    public static Integer attendancePercent(int completed, int missed) {
        int total = completed + missed;
        return total == 0 ? null : Math.round((completed * 100f) / total);
    }

    /** Effort bands as the coach reads them: easy / working / hard. */
    public static Map<String, Integer> rpeDistribution(List<Integer> rpeValues) {
        Map<String, Integer> bands = new LinkedHashMap<>();
        bands.put("light", 0);
        bands.put("medium", 0);
        bands.put("hard", 0);
        for (Integer rpe : rpeValues) {
            if (rpe == null) continue;
            String band = rpe <= 4 ? "light" : rpe <= 7 ? "medium" : "hard";
            bands.merge(band, 1, Integer::sum);
        }
        return bands;
    }

    @Nullable
    public static Double average(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = values.stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        long count = values.stream().filter(java.util.Objects::nonNull).count();
        return count == 0 ? null : Math.round((sum * 10.0) / count) / 10.0;
    }

    private static Set<String> weekKeys(List<LocalDate> dates) {
        Set<String> keys = new java.util.HashSet<>();
        for (LocalDate date : dates) {
            keys.add(weekKey(date));
        }
        return keys;
    }

    /** ISO week-based year, so the last days of December belong to the right week. */
    private static String weekKey(LocalDate date) {
        return date.get(IsoFields.WEEK_BASED_YEAR) + "-" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
}
