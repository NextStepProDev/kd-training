package pl.kdtraining.domain.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseVideoRepository extends JpaRepository<ExerciseVideo, UUID> {

    Optional<ExerciseVideo> findByVideoKey(String videoKey);

    /**
     * Browsing the library in the admin panel. An empty query lists everything; archived clips are
     * hidden unless explicitly asked for, so a retired video stops being offered without vanishing
     * from the trainings that already use it.
     */
    @Query("""
        SELECT v FROM ExerciseVideo v
        WHERE (:query = '' OR v.searchText LIKE CONCAT('%', :query, '%'))
          AND (:includeArchived = true OR v.archivedAt IS NULL)
        ORDER BY v.name ASC
        """)
    Page<ExerciseVideo> search(@Param("query") String query,
                               @Param("includeArchived") boolean includeArchived,
                               Pageable pageable);

    /** Typeahead while attaching a video to a training — never offers archived clips. */
    @Query("""
        SELECT v FROM ExerciseVideo v
        WHERE v.archivedAt IS NULL
          AND (:query = '' OR v.searchText LIKE CONCAT('%', :query, '%'))
        ORDER BY v.name ASC
        """)
    List<ExerciseVideo> suggest(@Param("query") String query, Pageable pageable);
}
