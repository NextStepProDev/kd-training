package pl.mocnygrzbiet.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.mocnygrzbiet.domain.training.PersonalTraining;
import pl.mocnygrzbiet.domain.training.PersonalTrainingRepository;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Changed;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Slot;
import pl.mocnygrzbiet.infrastructure.mail.TrainingMailService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the queue {@link TrainingChangeRecorder} fills into one mail per client.
 * <p>
 * A client's changes wait until their calendar has been quiet for {@link #QUIET_PERIOD}: the coach
 * fixing an hour, then the description, then adding Thursday is one mail with the final plan, not
 * three with two stale ones. Five minutes was agreed with Mateusz (08.11.2026) — short enough for a
 * same-day cancellation to still be useful.
 * <p>
 * The queue lives in the database, unlike the coach-reply throttle: a restart there costs one extra
 * mail, here it would cost a lost cancellation.
 */
@Service
public class TrainingChangeMailer {

    private static final Logger log = LoggerFactory.getLogger(TrainingChangeMailer.class);
    public static final Duration QUIET_PERIOD = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final PersonalTrainingRepository trainings;
    private final UserRepository users;
    private final TrainingMailService mail;

    public TrainingChangeMailer(JdbcTemplate jdbc, PersonalTrainingRepository trainings,
                                UserRepository users, TrainingMailService mail) {
        this.jdbc = jdbc;
        this.trainings = trainings;
        this.users = users;
        this.mail = mail;
    }

    /** Clients whose newest queued change is no later than {@code cutoff}. */
    @Transactional(readOnly = true)
    public List<UUID> athletesDue(Instant cutoff) {
        return jdbc.queryForList("""
                SELECT athlete_id FROM training_change_notices
                GROUP BY athlete_id HAVING max(last_change_at) <= ?
                """, UUID.class, Timestamp.from(cutoff));
    }

    /**
     * Sends one client's queue as one mail and empties it.
     * <p>
     * The rows are locked and the quiet period checked again under the lock: the coach may have
     * touched the calendar since {@link #athletesDue} looked, and then this client waits for the next
     * round. Only the rows read here are deleted — one queued a moment later belongs to the next mail.
     * <p>
     * The mail leaves after the commit, so a rollback here never mails a queue that is still there.
     *
     * @return whether a mail was handed over
     */
    @Transactional
    public boolean sendDigest(UUID athleteId, Instant cutoff) {
        List<Notice> queued = jdbc.query("""
                SELECT training_id, change_kind, training_date, start_time, last_change_at
                FROM training_change_notices WHERE athlete_id = ? FOR UPDATE
                """, (rs, i) -> new Notice(
                        rs.getObject("training_id", UUID.class),
                        TrainingChangeRecorder.Change.valueOf(rs.getString("change_kind")),
                        new Slot(rs.getObject("training_date", LocalDate.class),
                                rs.getObject("start_time", LocalTime.class)),
                        rs.getTimestamp("last_change_at").toInstant()),
                athleteId);
        if (queued.isEmpty() || queued.stream().anyMatch(n -> n.lastChangeAt().isAfter(cutoff))) {
            return false;
        }
        List<UUID> ids = queued.stream().map(Notice::trainingId).toList();
        for (UUID id : ids) {
            jdbc.update("DELETE FROM training_change_notices WHERE training_id = ?", id);
        }

        User athlete = users.findById(athleteId).orElse(null);
        // A blocked account cannot open the plan, and a removed flag means there is no plan to open.
        if (athlete == null || athlete.isBlocked() || !athlete.isAthlete()) {
            return false;
        }
        Map<UUID, PersonalTraining> current = trainings.findAllById(ids).stream()
                .collect(Collectors.toMap(PersonalTraining::getId, Function.identity()));
        TrainingChangeDigest digest = digestOf(queued, current, LocalDateTime.now());
        if (digest.isEmpty()) {
            return false;
        }

        // Plain values, read now: the entity must not be touched after its transaction ends.
        String email = athlete.getEmail();
        String firstName = athlete.getFirstName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mail.sendTrainingChanges(email, firstName, digest);
                } catch (RuntimeException e) {
                    log.warn("Could not queue the training-change mail for user {}", athleteId, e);
                }
            }
        });
        return true;
    }

    /**
     * Each queued row holds the first change since the last mail; the training itself, read now, holds
     * the outcome. Together:
     * <ul>
     *   <li>added, still there and ahead → added, in its current slot; gone or moved into the past →
     *       nothing (the client never heard of it);</li>
     *   <li>changed, still there → changed, with the old slot when it moved; gone → cancelled, in the
     *       slot the client knew;</li>
     *   <li>cancelled → cancelled.</li>
     * </ul>
     */
    static TrainingChangeDigest digestOf(List<Notice> queued, Map<UUID, PersonalTraining> current,
                                         LocalDateTime now) {
        List<Slot> added = new ArrayList<>();
        List<Changed> changed = new ArrayList<>();
        List<Slot> cancelled = new ArrayList<>();
        for (Notice notice : queued) {
            @Nullable PersonalTraining training = current.get(notice.trainingId());
            switch (notice.change()) {
                case ADDED -> {
                    if (training != null && TrainingChangeRecorder.slotOf(training).isUpcoming(now)) {
                        added.add(TrainingChangeRecorder.slotOf(training));
                    }
                }
                case CHANGED -> {
                    if (training == null) {
                        cancelled.add(notice.slot());
                    } else {
                        Slot slot = TrainingChangeRecorder.slotOf(training);
                        changed.add(new Changed(slot, slot.equals(notice.slot()) ? null : notice.slot()));
                    }
                }
                case CANCELLED -> cancelled.add(notice.slot());
            }
        }
        return new TrainingChangeDigest(added, changed, cancelled);
    }

    record Notice(UUID trainingId, TrainingChangeRecorder.Change change, Slot slot, Instant lastChangeAt) {}
}
