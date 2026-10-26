package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * "Last time this viewer looked at this calendar, and how far into it they got."
 * <p>
 * The client is the row where {@code userId == athleteId}; a coach gets one row per client, so two
 * admins keep independent counters. A missing row means EPOCH — count everything, which is the right
 * answer for a calendar nobody has opened yet.
 * <p>
 * Two columns rather than one, because being caught up has two edges. {@code seenAt} answers "as of
 * when", {@code seenThrough} answers "up to which day" — and without the second, opening one week
 * cleared the notice for a month the viewer never reached. It only ever moves forward: paging back
 * to last week cannot un-read what was already read.
 */
@Entity
@Table(name = "training_calendar_reads")
@IdClass(TrainingCalendarRead.Key.class)
public class TrainingCalendarRead {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    /** Null means no day has been reached yet — see the class note on why this is not backfilled. */
    @Column(name = "seen_through")
    private LocalDate seenThrough;

    protected TrainingCalendarRead() {}

    public UUID getUserId() {
        return userId;
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public LocalDate getSeenThrough() {
        return seenThrough;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private UUID athleteId;

        public Key() {}

        public Key(UUID userId, UUID athleteId) {
            this.userId = userId;
            this.athleteId = athleteId;
        }

        /** Null is a legal argument to {@code equals} and answers false — marked so the signature says it. */
        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(athleteId, key.athleteId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, athleteId);
        }
    }
}
