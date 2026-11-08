package pl.mocnygrzbiet.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.mocnygrzbiet.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/** One message in the thread under a material: the client's question or the coach's reply. */
@Entity
@Table(name = "material_questions")
public class MaterialQuestion {

    public static final int MAX_BODY_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private AthleteMaterial material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @Nullable
    private User author;

    @Column(name = "author_is_admin", nullable = false)
    private boolean authorIsAdmin;

    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MaterialQuestion() {}

    public MaterialQuestion(AthleteMaterial material, User author, boolean authorIsAdmin, String body) {
        this.material = material;
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

    public AthleteMaterial getMaterial() {
        return material;
    }

    public boolean isAuthorIsAdmin() {
        return authorIsAdmin;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
