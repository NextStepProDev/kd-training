package pl.kdtraining.api.file;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.infrastructure.storage.FileStorageService;
import pl.kdtraining.infrastructure.storage.StoragePaths;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
public class FileController {

    /**
     * The folders this endpoint may hand out. Everything here is unauthenticated and publicly
     * cacheable (see SecurityConfig), so the list is the line between "a picture meant to be
     * shown" and "somebody's private file".
     * <p>
     * Without it the path pattern alone decided, and it accepts ANY lowercase name — so any folder
     * ever created under the storage root became world-readable the moment a filename leaked. An
     * allowlist inverts that: a new folder is private until someone deliberately adds it here.
     * Training photos are health data and must never appear on this list.
     */
    private static final Set<String> PUBLIC_FOLDERS =
        Set.of("avatars");

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable String folder, @PathVariable String filename) {
        // The shapes come from StoragePaths, which the storage layer also enforces. The rule is one
        // rule; only the answer differs — a bad request is a 400 here, an outright refusal there.
        if (!StoragePaths.FOLDER.matcher(folder).matches() || !PUBLIC_FOLDERS.contains(folder)) {
            return ResponseEntity.badRequest().build();
        }
        if (!StoragePaths.FILENAME.matcher(filename).matches()) {
            return ResponseEntity.badRequest().build();
        }
        if (!fileStorageService.exists(folder, filename)) {
            return ResponseEntity.notFound().build();
        }
        InputStream inputStream = fileStorageService.getInputStream(folder, filename);
        long fileSize = fileStorageService.getFileSize(folder, filename);
        String contentType = filename.endsWith(".png") ? "image/png" : filename.endsWith(".webp") ? "image/webp" : "image/jpeg";

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(fileSize)
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(inputStream));
    }
}
