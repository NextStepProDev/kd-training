package pl.kdtraining.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.Set;

public interface FileStorageService {

    /** Stores under {@link StorePolicy#DEFAULT}. The common case: catalog artwork. */
    String store(String folder, MultipartFile file);

    /**
     * Stores under an explicit policy and reports what was actually written — callers that have to
     * record the dimensions (training photos, so the client can reserve the box) need them, and
     * only the encoder knows them.
     */
    StoredImage storeImage(String folder, MultipartFile file, StorePolicy policy);

    void delete(String folder, String filename);
    boolean exists(String folder, String filename);
    InputStream getInputStream(String folder, String filename);
    long getFileSize(String folder, String filename);

    /**
     * Every filename currently in the folder, for reconciling a folder against the rows that are
     * supposed to own it. Returns an empty set when the folder does not exist yet.
     */
    Set<String> listFilenames(String folder);

    /** When the file was written. Lets a sweep skip files younger than its own read of the database. */
    Instant getLastModified(String folder, String filename);

    record StoredImage(String filename, int width, int height, long bytes) {}
}
