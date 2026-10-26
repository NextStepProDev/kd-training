package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PersonalTrainingRepository extends JpaRepository<PersonalTraining, UUID> {

    /**
     * One calendar page. Ordering matches how the day column renders: untimed first (the default
     * case, "do this today"), then chronologically, then by insertion for ties. The same rule lives
     * in {@code sortTiles()} on the frontend — change one, change both.
     */
    @Query("""
        SELECT pt FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId
          AND pt.date BETWEEN :from AND :to
        ORDER BY pt.date ASC, pt.startTime ASC NULLS FIRST, pt.createdAt ASC
        """)
    List<PersonalTraining> findRange(@Param("athleteId") UUID athleteId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Trainings the other side touched that this viewer has not caught up with.
     * <p>
     * Keyed on {@code updatedAt}, never {@code completedAt}: undoing a completion clears that column,
     * and the coach still needs to hear about it. {@code lastModifiedByAdmin} is what separates
     * "they changed it" from "I did" — {@code @PreUpdate} bumps {@code updatedAt} either way.
     * <p>
     * The second half of the condition is what keeps this number honest against the dots. Opening one
     * page used to stamp "seen" over the whole plan, so a month the viewer never reached was cleared
     * from the badge without a dot ever appearing on it. A row past {@code seenThrough} has not been
     * looked at, whenever it was written.
     */
    @Query("""
        SELECT COUNT(pt) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId
          AND pt.lastModifiedByAdmin = :byAdmin
          AND (pt.updatedAt > :since OR pt.date > :seenThrough)
        """)
    long countTouchedSince(@Param("athleteId") UUID athleteId,
                           @Param("byAdmin") boolean byAdmin,
                           @Param("since") Instant since,
                           @Param("seenThrough") LocalDate seenThrough);

    /**
     * Next training from today onwards — powers the "what's next" line on the account tile. Tasks are
     * excluded on purpose: a calorie ceiling is not what "next training" means.
     */
    @Query("""
        SELECT MIN(pt.date) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId AND pt.date >= :from AND pt.completedAt IS NULL
          AND pt.kind = pl.kdtraining.domain.training.TrainingKind.TRAINING
        """)
    @Nullable
    LocalDate findNextTrainingDate(@Param("athleteId") UUID athleteId, @Param("from") LocalDate from);

    /**
     * Everything ever ticked off, counted in the database rather than from a loaded page.
     * <p>
     * The statistics panel reads a rolling year of rows for the heatmap, streaks and averages, and
     * the lifetime figures used to be derived from that same list. They cannot be: after a year of
     * coaching the "total" stops growing — old sessions drop out of the window exactly as fast as
     * new ones arrive — and the "first activity" date creeps forward month by month, so a client's
     * own history quietly rewrites itself. Tasks are excluded here for the same reason they are
     * excluded everywhere else in the training numbers: holding a calorie ceiling is not a session.
     */
    @Query("""
        SELECT COUNT(pt) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId AND pt.completedAt IS NOT NULL
          AND pt.kind = pl.kdtraining.domain.training.TrainingKind.TRAINING
        """)
    long countCompleted(@Param("athleteId") UUID athleteId);

    /** Date of the earliest completed training ever, or null when there is none yet. */
    @Query("""
        SELECT MIN(pt.date) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId AND pt.completedAt IS NOT NULL
          AND pt.kind = pl.kdtraining.domain.training.TrainingKind.TRAINING
        """)
    @Nullable
    LocalDate findFirstCompletedDate(@Param("athleteId") UUID athleteId);

    /** Erasing one athlete's 1-on-1 plan. Rows only — files are unlinked before this runs. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PersonalTraining t WHERE t.athlete.id = :athleteId")
    int deleteAllForAthlete(@Param("athleteId") UUID athleteId);
}
