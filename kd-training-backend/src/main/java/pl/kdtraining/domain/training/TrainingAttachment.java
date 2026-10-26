package pl.kdtraining.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Material hanging off a training or a template — never both.
 * <p>
 * A VIDEO is a REFERENCE into the library, not a copy of its link: correcting a name or URL there
 * fixes it everywhere at once. A LINK is a one-off address that belongs to this training alone.
 */
@Entity
@Table(name = "training_attachments")
public class TrainingAttachment {

    /** Three is what fits on a card without turning it into a list of homework. */
    public static final int MAX_PER_OWNER = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    @Nullable
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @Nullable
    private TrainingTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentKind kind;

    @Column(length = 150)
    @Nullable
    private String label;

    @Column(length = 500)
    @Nullable
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    @Nullable
    private ExerciseVideo video;

    @Column(nullable = false)
    private short position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingAttachment() {}

    public static TrainingAttachment link(String url, @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment();
        a.kind = AttachmentKind.LINK;
        a.url = url;
        a.label = label;
        a.position = (short) position;
        return a;
    }

    public static TrainingAttachment video(ExerciseVideo video, @Nullable String label, int position) {
        TrainingAttachment a = new TrainingAttachment();
        a.kind = AttachmentKind.VIDEO;
        a.video = video;
        a.label = label;
        a.position = (short) position;
        return a;
    }

    public void attachTo(PersonalTraining training) {
        this.training = training;
        this.template = null;
    }

    public void attachTo(TrainingTemplate template) {
        this.template = template;
        this.training = null;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public PersonalTraining getTrainingOwner() {
        return training;
    }

    public AttachmentKind getKind() {
        return kind;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public ExerciseVideo getVideo() {
        return video;
    }

    public short getPosition() {
        return position;
    }
}
