package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Snapshot of a deleted FUTURE training.
 * <p>
 * The row it describes is gone, so the alert cannot point at it — it has to carry its own copy.
 * Deleting past entries is housekeeping and is deliberately not recorded.
 */
@Entity
@Table(name = "training_deletions")
public class TrainingDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "training_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    @Nullable
    private LocalTime startTime;

    @Column(nullable = false, length = 150)
    private String title;

    /** Both sides may delete, and the alert travels the other way. */
    @Column(name = "deleted_by_admin", nullable = false)
    private boolean deletedByAdmin;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;

    @Column(name = "dismissed_at")
    @Nullable
    private Instant dismissedAt;

    protected TrainingDeletion() {}

    public TrainingDeletion(PersonalTraining source, boolean deletedByAdmin) {
        this.athlete = source.getAthlete();
        this.date = source.getDate();
        this.startTime = source.getStartTime();
        this.title = source.getTitle();
        this.deletedByAdmin = deletedByAdmin;
    }

    @PrePersist
    void onCreate() {
        deletedAt = Instant.now();
    }

    public void dismiss() {
        this.dismissedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    @Nullable
    public LocalTime getStartTime() {
        return startTime;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDeletedByAdmin() {
        return deletedByAdmin;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Nullable
    public Instant getDismissedAt() {
        return dismissedAt;
    }
}
