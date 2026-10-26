package pl.kdtraining.domain.adminnote;

import jakarta.persistence.*;
import pl.kdtraining.domain.training.PersonalTraining;
import pl.kdtraining.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * One private note, written by the owner about one training, readable by nobody else.
 *
 * <h2>Why this type is fenced off</h2>
 * The risk this feature carries is not a missing role check on the controller -- it is an obliging
 * field. The shapes describing a session are shared: {@code CalendarRangeResponse} is one record
 * served to the coach and to the client, and the public calendar DTOs are cached. A note field
 * added to any of them would compile, would look like a convenience, and would publish the
 * notebook to the very people it is written about. So the type is unreachable outside
 * {@code domain.adminnote} and {@code api.admin.note}, enforced by
 * {@code architecture/AdminNoteIsolationArchTest}: a service that cannot read a note cannot leak
 * one, and privacy stops depending on anybody remembering.
 *
 * <h2>No optimistic locking, deliberately</h2>
 * The rest of the module version-checks a shared row ({@link PersonalTraining}) because two people
 * genuinely edit it at once. A note has ONE author, so the only collision possible is that author's
 * own second tab, and the rule this table already runs on — a second write is a correction, not a
 * second note — gives that the same answer. Adding {@code @Version} would buy a conflict dialog for
 * a conflict with yourself.
 */
@Entity
@Table(name = "admin_private_notes")
public class AdminPrivateNote {

    public static final int MAX_BODY_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminPrivateNote() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public User getAuthor() { return author; }

    public PersonalTraining getTraining() { return training; }

    public String getBody() { return body; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
