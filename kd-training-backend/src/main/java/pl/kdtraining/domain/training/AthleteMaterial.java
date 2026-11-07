package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/** A clip, a tip, or both, that the coach gives one client to keep (no date attached). */
@Entity
@Table(name = "athlete_materials")
public class AthleteMaterial {

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_NOTE_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_NOTE_LENGTH)
    @Nullable
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    @Nullable
    private ExerciseVideo video;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AthleteMaterial() {}

    public AthleteMaterial(User athlete, String title, @Nullable String note, @Nullable ExerciseVideo video) {
        this.athlete = athlete;
        this.title = title;
        this.note = note;
        this.video = video;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void edit(String title, @Nullable String note, @Nullable ExerciseVideo video) {
        this.title = title;
        this.note = note;
        this.video = video;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getNote() {
        return note;
    }

    public @Nullable ExerciseVideo getVideo() {
        return video;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
