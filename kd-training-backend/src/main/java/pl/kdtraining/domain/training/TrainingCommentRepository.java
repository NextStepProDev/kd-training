package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TrainingCommentRepository extends JpaRepository<TrainingComment, UUID> {

    @Query("""
        SELECT c FROM TrainingComment c
        LEFT JOIN FETCH c.author
        WHERE c.training.id = :trainingId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findThread(@Param("trainingId") UUID trainingId);

    /**
     * Comments the given viewer has not seen: written by the other side after their last visit, or
     * sitting on a training beyond the last day they reached. {@code authorIsAdmin} is the frozen
     * role, so this stays correct even if someone changes role.
     * <p>
     * The date clause reads the TRAINING's day, not the comment's timestamp: a comment is findable
     * exactly where its training is, so that is what decides whether the viewer could have seen it.
     */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId
          AND c.authorIsAdmin = :fromAdmin
          AND (c.createdAt > :since OR c.training.date > :seenThrough)
        """)
    long countSince(@Param("athleteId") UUID athleteId,
                    @Param("fromAdmin") boolean fromAdmin,
                    @Param("since") Instant since,
                    @Param("seenThrough") LocalDate seenThrough);

    long countByTrainingId(UUID trainingId);

    /** Comment counts for a whole calendar page in one query. */
    @Query("""
        SELECT c.training.id, COUNT(c) FROM TrainingComment c
        WHERE c.training.id IN :trainingIds
        GROUP BY c.training.id
        """)
    List<Object[]> countByTrainingIds(@Param("trainingIds") List<UUID> trainingIds);

    /**
     * Per-training flag for the unread dot, batched for one calendar page.
     * <p>
     * The {@code seenThrough} clause has to match {@link #countSince} exactly: the number promises
     * something findable, and these are the dots it is found by. Every id here is already on the page
     * being rendered, so the clause only ever adds trainings the viewer is looking at for the first
     * time.
     */
    @Query("""
        SELECT DISTINCT c.training.id FROM TrainingComment c
        WHERE c.training.id IN :trainingIds
          AND c.authorIsAdmin = :fromAdmin
          AND (c.createdAt > :since OR c.training.date > :seenThrough)
        """)
    List<UUID> findTrainingIdsWithNewComments(@Param("trainingIds") List<UUID> trainingIds,
                                              @Param("fromAdmin") boolean fromAdmin,
                                              @Param("since") Instant since,
                                              @Param("seenThrough") LocalDate seenThrough);

    /** The per-training photo cap counts across the whole thread, not per comment. */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.id = :trainingId
          AND c.photoFilename IS NOT NULL
        """)
    long countPhotosForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Photos sitting in one client's calendar since {@code since}, whoever put them there.
     * <p>
     * Counted per ATHLETE and not per uploader, because the coach uploads into many calendars in one
     * sitting: a cap on "photos this account sent today" would let a client through untouched and
     * stop the coach at the seventh person they planned for. The thing that grows is one client's
     * folder, so that is the thing with a ceiling — and the total then has a bound anybody can work
     * out, since the roster size is the coach's own decision.
     * <p>
     * Rows still present, not rows ever created: deleting a photo takes the file off disk too, so a
     * delete-and-reupload cycle leaves the disk exactly where it was. There is nothing to defend
     * against there, and no second table to keep.
     */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId
          AND c.photoFilename IS NOT NULL
          AND c.createdAt >= :since
        """)
    long countPhotosForAthleteSince(@Param("athleteId") UUID athleteId, @Param("since") Instant since);

    /**
     * Filenames to unlink before a training is deleted. The rows go through the DB cascade without
     * Hibernate ever loading them, so no entity callback can do this — it has to be explicit.
     */
    @Query("""
        SELECT c.photoFilename FROM TrainingComment c
        WHERE c.training.id = :trainingId
          AND c.photoFilename IS NOT NULL
        """)
    List<String> findPhotoFilenamesForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Every photo sitting on one athlete's trainings, whoever uploaded it.
     * <p>
     * Deliberately not {@code findPhotosForUser}: that one follows AUTHORSHIP, so it would miss the
     * coach's photos on this athlete's calendar — which are exactly the ones an erasure of that
     * athlete's plan has to remove.
     */
    @Query("""
        SELECT c.photoFilename FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId
          AND c.photoFilename IS NOT NULL
        """)
    List<String> findPhotoFilenamesForAthlete(@Param("athleteId") UUID athleteId);

    /**
     * Filenames to unlink before an account is deleted. Covers both sides: photos on this person's
     * own trainings, and photos they wrote in someone else's thread — {@code author_id} is only set
     * to NULL by the cascade, so those files would otherwise outlive their author.
     */
    @Query("""
        SELECT c FROM TrainingComment c
        WHERE c.photoFilename IS NOT NULL
          AND (c.training.athlete.id = :userId OR c.author.id = :userId)
        """)
    List<TrainingComment> findPhotosForUser(@Param("userId") UUID userId);

    @Query("""
        SELECT c FROM TrainingComment c
        WHERE c.photoFilename IS NOT NULL
          AND c.photoExpiresAt < :now
        """)
    List<TrainingComment> findExpiredPhotos(@Param("now") Instant now);

    /** Every filename the database still knows about — the other half of the orphan sweep. */
    @Query("SELECT c.photoFilename FROM TrainingComment c WHERE c.photoFilename IS NOT NULL")
    Set<String> findAllPhotoFilenames();
}
