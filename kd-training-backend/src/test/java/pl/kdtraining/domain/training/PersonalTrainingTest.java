package pl.kdtraining.domain.training;

import org.junit.jupiter.api.Test;
import pl.kdtraining.domain.user.User;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pure domain rules, no Spring and no clock reads inside the assertions — every method that cares
 * about "now" takes it as a parameter, so these stay green next year.
 */
class PersonalTrainingTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    private static PersonalTraining untimed() {
        return new PersonalTraining(mock(User.class), TrainingKind.TRAINING, DAY, "Siła", true);
    }

    private static PersonalTraining timed(LocalTime start, LocalTime end) {
        PersonalTraining t = new PersonalTraining(mock(User.class), TrainingKind.TRAINING, DAY, "Siła", true);
        t.edit(DAY, start, end, "Siła", null, null, true);
        return t;
    }

    @Test
    void shouldTreatUntimedTrainingAsStartedFromMidnight() {
        // Given: no hour set — the default case, "do this that day"
        PersonalTraining training = untimed();

        assertFalse(training.hasStarted(DAY.minusDays(1).atTime(23, 59)));
        assertTrue(training.hasStarted(DAY.atStartOfDay()));
        assertTrue(training.hasStarted(DAY.atTime(6, 0)));
    }

    @Test
    void shouldTreatTimedTrainingAsStartedFromItsHour() {
        PersonalTraining training = timed(LocalTime.of(17, 0), LocalTime.of(18, 30));

        assertFalse(training.hasStarted(DAY.atTime(16, 59)));
        assertTrue(training.hasStarted(DAY.atTime(17, 0)));
    }

    @Test
    void shouldNotMarkTodaysUntimedTrainingAsMissed() {
        // Given: an untimed training runs until the end of its day — flagging it missed at 08:00
        // would scold the client for a session they still have all day to do
        PersonalTraining training = untimed();

        assertEquals(TrainingStatus.PLANNED, training.status(DAY.atTime(8, 0)));
        assertEquals(TrainingStatus.PLANNED, training.status(DAY.atTime(23, 59)));
        assertEquals(TrainingStatus.MISSED, training.status(DAY.plusDays(1).atTime(0, 1)));
    }

    @Test
    void shouldMarkTimedTrainingMissedOnlyAfterItsEnd() {
        PersonalTraining training = timed(LocalTime.of(17, 0), LocalTime.of(18, 30));

        assertEquals(TrainingStatus.PLANNED, training.status(DAY.atTime(18, 0)));
        assertEquals(TrainingStatus.MISSED, training.status(DAY.atTime(18, 31)));
    }

    @Test
    void shouldReportCompletedRegardlessOfDate() {
        PersonalTraining training = untimed();
        training.complete(7, "ok");

        // Even long in the past it stays COMPLETED — MISSED is only for what was never ticked off
        assertEquals(TrainingStatus.COMPLETED, training.status(DAY.plusYears(1).atStartOfDay()));
    }

    @Test
    void shouldRejectRpeOutsideOneToTen() {
        PersonalTraining training = untimed();

        assertThrows(IllegalArgumentException.class, () -> training.complete(0, null));
        assertThrows(IllegalArgumentException.class, () -> training.complete(11, null));
        assertFalse(training.isCompleted());
    }

    @Test
    void shouldClearCoachAuthorshipWhenCompleting() {
        // Given: a training the coach created and last touched
        PersonalTraining training = untimed();
        assertTrue(training.isLastModifiedByAdmin());

        // When: the client ticks it off
        training.complete(6, "dobrze");

        // Then: authorship flips to the client. @PreUpdate bumps updatedAt on every write, so this
        // flag is the ONLY thing telling the two apart — leaving it true would light the client's
        // own "new from coach" dot on their own action.
        assertFalse(training.isLastModifiedByAdmin());
    }

    @Test
    void shouldClearCoachAuthorshipWhenUncompleting() {
        PersonalTraining training = untimed();
        training.complete(6, "dobrze");
        training.edit(DAY, null, null, "Siła", null, null, true);
        assertTrue(training.isLastModifiedByAdmin());

        training.uncomplete();

        assertFalse(training.isLastModifiedByAdmin());
    }

    @Test
    void shouldClearRpeAndFeedbackWhenUncompleting() {
        // Given: the DB CHECK refuses an RPE without a completion, so undo has to wipe both
        PersonalTraining training = untimed();
        training.complete(9, "ciężko");

        training.uncomplete();

        assertNull(training.getCompletedAt());
        assertNull(training.getRpe());
        assertNull(training.getFeedback());
        assertFalse(training.isCompleted());
    }

    @Test
    void shouldTickOffATaskWithoutAnEffortRating() {
        // Given: a task — "stay under 2200 kcal today"
        PersonalTraining task = new PersonalTraining(
                mock(User.class), TrainingKind.TASK, DAY, "Max 2200 kcal", true);

        task.complete(null, "wyszło");

        assertTrue(task.isCompleted());
        assertNull(task.getRpe());
        // Same as a training: the client's act, so authorship flips and their own dot stays dark
        assertFalse(task.isLastModifiedByAdmin());
    }

    @Test
    void shouldRefuseAnEffortRatingOnATask() {
        // Given: "how hard was staying under 2200 kcal, 1-10" is a question about nothing, and an
        // answer would land in the same averages the coach reads training load from
        PersonalTraining task = new PersonalTraining(
                mock(User.class), TrainingKind.TASK, DAY, "Max 2200 kcal", true);

        assertThrows(IllegalArgumentException.class, () -> task.complete(5, null));
        assertFalse(task.isCompleted());
    }

    @Test
    void shouldRefuseToTickOffATrainingWithoutAnEffortRating() {
        PersonalTraining training = untimed();

        assertThrows(IllegalArgumentException.class, () -> training.complete(null, "zrobione"));
        assertFalse(training.isCompleted());
    }

    @Test
    void shouldKeepCaloriesOnATaskAndDropThemOnATraining() {
        PersonalTraining task = new PersonalTraining(
                mock(User.class), TrainingKind.TASK, DAY, "Max 2200 kcal", true);
        task.edit(DAY, null, null, "Max 2200 kcal", null, 2200, true);
        assertEquals(2200, task.getTargetCalories());

        // A training has no such field in the form, so a value arriving with one is noise
        PersonalTraining training = untimed();
        training.edit(DAY, null, null, "Siła", null, 2200, true);
        assertNull(training.getTargetCalories());
    }

    @Test
    void shouldDropEndTimeWhenTrainingBecomesUntimed() {
        // Given: a timed training
        PersonalTraining training = timed(LocalTime.of(17, 0), LocalTime.of(18, 30));

        // When: the hour is removed
        training.edit(DAY, null, LocalTime.of(18, 30), "Siła", null, null, true);

        // Then: the end goes with it — an end without a start has no meaning without an hour grid,
        // and the DB CHECK would reject the row anyway
        assertNull(training.getStartTime());
        assertNull(training.getEndTime());
    }
}
