package pl.kdtraining.api.trainingcalendar;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.*;

import java.util.List;
import java.util.UUID;

/** The coach's own material: the exercise-video library and the training templates. */
@RestController
@RequestMapping("/api/admin")
public class AdminTrainingLibraryController {

    private final ExerciseVideoService videos;
    private final TrainingTemplateService templates;

    public AdminTrainingLibraryController(ExerciseVideoService videos, TrainingTemplateService templates) {
        this.videos = videos;
        this.templates = templates;
    }

    @GetMapping("/exercise-videos")
    public PagedExerciseVideos listVideos(@RequestParam(defaultValue = "") String query,
                                          @RequestParam(defaultValue = "false") boolean includeArchived,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return videos.list(query, includeArchived, page, size);
    }

    /** Typeahead used while attaching a clip to a training. */
    @GetMapping("/exercise-videos/search")
    public List<ExerciseVideoResponse> suggestVideos(@RequestParam(defaultValue = "") String query) {
        return videos.suggest(query);
    }

    /**
     * Looks a pasted link up on YouTube before anything is saved: the title to prefill the name,
     * and whether the clip will embed at all. Called from the form as the link is pasted.
     */
    @GetMapping("/exercise-videos/metadata")
    public VideoMetadataResponse videoMetadata(@RequestParam String url) {
        return videos.metadata(url);
    }

    @PostMapping("/exercise-videos")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseVideoResponse createVideo(@Valid @RequestBody ExerciseVideoRequest request) {
        return videos.create(request);
    }

    @PutMapping("/exercise-videos/{id}")
    public ExerciseVideoResponse updateVideo(@PathVariable UUID id,
                                             @Valid @RequestBody ExerciseVideoRequest request) {
        return videos.update(id, request);
    }

    /** Refused with 409 for a clip in use — archive it instead, so history keeps its demonstration. */
    @DeleteMapping("/exercise-videos/{id}")
    public ResponseEntity<Void> deleteVideo(@PathVariable UUID id) {
        videos.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exercise-videos/{id}/archive")
    public ExerciseVideoResponse archiveVideo(@PathVariable UUID id) {
        return videos.setArchived(id, true);
    }

    @PostMapping("/exercise-videos/{id}/restore")
    public ExerciseVideoResponse restoreVideo(@PathVariable UUID id) {
        return videos.setArchived(id, false);
    }

    @GetMapping("/training-templates")
    public List<TrainingTemplateResponse> listTemplates() {
        return templates.list();
    }

    @PostMapping("/training-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingTemplateResponse createTemplate(@Valid @RequestBody TrainingTemplateRequest request) {
        return templates.create(request);
    }

    @PutMapping("/training-templates/{id}")
    public TrainingTemplateResponse updateTemplate(@PathVariable UUID id,
                                                   @Valid @RequestBody TrainingTemplateRequest request) {
        return templates.update(id, request);
    }

    @DeleteMapping("/training-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templates.delete(id);
        return ResponseEntity.noContent().build();
    }
}
