package pl.kdtraining.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AthleteGoalRepository extends JpaRepository<AthleteGoal, UUID> {

    /**
     * Active goals. Deliberately UNORDERED here: the column stores the enum as text, so SQL would
     * sort it alphabetically (LONG, MEDIUM, SHORT) rather than by horizon. The service sorts by the
     * enum's own order, which is what the three cards render in.
     */
    @Query("""
        SELECT g FROM AthleteGoal g
        WHERE g.athlete.id = :athleteId AND g.achievedAt IS NULL
        """)
    List<AthleteGoal> findActive(@Param("athleteId") UUID athleteId);

    /** The trophy case: everything ever achieved, newest first. */
    @Query("""
        SELECT g FROM AthleteGoal g
        WHERE g.athlete.id = :athleteId AND g.achievedAt IS NOT NULL
        ORDER BY g.achievedAt DESC
        """)
    List<AthleteGoal> findAchieved(@Param("athleteId") UUID athleteId);

    /** A goal set by the coach is news for the client — one of the unread sources. */
    @Query("""
        SELECT COUNT(g) FROM AthleteGoal g
        WHERE g.athlete.id = :athleteId AND g.createdAt > :since
        """)
    long countCreatedSince(@Param("athleteId") UUID athleteId, @Param("since") Instant since);

    /** Erasing one athlete's 1-on-1 plan. Rows only — files are unlinked before this runs. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AthleteGoal g WHERE g.athlete.id = :athleteId")
    int deleteAllForAthlete(@Param("athleteId") UUID athleteId);
}
