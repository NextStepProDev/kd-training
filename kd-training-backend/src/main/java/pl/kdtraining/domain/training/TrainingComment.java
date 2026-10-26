package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/** One message in the conversation attached to a training. */
@Entity
@Table(name = "training_comments")
public class TrainingComment {

    public static final int MAX_BODY_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @Nullable
    private User author;

    /**
     * The author's role frozen at the time of writing. Reading it off the user today would relabel a
     * client's old comments the day they are promoted, and flip everyone's unread dots with them.
     */
    @Column(name = "author_is_admin", nullable = false)
    private boolean authorIsAdmin;

    /** Null only when the comment is a bare photo — the DB CHECK forbids a row with neither. */
    @Column(length = MAX_BODY_LENGTH)
    @Nullable
    private String body;

    /**
     * Stored filename of the attached photo, or null. Health data (GDPR art. 9), so it never goes
     * into the public {@code /api/files} namespace — see TrainingPhotoService for how it is served.
     */
    @Column(name = "photo_filename", length = 64)
    @Nullable
    private String photoFilename;

    /** Rendered dimensions, so the client can reserve the box and not have the thread jump. */
    @Column(name = "photo_width")
    @Nullable
    private Short photoWidth;

    @Column(name = "photo_height")
    @Nullable
    private Short photoHeight;

    @Column(name = "photo_expires_at")
    @Nullable
    private Instant photoExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingComment() {}

    public TrainingComment(PersonalTraining training, User author, boolean authorIsAdmin, @Nullable String body) {
        this.training = training;
        this.author = author;
        this.authorIsAdmin = authorIsAdmin;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public PersonalTraining getTraining() {
        return training;
    }

    @Nullable
    public User getAuthor() {
        return author;
    }

    public boolean isAuthorIsAdmin() {
        return authorIsAdmin;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getPhotoFilename() {
        return photoFilename;
    }

    @Nullable
    public Short getPhotoWidth() {
        return photoWidth;
    }

    @Nullable
    public Short getPhotoHeight() {
        return photoHeight;
    }

    @Nullable
    public Instant getPhotoExpiresAt() {
        return photoExpiresAt;
    }

    public boolean hasPhoto() {
        return photoFilename != null;
    }

    public void attachPhoto(String filename, int width, int height, Instant expiresAt) {
        this.photoFilename = filename;
        this.photoWidth = (short) width;
        this.photoHeight = (short) height;
        this.photoExpiresAt = expiresAt;
    }

    /**
     * Detaches the photo, leaving the text behind. Callers must delete the file themselves and must
     * drop the whole row when the body is null — the CHECK constraint refuses an empty comment.
     */
    public void clearPhoto() {
        this.photoFilename = null;
        this.photoWidth = null;
        this.photoHeight = null;
        this.photoExpiresAt = null;
    }
}
