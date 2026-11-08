package pl.mocnygrzbiet.api.trainingcalendar;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.mocnygrzbiet.domain.training.PersonalTraining;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Slot;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Queues "the coach changed your plan" for the client (the client's answer: a mail when the coach
 * adds, changes or cancels a training). {@link TrainingChangeMailer} sends the queue once the
 * client's calendar has been quiet for a while — see V7 for the table and its one-row-per-training
 * rule.
 * <p>
 * Written inside the caller's transaction, so a change that fails to save never announces itself.
 * <p>
 * What is left out, on purpose: the client's own writes (they know), tasks (the client asked about
 * trainings, and a calorie target is not an appointment), and anything already behind the client —
 * reshuffling last week is housekeeping, not news.
 */
@Component
public class TrainingChangeRecorder {

    enum Change { ADDED, CHANGED, CANCELLED }

    private final JdbcTemplate jdbc;

    public TrainingChangeRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** After the row is saved: the queue is keyed by its id. */
    public void added(PersonalTraining training, boolean byAdmin) {
        Slot slot = slotOf(training);
        if (relevant(training, byAdmin) && slot.isUpcoming(LocalDateTime.now())) {
            record(training, Change.ADDED, slot);
        }
    }

    /**
     * @param before the slot as it was before this edit. Kept only if this is the first change since the
     *               last mail — what the client last heard is what the mail compares against.
     */
    public void changed(PersonalTraining training, Slot before, boolean byAdmin) {
        LocalDateTime now = LocalDateTime.now();
        // Either end ahead: moving tomorrow's session to yesterday is still news to the client.
        if (relevant(training, byAdmin) && (before.isUpcoming(now) || slotOf(training).isUpcoming(now))) {
            record(training, Change.CHANGED, before);
        }
    }

    /** Before the row is deleted: afterwards nothing says when it was. */
    public void cancelled(PersonalTraining training, boolean byAdmin) {
        Slot slot = slotOf(training);
        if (relevant(training, byAdmin) && slot.isUpcoming(LocalDateTime.now())) {
            record(training, Change.CANCELLED, slot);
        }
    }

    /**
     * Drops whatever is queued for this client. For the plan erasure: the trainings are gone without
     * passing through {@link #cancelled}, and a plan switched back on within the quiet period must
     * not open with a mail about the erased one.
     */
    public void forgetAthlete(UUID athleteId) {
        jdbc.update("DELETE FROM training_change_notices WHERE athlete_id = ?", athleteId);
    }

    static Slot slotOf(PersonalTraining training) {
        return new Slot(training.getDate(), training.getStartTime());
    }

    private static boolean relevant(PersonalTraining training, boolean byAdmin) {
        return byAdmin && !training.isTask();
    }

    /**
     * The first change since the last mail stays; a later one only restarts the quiet period. An
     * entry added and then cancelled inside one quiet period therefore stays "added" here, and the
     * mailer, finding no row behind it, says nothing at all — which is the truth.
     */
    private void record(PersonalTraining training, Change change, Slot slot) {
        jdbc.update("""
                INSERT INTO training_change_notices
                    (training_id, athlete_id, change_kind, training_date, start_time, last_change_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (training_id) DO UPDATE SET last_change_at = EXCLUDED.last_change_at
                """,
                training.getId(), training.getAthlete().getId(), change.name(),
                slot.date(), slot.start(), Timestamp.from(Instant.now()));
    }
}
