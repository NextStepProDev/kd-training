package pl.kdtraining.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MaterialQuestionRepository extends JpaRepository<MaterialQuestion, UUID> {

    /** Every thread on one client's materials page in one query, oldest message first. */
    @Query("""
        SELECT q FROM MaterialQuestion q
        WHERE q.material.id IN :materialIds
        ORDER BY q.createdAt
        """)
    List<MaterialQuestion> findForMaterials(@Param("materialIds") Collection<UUID> materialIds);

    /**
     * Messages from the other side since the viewer last looked. Timestamp only, like goals: a
     * material carries no date, so the calendar's "how far did they get" half does not apply.
     */
    @Query("""
        SELECT COUNT(q) FROM MaterialQuestion q
        WHERE q.material.athlete.id = :athleteId
          AND q.authorIsAdmin = :fromAdmin
          AND q.createdAt > :since
        """)
    long countSince(@Param("athleteId") UUID athleteId,
                    @Param("fromAdmin") boolean fromAdmin,
                    @Param("since") Instant since);
}
