package pl.kdtraining.domain.adminnote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every read and every delete is narrowed to (author, target).
 * <p>
 * A note is never addressed by its own id, so there is no branch in which somebody could forget to
 * compare the author. That is the whole access-control story for this table, and it is structural
 * rather than remembered.
 */
public interface AdminPrivateNoteRepository extends JpaRepository<AdminPrivateNote, UUID> {

    // --- reads -----------------------------------------------------------------------------------

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    Optional<AdminPrivateNote> findForTraining(@Param("authorId") UUID authorId,
                                               @Param("trainingId") UUID trainingId);

    // --- writes ----------------------------------------------------------------------------------
    //
    // Not read-then-save: a second tab or a double tap would both find nothing, both insert, and the
    // unique constraint would refuse the second -- a 500 for somebody who pressed the button twice.
    // Letting the database settle the tie makes the second write a correction, which is what a
    // second note has always meant here.
    //
    // `updatedAt` arrives as an argument rather than SQL now(): now() is the transaction start time,
    // which can predate an edit made moments earlier. `clearAutomatically` matters just as much --
    // without it Hibernate keeps serving the pre-save row from its identity map for the rest of the
    // transaction, and the caller reads back what it just overwrote.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, training_id, body, updated_at)
        VALUES (:authorId, :trainingId, :body, :updatedAt)
        ON CONFLICT (author_id, training_id)
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForTraining(@Param("authorId") UUID authorId,
                           @Param("trainingId") UUID trainingId,
                           @Param("body") String body,
                           @Param("updatedAt") Instant updatedAt);

    // --- deletes ---------------------------------------------------------------------------------
    //
    // Deliberately NOT behind the athlete gate the reads and writes sit behind. Removing your own
    // text cannot leak anything, and a delete narrowed to (author, target) can only ever reach the
    // row the caller wrote. In the sibling app the symmetric version trapped data: clearing
    // somebody's athlete flag made notes about their trainings invisible AND undeletable at once,
    // leaving another person's data in the database with no way to remove it -- the exact opposite
    // of what the gate is for.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    int deleteForTraining(@Param("authorId") UUID authorId, @Param("trainingId") UUID trainingId);

    /**
     * Every note ANY author wrote about one person, for a GDPR access request (art. 15).
     * <p>
     * Notes on that person's trainings. The training is fetched eagerly so the export can name the
     * anchor without one query per note.
     */
    @Query("SELECT n FROM AdminPrivateNote n JOIN FETCH n.training t"
        + " WHERE t.athlete.id = :athleteId ORDER BY n.createdAt")
    List<AdminPrivateNote> findAllAboutAthlete(@Param("athleteId") UUID athleteId);

    /**
     * How many of ONE author's notes hang off that athlete's trainings.
     * <p>
     * Counted rather than deleted: those rows leave on their own when the trainings do, through the
     * foreign key's cascade. But a count is not optional — erasure reports back how many of the
     * caller's notes went. Only the caller's own: counting everybody's would quietly announce that
     * another admin keeps notes about this person, and how many. Must be asked BEFORE the trainings
     * are deleted.
     */
    @Query("SELECT COUNT(n) FROM AdminPrivateNote n JOIN n.training t"
        + " WHERE n.author.id = :authorId AND t.athlete.id = :athleteId")
    int countAboutAthleteTrainingsByAuthor(@Param("athleteId") UUID athleteId,
                                           @Param("authorId") UUID authorId);

    // --- markers ---------------------------------------------------------------------------------
    //
    // Identifiers only, never the text. A month at a time answers "is there a note here" and nothing
    // else; handing back the notes themselves would drop the notebook into the response that exists
    // to draw icons, undoing the reason a note has its own endpoint per target.

    @Query("SELECT n.training.id FROM AdminPrivateNote n JOIN n.training t WHERE n.author.id = :authorId"
        + " AND t.athlete.id = :athleteId AND t.date BETWEEN :from AND :to")
    List<UUID> markedTrainingIds(@Param("authorId") UUID authorId, @Param("athleteId") UUID athleteId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);
}
