package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal the coach set on one of three horizons.
 * <p>
 * Achieved goals are immutable and are never deleted: they are the trophy case, and rewriting one
 * would edit somebody's history.
 */
@Entity
@Table(name = "athlete_goals")
public class AthleteGoal {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalHorizon horizon;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "target_date")
    @Nullable
    private LocalDate targetDate;

    @Column(name = "achieved_at")
    @Nullable
    private LocalDate achievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AthleteGoal() {}

    public AthleteGoal(User athlete, GoalHorizon horizon, String content, @Nullable LocalDate targetDate) {
        this.athlete = athlete;
        this.horizon = horizon;
        this.content = content;
        this.targetDate = targetDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void edit(String content, @Nullable LocalDate targetDate) {
        this.content = content;
        this.targetDate = targetDate;
    }

    /** The horizon deliberately cannot change: a short-term goal that became long-term is a new goal. */
    public void achieve(LocalDate date) {
        this.achievedAt = date;
    }

    public boolean isAchieved() {
        return achievedAt != null;
    }

    /** Whose goal this is — the access check needs it, the same way a training exposes its athlete. */
    public User getAthlete() {
        return athlete;
    }

    public UUID getId() {
        return id;
    }

    public GoalHorizon getHorizon() {
        return horizon;
    }

    public String getContent() {
        return content;
    }

    @Nullable
    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Nullable
    public LocalDate getAchievedAt() {
        return achievedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
