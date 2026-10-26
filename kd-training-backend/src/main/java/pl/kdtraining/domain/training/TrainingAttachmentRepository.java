package pl.kdtraining.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TrainingAttachmentRepository extends JpaRepository<TrainingAttachment, UUID> {

    /** Materials for a whole calendar page in one query. */
    @Query("""
        SELECT a FROM TrainingAttachment a
        LEFT JOIN FETCH a.video
        WHERE a.training.id IN :trainingIds
        ORDER BY a.position ASC
        """)
    List<TrainingAttachment> findForTrainings(@Param("trainingIds") List<UUID> trainingIds);

    @Query("""
        SELECT a FROM TrainingAttachment a
        LEFT JOIN FETCH a.video
        WHERE a.template.id = :templateId
        ORDER BY a.position ASC
        """)
    List<TrainingAttachment> findForTemplate(@Param("templateId") UUID templateId);

    @Modifying
    @Query("DELETE FROM TrainingAttachment a WHERE a.training.id = :trainingId")
    void deleteForTraining(@Param("trainingId") UUID trainingId);

    @Modifying
    @Query("DELETE FROM TrainingAttachment a WHERE a.template.id = :templateId")
    void deleteForTemplate(@Param("templateId") UUID templateId);

    /**
     * Whether a library video is in use anywhere. The FK is ON DELETE RESTRICT, so the database
     * would refuse the delete regardless — this exists to answer with a readable 409 instead of a
     * constraint violation.
     */
    boolean existsByVideoId(UUID videoId);
}
