package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** One demonstration clip in the coach's library, referenced by any number of trainings. */
@Entity
@Table(name = "exercise_videos")
public class ExerciseVideo {

    public static final int MAX_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "video_key", nullable = false, length = 64)
    private String videoKey;

    @Column(length = 1000)
    @Nullable
    private String description;

    @Column(name = "search_text", nullable = false)
    private String searchText;

    @Column(name = "archived_at")
    @Nullable
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExerciseVideo() {}

    public ExerciseVideo(String name, String url, String videoKey, @Nullable String description) {
        edit(name, url, videoKey, description);
    }

    public void edit(String name, String url, String videoKey, @Nullable String description) {
        this.name = name;
        this.url = url;
        this.videoKey = videoKey;
        this.description = description;
        this.searchText = buildSearchText(name);
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

    /**
     * Lowercased and stripped of Polish diacritics, so "cwiczenie" finds "Ćwiczenie…".
     * Nobody types accents into a search box while planning a session.
     */
    static String buildSearchText(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // ł has no combining form, so NFD leaves it alone.
        return decomposed.replace('ł', 'l').replace('Ł', 'L').toLowerCase(Locale.ROOT).trim();
    }

    public void archive() {
        this.archivedAt = Instant.now();
    }

    public void restore() {
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getVideoKey() {
        return videoKey;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public Instant getArchivedAt() {
        return archivedAt;
    }
}
