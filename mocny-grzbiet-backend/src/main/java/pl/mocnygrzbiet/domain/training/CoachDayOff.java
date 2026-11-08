package pl.mocnygrzbiet.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A stretch of days the coach does not train (holiday, illness, a course). There is one coach, so the
 * row belongs to nobody: every client sees it in their own calendar. Both ends are inclusive.
 * <p>
 * It only marks the days; trainings already planned on them stay where they are, and the coach decides
 * what to do with each one. Deleting them in bulk would also wipe the comments under them.
 */
@Entity
@Table(name = "coach_days_off")
public class CoachDayOff {

    public static final int MAX_NOTE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = MAX_NOTE_LENGTH)
    @Nullable
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoachDayOff() {}

    public CoachDayOff(LocalDate startDate, LocalDate endDate, @Nullable String note) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public @Nullable String getNote() {
        return note;
    }
}
