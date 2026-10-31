package pl.kdtraining.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    /**
     * Real encoded images, because uploads are now accepted on their signature. These tests used to post
     * strings like "image data" and pass — which was the hole, not a convenience.
     */
    private static byte[] image(String format) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** A minimal RIFF/WEBP container — the JDK has no WebP encoder, and none is needed for a signature. */
    private static byte[] webp() {
        byte[] bytes = new byte[64];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    private static MultipartFile upload(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());
    }

    @Test
    void shouldStoreFileSuccessfully() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("instructors", file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".jpg"));
        assertTrue(storageService.exists("instructors", filename));
    }

    @Test
    void shouldStorePngFile() throws Exception {
        MultipartFile file = upload("image.png", "image/png", image("png"));

        String filename = storageService.store("eventtypes", file);

        assertTrue(filename.endsWith(".png"));
        assertTrue(storageService.exists("eventtypes", filename));
    }

    @Test
    void shouldStoreWebpFile() throws Exception {
        MultipartFile file = upload("image.webp", "image/webp", webp());

        String filename = storageService.store("instructors", file);

        assertTrue(filename.endsWith(".webp"));
    }

    @Test
    void shouldRejectDisallowedContentType() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectNullContentType() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectDisallowedExtension() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("file.gif");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectFileWithoutExtension() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("filename");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldDeleteFile() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("instructors", file);
        assertTrue(storageService.exists("instructors", filename));

        storageService.delete("instructors", filename);
        assertFalse(storageService.exists("instructors", filename));
    }

    @Test
    void shouldKeepTheFileWhenTheTransactionThatDeletedItRollsBack() throws Exception {
        // A row change that rolls back must find its file still there: deleting at once left an
        // avatar or photo row pointing at bytes that were already gone.
        String filename = storageService.store("instructors", upload("photo.jpg", "image/jpeg", image("jpg")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            storageService.delete("instructors", filename);
            assertTrue(storageService.exists("instructors", filename), "Nothing may be unlinked before the commit");

            TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertTrue(storageService.exists("instructors", filename), "A rollback keeps the file");
    }

    @Test
    void shouldDeleteTheFileOnceTheTransactionCommits() throws Exception {
        String filename = storageService.store("instructors", upload("photo.jpg", "image/jpeg", image("jpg")));
        TransactionSynchronizationManager.initSynchronization();
        try {
            storageService.delete("instructors", filename);
            assertTrue(storageService.exists("instructors", filename));

            TransactionSynchronizationUtils.triggerAfterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertFalse(storageService.exists("instructors", filename), "After the commit the file goes");
    }

    @Test
    void shouldNotThrowWhenDeletingNonExistentFile() {
        assertDoesNotThrow(() -> storageService.delete("instructors", absentName()));
    }

    @Test
    void shouldReturnFileInputStream() throws Exception {
        byte[] stored = image("jpg");
        MultipartFile file = upload("photo.jpg", "image/jpeg", stored);

        String filename = storageService.store("instructors", file);

        try (InputStream is = storageService.getInputStream("instructors", filename)) {
            assertNotNull(is);
            assertArrayEquals(stored, is.readAllBytes());
        }
    }

    @Test
    void shouldReturnFileSize() throws Exception {
        byte[] data = image("png");
        MultipartFile file = upload("image.png", "image/png", data);

        String filename = storageService.store("instructors", file);

        assertEquals(data.length, storageService.getFileSize("instructors", filename));
    }

    @Test
    void shouldReturnFalseForNonExistentFile() {
        assertFalse(storageService.exists("instructors", absentName()));
    }

    @Test
    void shouldCreateDirectoriesAutomatically() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("newfolder", file);

        assertTrue(Files.exists(tempDir.resolve("newfolder")));
        assertTrue(storageService.exists("newfolder", filename));
    }

    @Test
    void shouldGenerateUniqueFilenames() throws Exception {
        MultipartFile file1 = upload("same.jpg", "image/jpeg", image("jpg"));
        MultipartFile file2 = upload("same.jpg", "image/jpeg", image("jpg"));

        String name1 = storageService.store("instructors", file1);
        String name2 = storageService.store("instructors", file2);

        assertNotEquals(name1, name2);
    }

    // --- names this service could never have written ---------------------------------------------

    /**
     * Nothing reaches these methods with a name it did not mint itself: names come from the database,
     * and the one public endpoint that takes a filename from a URL screens it first. That is exactly
     * why the check belongs here too — a layer whose safety rests entirely on every caller staying
     * careful is one refactor away from not being safe, and the failure would be silent.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "../../../etc/passwd",
        "../secret.jpg",
        "/etc/passwd",
        "not-a-uuid.jpg",
        "12345678-1234-1234-1234-123456789abc.exe",
        "12345678-1234-1234-1234-123456789ABC.jpg",  // uppercase: never produced by randomUUID()
        "12345678-1234-1234-1234-123456789abc.jpg.txt",
        "12345678-1234-1234-1234-123456789abc.jpg\0.txt"  // null byte, in case anything downstream truncates
    })
    void shouldRejectAFilenameItCouldNotHaveWritten(String filename) {
        assertThrows(IllegalArgumentException.class, () -> storageService.delete("instructors", filename));
        assertThrows(IllegalArgumentException.class, () -> storageService.exists("instructors", filename));
        assertThrows(IllegalArgumentException.class, () -> storageService.getInputStream("instructors", filename));
        assertThrows(IllegalArgumentException.class, () -> storageService.getFileSize("instructors", filename));
        assertThrows(IllegalArgumentException.class, () -> storageService.getLastModified("instructors", filename));
    }

    /** A folder is chosen in code, so anything shaped otherwise is a bug — including on the way in. */
    @ParameterizedTest
    @ValueSource(strings = {"..", "../uploads", "avatars/../trainingphotos", "Avatars", "training_photos"})
    void shouldRejectAFolderOutsideItsOwnNamingRules(String folder) {
        assertThrows(IllegalArgumentException.class, () -> storageService.exists(folder, absentName()));
        assertThrows(IllegalArgumentException.class, () -> storageService.listFilenames(folder));
        assertThrows(IllegalArgumentException.class,
            () -> storageService.store(folder, upload("photo.jpg", "image/jpeg", image("jpg"))));
    }

    /** The consequence in full: a traversal name must not be able to read a file outside the root. */
    @Test
    void shouldNotReachAFileOutsideTheStorageRoot() throws Exception {
        Path outside = tempDir.getParent().resolve("outside-" + UUID.randomUUID() + ".jpg");
        Files.writeString(outside, "not yours");
        try {
            String traversal = "../" + outside.getFileName();

            assertThrows(IllegalArgumentException.class, () -> storageService.exists("instructors", traversal));
            assertTrue(Files.exists(outside), "the file outside the root must still be there, untouched");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    /** A valid-looking name for a file that was never stored — the "missing", not "malformed", case. */
    private static String absentName() {
        return UUID.randomUUID() + ".jpg";
    }
}
