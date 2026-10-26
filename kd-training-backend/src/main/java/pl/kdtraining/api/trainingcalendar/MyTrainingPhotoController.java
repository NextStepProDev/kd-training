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
 * Client side of comment photos. Sits under {@code /api/user/my-training}, so it inherits both the
 * athlete check in {@link TrainingAccessService} and the art. 9 consent gate in
 * {@code TrainingConsentInterceptor} without a line of its own.
 * <p>
 * The upload lives at {@code /photos} rather than under {@code /trainings/{id}/comments} for a
 * reason that is easy to mistake for arbitrary: {@code RateLimitFilter} picks a bucket by path
 * prefix alone, so an upload sharing the comment prefix would inherit the 120/min ceiling meant for
 * reading a calendar. Its own prefix is the only way to give it a tighter one without breaking the
 * filter's most-specific-first rule.
 */
@RestController
@RequestMapping("/api/user/my-training")
public class MyTrainingPhotoController {

    private final TrainingPhotoService service;

    public MyTrainingPhotoController(TrainingPhotoService service) {
        this.service = service;
    }

    @PostMapping("/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingCommentResponse upload(@CurrentUserId UUID userId,
                                          @RequestParam UUID trainingId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) @Nullable String body) {
        return service.addPhotoComment(trainingId, body, file, userId, false);
    }

    @GetMapping("/comments/{commentId}/photo")
    public ResponseEntity<InputStreamResource> photo(@CurrentUserId UUID userId, @PathVariable UUID commentId) {
        return TrainingPhotoResponses.stream(service.open(commentId, userId, false));
    }

    @DeleteMapping("/comments/{commentId}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@CurrentUserId UUID userId, @PathVariable UUID commentId) {
        service.deletePhoto(commentId, userId, false);
    }
}
