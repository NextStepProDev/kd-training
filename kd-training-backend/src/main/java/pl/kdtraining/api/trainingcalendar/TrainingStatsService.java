package pl.kdtraining.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.kdtraining.domain.training.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's training statistics.
 *
 * <h2>Deliberately not cached</h2>
 * Undoing a completion, deleting a training or back-dating one all have to move the numbers
 * immediately — a coach who unticks a session and still sees it counted stops trusting the panel.
 * The cost is two indexed queries plus one in-memory pass, which is cheap enough that a cache would
 * only buy staleness.
 *
 * <p>All arithmetic lives in {@link TrainingStatsCalculator}, which takes "today" as an argument so
 * every window can be tested against a fixed date.
 */
@Service
public class TrainingStatsService {

    private final PersonalTrainingRepository trainingRepository;
    private final TrainingAccessService access;

    public TrainingStatsService(PersonalTrainingRepository trainingRepository,
                                TrainingAccessService access) {
        this.trainingRepository = trainingRepository;
        this.access = access;
    }

    /**
     * @param includeOvertraining the overtraining signal is for the coach's eyes only. Showing a
     *                            client "you are overreaching" turns a conversation starter into a
     *                            verdict delivered by a web page.
     */
    @Transactional(readOnly = true)
    public TrainingStatsResponse stats(UUID athleteId, boolean includeOvertraining) {
        access.requireAthlete(athleteId);
        return build(athleteId, LocalDateTime.now(), includeOvertraining);
    }

    /**
     * Package-private with an explicit "now" so tests are not at the mercy of the clock.
     * <p>
     * It must be a {@code LocalDateTime}, not a date: an untimed training runs until the end of its
     * day, so treating today as already elapsed would count a session the client still has all day
     * to do as missed — and greet them with 0% attendance over breakfast.
     */
    TrainingStatsResponse build(UUID athleteId, LocalDateTime now, boolean includeOvertraining) {
        LocalDate today = now.toLocalDate();
        LocalDate yearAgo = today.minusDays(TrainingStatsCalculator.HEATMAP_DAYS - 1L);
        List<PersonalTraining> entries = trainingRepository.findRange(athleteId, yearAgo, today);
        List<LocalDate> completedDates = new ArrayList<>();
        List<PersonalTraining> completed = new ArrayList<>();
        int missedInWindow = 0;
        int completedInWindow = 0;
        int tasksThisMonthDone = 0;
        int tasksThisMonthMissed = 0;
        int tasksDoneInWindow = 0;
        int tasksMissedInWindow = 0;
        LocalDate attendanceFrom = today.minusDays(TrainingStatsCalculator.ATTENDANCE_DAYS - 1L);
        YearMonth currentMonth = YearMonth.from(today);

        for (PersonalTraining t : entries) {
            boolean done = t.isCompleted();

            // Tasks run on their own counters and touch nothing below. A held calorie ceiling is not
            // a session: counted as one it would inflate the streak, the heatmap and the monthly
            // total, and every one of those numbers would stop meaning what its label says.
            if (t.isTask()) {
                // Only what has come due is counted, on both windows: a task still ahead of the
                // client is not something they have failed to hold, and counting it would report a
                // failure that has not had the chance to happen.
                boolean missed = !done && t.status(now) == TrainingStatus.MISSED;
                if (YearMonth.from(t.getDate()).equals(currentMonth)) {
                    if (done) {
                        tasksThisMonthDone++;
                    } else if (missed) {
                        tasksThisMonthMissed++;
                    }
                }
                if (!t.getDate().isBefore(attendanceFrom)) {
                    if (done) {
                        tasksDoneInWindow++;
                    } else if (missed) {
                        tasksMissedInWindow++;
                    }
                }
                continue;
            }

            if (done) {
                completedDates.add(t.getDate());
                completed.add(t);
            }
            // Attendance counts 1-on-1 trainings only: nobody ticks off a group session, so
            // including them would read as a wall of misses.
            if (!t.getDate().isBefore(attendanceFrom)) {
                if (done) {
                    completedInWindow++;
                } else if (t.status(now) == TrainingStatus.MISSED) {
                    missedInWindow++;
                }
            }
        }

        // Newest first — that is the order the overtraining rule reads.
        completed.sort(Comparator.comparing(PersonalTraining::getDate).reversed());
        List<Integer> rpeNewestFirst = completed.stream()
                .map(PersonalTraining::getRpe)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Integer> rpeRecent = rpeInWindow(completed, today, TrainingStatsCalculator.RPE_RECENT_DAYS);
        List<Integer> rpeDistributionWindow =
                rpeInWindow(completed, today, TrainingStatsCalculator.RPE_DISTRIBUTION_DAYS);

        YearMonth thisMonth = currentMonth;
        YearMonth prevMonth = thisMonth.minusMonths(1);
        int thisMonthCount = (int) completedDates.stream().filter(d -> YearMonth.from(d).equals(thisMonth)).count();
        int prevMonthCount = (int) completedDates.stream().filter(d -> YearMonth.from(d).equals(prevMonth)).count();

        Map<LocalDate, Integer> heatmap = TrainingStatsCalculator.heatmap(completedDates, today);

        // The two lifetime figures are asked of the database, not derived from the rolling year
        // above: counted from the window, "total" would stop growing after twelve months and
        // "first activity" would walk forward as the oldest sessions fell out of it.
        return new TrainingStatsResponse(
                thisMonthCount,
                prevMonthCount,
                (int) trainingRepository.countCompleted(athleteId),
                trainingRepository.findFirstCompletedDate(athleteId),
                TrainingStatsCalculator.currentStreakWeeks(completedDates, today),
                TrainingStatsCalculator.bestStreakWeeks(completedDates),
                TrainingStatsCalculator.averagePerMonth(completedDates, today),
                heatmap,
                TrainingStatsCalculator.attendancePercent(completedInWindow, missedInWindow),
                TrainingStatsCalculator.average(rpeNewestFirst),
                TrainingStatsCalculator.average(rpeRecent),
                TrainingStatsCalculator.rpeDistribution(rpeDistributionWindow),
                new TaskBreakdown(
                        tasksThisMonthDone, tasksThisMonthDone + tasksThisMonthMissed,
                        tasksDoneInWindow, tasksDoneInWindow + tasksMissedInWindow,
                        // Same rule as attendance: a percentage only exists once something came due.
                        TrainingStatsCalculator.attendancePercent(tasksDoneInWindow, tasksMissedInWindow)),
                includeOvertraining ? OvertrainingRule.isOvertrained(rpeNewestFirst) : null);
    }

    private static List<Integer> rpeInWindow(List<PersonalTraining> completedNewestFirst,
                                             LocalDate today, int days) {
        LocalDate from = today.minusDays(days - 1L);
        return completedNewestFirst.stream()
                .filter(t -> !t.getDate().isBefore(from))
                .map(PersonalTraining::getRpe)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
