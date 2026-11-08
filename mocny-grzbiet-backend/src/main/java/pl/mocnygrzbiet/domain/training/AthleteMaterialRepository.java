package pl.mocnygrzbiet.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AthleteMaterialRepository extends JpaRepository<AthleteMaterial, UUID> {

    /** Newest first: what the coach gave last is what the client is most likely looking for. */
    @Query("""
        SELECT m FROM AthleteMaterial m LEFT JOIN FETCH m.video
        WHERE m.athlete.id = :athleteId
        ORDER BY m.createdAt DESC
        """)
    List<AthleteMaterial> findForAthlete(@Param("athleteId") UUID athleteId);

    boolean existsByVideoId(UUID videoId);

    /** News for the client only: materials come from the coach, never the other way. */
    @Query("SELECT COUNT(m) FROM AthleteMaterial m WHERE m.athlete.id = :athleteId AND m.createdAt > :since")
    long countCreatedSince(@Param("athleteId") UUID athleteId, @Param("since") Instant since);

    /** Erasing one athlete's 1-on-1 plan; the questions go with it (ON DELETE CASCADE). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM athlete_materials WHERE athlete_id = :athleteId", nativeQuery = true)
    int deleteAllForAthlete(@Param("athleteId") UUID athleteId);
}
