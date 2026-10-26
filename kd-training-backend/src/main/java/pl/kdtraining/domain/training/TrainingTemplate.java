package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A reusable skeleton the coach drops into a client's plan.
 * <p>
 * Applying a template COPIES its content: editing the template later must not rewrite sessions
 * already handed out, because those describe what somebody actually did.
 */
@Entity
@Table(name = "training_templates")
public class TrainingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    @Nullable
    private String description;

    /** Null is allowed: an untimed training has no duration, so demanding one invents data. */
    @Column(name = "default_duration_minutes")
    @Nullable
    private Integer defaultDurationMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingTemplate() {}

    public TrainingTemplate(String title, @Nullable String description, @Nullable Integer defaultDurationMinutes) {
        edit(title, description, defaultDurationMinutes);
    }

    public void edit(String title, @Nullable String description, @Nullable Integer defaultDurationMinutes) {
        this.title = title;
        this.description = description;
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public Integer getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }
}
