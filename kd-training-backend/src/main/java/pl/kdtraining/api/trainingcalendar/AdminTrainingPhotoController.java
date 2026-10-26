package pl.kdtraining.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.kdtraining.api.trainingcalendar.TrainingCalendarDtos.TrainingCommentResponse;
import pl.kdtraining.config.CurrentUserId;

import java.util.UUID;

/**
 * Coach side of comment photos. Role protection comes from the {@code /api/admin/**} prefix.
 * <p>
 * The upload sits at {@code /api/admin/training-photos}, outside the {@code personal-trainings}
 * prefix, so {@code RateLimitFilter} can give it its own tighter bucket — same reasoning as
 * {@link MyTrainingPhotoController}. Reading a photo stays on the ordinary admin ceiling, because
 * opening a few trainings in a row is a dozen GETs and should not be rationed.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminTrainingPhotoController {

    private final TrainingPhotoService service;

    public AdminTrainingPhotoController(TrainingPhotoService service) {
        this.service = service;
    }

    @PostMapping("/training-photos")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingCommentResponse upload(@CurrentUserId UUID adminId,
                                          @RequestParam UUID trainingId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) @Nullable String body) {
        return service.addPhotoComment(trainingId, body, file, adminId, true);
    }

    @GetMapping("/personal-trainings/comments/{commentId}/photo")
    public ResponseEntity<InputStreamResource> photo(@CurrentUserId UUID adminId, @PathVariable UUID commentId) {
        return TrainingPhotoResponses.stream(service.open(commentId, adminId, true));
    }

    @DeleteMapping("/personal-trainings/comments/{commentId}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@CurrentUserId UUID adminId, @PathVariable UUID commentId) {
        service.deletePhoto(commentId, adminId, true);
    }
}
