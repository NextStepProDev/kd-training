package pl.kdtraining.api.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerIntegrationTest extends BaseIntegrationTest {

    private String testFilename;

    @BeforeEach
    void setUpFile() throws Exception {
        String storageRoot = System.getProperty("java.io.tmpdir") + "/kd-training-test-uploads";
        Path dir = Path.of(storageRoot, "avatars");
        Files.createDirectories(dir);

        testFilename = UUID.randomUUID() + ".jpg";
        Files.write(dir.resolve(testFilename), "fake image data".getBytes());
    }

    @Test
    void shouldServeExistingFile() throws Exception {
        mockMvc.perform(get("/api/files/avatars/" + testFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
            .andExpect(header().exists("Cache-Control"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void shouldReturn404ForNonExistentFile() throws Exception {
        String fakeUuid = UUID.randomUUID() + ".jpg";
        mockMvc.perform(get("/api/files/avatars/" + fakeUuid))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidFolderName() throws Exception {
        mockMvc.perform(get("/api/files/INVALID/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    /**
     * This endpoint is unauthenticated and publicly cacheable, so the folder allowlist is what keeps
     * it to catalog artwork. The name pattern alone accepts any lowercase word, which meant every
     * folder ever created under the storage root became world-readable once a filename leaked.
     */
    @Test
    void shouldRefuseAFolderThatIsNotPublic() throws Exception {
        String storageRoot = System.getProperty("java.io.tmpdir") + "/kd-training-test-uploads";
        Path dir = Path.of(storageRoot, "trainingphotos");
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + ".jpg";
        Files.write(dir.resolve(filename), "health data".getBytes());

        // The file is right there and the name is well-formed. It still must not come back.
        mockMvc.perform(get("/api/files/trainingphotos/" + filename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFolderWithNumbers() throws Exception {
        mockMvc.perform(get("/api/files/folder123/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFolderWithSpecialChars() throws Exception {
        mockMvc.perform(get("/api/files/fold-er/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidFilename() throws Exception {
        mockMvc.perform(get("/api/files/avatars/not-a-uuid.jpg"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUnsupportedExtension() throws Exception {
        mockMvc.perform(get("/api/files/avatars/" + UUID.randomUUID() + ".gif"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPathTraversal() throws Exception {
        mockMvc.perform(get("/api/files/avatars/../../../etc/passwd"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSetCacheControlHeader() throws Exception {
        mockMvc.perform(get("/api/files/avatars/" + testFilename))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=604800")));
    }

    @Test
    void shouldDetectPngContentType() throws Exception {
        String pngFilename = UUID.randomUUID() + ".png";
        String storageRoot = System.getProperty("java.io.tmpdir") + "/kd-training-test-uploads";
        Files.write(Path.of(storageRoot, "avatars", pngFilename), "png data".getBytes());

        mockMvc.perform(get("/api/files/avatars/" + pngFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/png"));
    }

    @Test
    void shouldDetectWebpContentType() throws Exception {
        String webpFilename = UUID.randomUUID() + ".webp";
        String storageRoot = System.getProperty("java.io.tmpdir") + "/kd-training-test-uploads";
        Files.write(Path.of(storageRoot, "avatars", webpFilename), "webp data".getBytes());

        mockMvc.perform(get("/api/files/avatars/" + webpFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/webp"));
    }
}
