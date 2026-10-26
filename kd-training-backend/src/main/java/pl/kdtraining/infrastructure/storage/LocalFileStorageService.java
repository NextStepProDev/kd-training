package pl.kdtraining.infrastructure.storage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final Path rootLocation;
    private final ImageOptimizer imageOptimizer;

    public LocalFileStorageService(@Value("${app.storage.root:./uploads}") String storageRoot,
                                   ImageOptimizer imageOptimizer) {
        this.rootLocation = Path.of(storageRoot);
        this.imageOptimizer = imageOptimizer;
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    public String store(String folder, MultipartFile file) {
        return storeImage(folder, file, StorePolicy.DEFAULT).filename();
    }

    @Override
    public StoredImage storeImage(String folder, MultipartFile file, StorePolicy policy) {
        // The filename below is minted here, so only the folder can carry a caller's mistake — and it
        // is the more dangerous half: a folder that escapes the root escapes it for a write.
        StoragePaths.requireFolder(folder);

        if (file.getSize() > policy.maxBytes()) {
            throw new IllegalArgumentException(
                "Plik jest za duży. Maksymalny rozmiar to " + policy.maxBytes() / (1024 * 1024) + " MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !policy.allowedContentTypes().contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Niedozwolony typ pliku. Dozwolone: " + describe(policy));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase()
            : "";
        if (!policy.allowedExtensions().contains(extension)) {
            throw new IllegalArgumentException(
                "Niedozwolone rozszerzenie pliku. Dozwolone: " + String.join(", ", policy.allowedExtensions()));
        }

        // Everything checked so far — the content type and the extension — was written by the client.
        // The bytes are the only part of the upload it cannot lie about.
        ImageFormat format = detectFormat(file);
        if (format == null) {
            throw new IllegalArgumentException("Plik nie jest obrazem JPG, PNG ani WebP");
        }
        if (!format.matchesExtension(extension)) {
            // Extension and content disagree. The served content type comes from the extension, so
            // storing this would hand the file back out described as something it is not.
            throw new IllegalArgumentException("Zawartość pliku nie zgadza się z jego rozszerzeniem");
        }

        // A real JPEG/PNG always decodes. One carrying the right signature that then fails to is truncated
        // or damaged; the decoder signals that either by returning nothing or by throwing. Both are the
        // uploader's problem, not the server's, so both become a 400 — this used to surface as a 500.
        ImageOptimizer.OptimizedImage optimized;
        try {
            optimized = imageOptimizer.optimize(file.getInputStream(), extension, policy.profile());
        } catch (IOException e) {
            log.debug("Rejected an undecodable {} upload: {}", extension, e.getMessage());
            throw new IllegalArgumentException("Plik obrazu jest uszkodzony");
        }
        if (format.isDecodable() && !optimized.decoded()) {
            throw new IllegalArgumentException("Plik obrazu jest uszkodzony");
        }

        try {
            String finalExtension = optimized.extension();
            String filename = UUID.randomUUID() + finalExtension;
            Path dir = rootLocation.resolve(folder);
            Files.createDirectories(dir);
            long written = Files.copy(optimized.inputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}/{}", folder, filename);
            return new StoredImage(filename, optimized.width(), optimized.height(), written);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    private static String describe(StorePolicy policy) {
        return policy.allowedContentTypes().stream()
            .map(type -> type.replace("image/", "").toUpperCase())
            .sorted()
            .collect(Collectors.joining(", "));
    }

    /** Reads just the signature. Multipart streams can be reopened, so the optimizer still sees the whole file. */
    @Nullable
    private static ImageFormat detectFormat(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return ImageFormat.sniff(in.readNBytes(ImageFormat.HEADER_BYTES));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    /**
     * The single point where a folder and a filename become a path. Everything that reads or removes
     * a file goes through here, so the check cannot be forgotten by one method the way it would be
     * if each resolved its own path.
     */
    private Path resolve(String folder, String filename) {
        StoragePaths.require(folder, filename);
        return rootLocation.resolve(folder).resolve(filename);
    }

    @Override
    public void delete(String folder, String filename) {
        Path path = resolve(folder, filename);
        try {
            Files.deleteIfExists(path);
            log.info("Deleted file: {}/{}", folder, filename);
        } catch (IOException e) {
            log.error("Failed to delete file: {}/{}", folder, filename, e);
        }
    }

    @Override
    public boolean exists(String folder, String filename) {
        return Files.exists(resolve(folder, filename));
    }

    @Override
    public InputStream getInputStream(String folder, String filename) {
        try {
            return Files.newInputStream(resolve(folder, filename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public long getFileSize(String folder, String filename) {
        try {
            return Files.size(resolve(folder, filename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get file size", e);
        }
    }

    @Override
    public Set<String> listFilenames(String folder) {
        StoragePaths.requireFolder(folder);
        Path dir = rootLocation.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (var entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Failed to list folder: {}", folder, e);
            return Set.of();
        }
    }

    @Override
    public Instant getLastModified(String folder, String filename) {
        Path path = resolve(folder, filename);
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            // Treat an unreadable timestamp as "just written" so a sweep leaves it alone. Losing a
            // pass costs a day; deleting a file that a transaction is still about to claim is worse.
            log.warn("Failed to read modification time of {}/{}", folder, filename);
            return Instant.now();
        }
    }
}
