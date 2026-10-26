package pl.kdtraining.api.trainingcalendar;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The one place the photo response is assembled, so the coach's endpoint and the client's cannot
 * drift into serving the same health data under different rules.
 */
final class TrainingPhotoResponses {

    private TrainingPhotoResponses() {}

    static ResponseEntity<InputStreamResource> stream(TrainingPhotoService.PhotoStream photo) {
        return ResponseEntity.ok()
                // Always JPEG: StorePolicy.TRAINING_PHOTO re-encodes every upload, so this is what
                // the bytes are rather than what someone claimed they were.
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(photo.size())
                // no-store, unlike the 7-day public cache on /api/files. Health data must not settle
                // in a browser's disk cache or anywhere between here and the reader. It costs the
                // user nothing: the frontend keeps the blob in memory for the session.
                .header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(new InputStreamResource(photo.inputStream()));
    }
}
