package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.training.TrainingComment;
import pl.kdtraining.domain.training.TrainingCommentRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.storage.FileStorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bytes on disk outlive rows unless something explicitly removes them.
 * <p>
 * {@code PersonalTraining} has no JPA cascade — comments disappear through {@code ON DELETE CASCADE}
 * without Hibernate ever loading them, so no entity callback can reach the files. These tests pin
 * every path that has to unlink them by hand, plus the nightly sweep that catches whatever those
 * paths miss when a transaction rolls back or a delete silently fails.
 */
class TrainingPhotoCleanupIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private TrainingCommentRepository commentRepository;
    @Autowired private TrainingPhotoRetentionService retention;
    @Autowired private TrainingPhotoService photoService;
    @Autowired private FileStorageService storage;

    // --- helpers --------------------------------------------------------------------------------

    private String flagAthlete(String email) {
        String token = createUserAndGetToken(email, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private String planTraining(String admin, UUID athleteId) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Bieg"}""".formatted(TOMORROW)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String upload(String token, String trainingId, String body) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB), "jpg", out);
        var request = MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                .file(new MockMultipartFile("file", "g.jpg", "image/jpeg", out.toByteArray()))
                .param("trainingId", trainingId)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.param("body", body);
        }
        String json = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String filenameOf(String commentId) {
        return commentRepository.findById(UUID.fromString(commentId)).orElseThrow().getPhotoFilename();
    }

    private boolean onDisk(String filename) {
        return storage.exists(TrainingPhotoService.FOLDER, filename);
    }

    // --- explicit unlink paths ------------------------------------------------------------------

    @Test
    void shouldUnlinkPhotosWhenTheTrainingIsDeleted() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-training@kdtraining.test");
        String trainingId = planTraining(admin, userRepository.findByEmail("cleanup-training@kdtraining.test").orElseThrow().getId());
        String filename = filenameOf(upload(client, trainingId, "notka"));
        assertTrue(onDisk(filename));

        mockMvc.perform(delete("/api/admin/personal-trainings/" + trainingId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertFalse(onDisk(filename), "the file must go with the training it hung off");
    }

    @Test
    void shouldUnlinkPhotosWhenTheAccountIsDeletedByAdmin() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-account@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-account@kdtraining.test").orElseThrow().getId();
        String filename = filenameOf(upload(client, planTraining(admin, clientId), null));

        mockMvc.perform(delete("/api/admin/users/" + clientId + "?notify=false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertFalse(onDisk(filename));
    }

    /**
     * The other half of {@code purgeForUser}, and the easy one to miss: a photo the coach wrote in
     * a client's thread. Deleting the coach's account only sets {@code author_id} to NULL through
     * the cascade, so without the OR branch in the query the file would outlive its author with
     * nothing left pointing at who sent it.
     */
    @Test
    void shouldUnlinkPhotosWrittenByTheDeletedUserInSomeoneElsesThread() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-author@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-author@kdtraining.test").orElseThrow().getId();
        String trainingId = planTraining(admin, clientId);

        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB), "jpg", out);
        String json = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(new MockMultipartFile("file", "g.jpg", "image/jpeg", out.toByteArray()))
                        .param("trainingId", trainingId)
                        .param("body", "Tak ma wyglądać ustawienie")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String commentId = JsonPath.read(json, "$.id");
        String filename = filenameOf(commentId);
        assertTrue(onDisk(filename));

        // The training is the client's; the photo is the coach's
        photoService.purgeForUser(adminUserId());

        assertFalse(onDisk(filename));
        // ...and the surviving comment must not still claim a photo. Its text stays (author_id is
        // only nulled), so a stale filename here would leave the client staring at a broken frame
        // for as long as the comment exists.
        TrainingComment survivor = commentRepository.findById(UUID.fromString(commentId)).orElseThrow();
        assertEquals("Tak ma wyglądać ustawienie", survivor.getBody());
        assertNull(survivor.getPhotoFilename());
        assertNull(survivor.getPhotoExpiresAt());
    }

    /** Same purge, but the coach sent only a picture — nothing readable is left, so the row goes. */
    @Test
    void shouldRemoveAPhotoOnlyCommentWhenItsAuthorIsDeleted() throws Exception {
        String admin = adminToken();
        flagAthlete("cleanup-author-silent@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-author-silent@kdtraining.test").orElseThrow().getId();
        String trainingId = planTraining(admin, clientId);

        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB), "jpg", out);
        String json = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(new MockMultipartFile("file", "g.jpg", "image/jpeg", out.toByteArray()))
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String commentId = JsonPath.read(json, "$.id");
        String filename = filenameOf(commentId);

        photoService.purgeForUser(adminUserId());

        assertFalse(onDisk(filename));
        assertTrue(commentRepository.findById(UUID.fromString(commentId)).isEmpty());
    }

    /**
     * Clearing the athlete flag deletes nothing, exactly as with goals (users.is_athlete and training_consent_at in V1) —
     * the data comes back if the arrangement resumes. Access is what is cut off, and the photo is
     * on a 30-day clock regardless. This is a decision, so it is asserted rather than assumed.
     */
    @Test
    void shouldKeepPhotosWhenTheAthleteFlagIsCleared() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-unflag@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-unflag@kdtraining.test").orElseThrow().getId();
        String commentId = upload(client, planTraining(admin, clientId), null);
        String filename = filenameOf(commentId);

        mockMvc.perform(delete("/api/admin/users/" + clientId + "/athlete")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertTrue(onDisk(filename), "dropping the flag must not destroy anything");

        // ...but nobody can reach it any more, and the coach gets the same 404 as a stranger
        mockMvc.perform(get("/api/admin/personal-trainings/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // --- the nightly sweep ----------------------------------------------------------------------

    @Test
    void shouldDeleteExpiredPhotosAndKeepTheWords() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-expiry@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-expiry@kdtraining.test").orElseThrow().getId();
        String trainingId = planTraining(admin, clientId);
        String withText = upload(client, trainingId, "nogi ciężkie");
        String photoOnly = upload(client, trainingId, null);
        String textFile = filenameOf(withText);
        String onlyFile = filenameOf(photoOnly);

        expire(withText, photoOnly);

        assertEquals(2, retention.deleteExpired());

        assertFalse(onDisk(textFile));
        assertFalse(onDisk(onlyFile));
        // The comment that had something to say survives without its picture
        TrainingComment kept = commentRepository.findById(UUID.fromString(withText)).orElseThrow();
        assertEquals("nogi ciężkie", kept.getBody());
        assertNull(kept.getPhotoFilename());
        // The one that was only a picture has nothing left to be
        assertTrue(commentRepository.findById(UUID.fromString(photoOnly)).isEmpty());
    }

    /**
     * The safety net. Every explicit unlink runs inside a transaction that can roll back, and
     * {@code LocalFileStorageService.delete} logs failures instead of raising them — so without this
     * pass a lost delete would leave somebody's health data on disk permanently.
     */
    @Test
    void shouldSweepFilesNoRowClaims() throws Exception {
        Path folder = Path.of(System.getProperty("java.io.tmpdir"), "kd-training-test-uploads",
                TrainingPhotoService.FOLDER);
        Files.createDirectories(folder);

        String orphan = UUID.randomUUID() + ".jpg";
        Path orphanPath = folder.resolve(orphan);
        Files.write(orphanPath, "leaked".getBytes());
        // Backdate it past the grace window, as a genuinely leaked file would be by the next sweep
        Files.setLastModifiedTime(orphanPath,
                java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));

        retention.deleteOrphans();

        assertFalse(onDisk(orphan));
    }

    /** A file written between reading the rows and listing the folder is not an orphan. */
    @Test
    void shouldNotSweepAFileThatWasJustWritten() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("cleanup-fresh@kdtraining.test");
        UUID clientId = userRepository.findByEmail("cleanup-fresh@kdtraining.test").orElseThrow().getId();
        String filename = filenameOf(upload(client, planTraining(admin, clientId), null));

        Path folder = Path.of(System.getProperty("java.io.tmpdir"), "kd-training-test-uploads",
                TrainingPhotoService.FOLDER);
        String justArrived = UUID.randomUUID() + ".jpg";
        Files.write(folder.resolve(justArrived), "seconds old".getBytes());

        retention.deleteOrphans();

        assertTrue(onDisk(filename), "a file a row still points at must never be swept");
        assertTrue(onDisk(justArrived), "a file younger than the grace window must survive the sweep");
    }

    /**
     * A file the service could not have written — put there by hand, or left by something else
     * entirely. It is skipped rather than deleted, because we do not destroy what we cannot account
     * for; the point of the test is the rest of the pass, which must still finish. One stray name
     * aborting the sweep would leave every genuinely expired photo on disk, and the only trace would
     * be a stack trace in a log nobody reads at 03:45.
     */
    @Test
    void shouldKeepSweepingPastAFileItCouldNotHaveWritten() throws Exception {
        Path folder = Path.of(System.getProperty("java.io.tmpdir"), "kd-training-test-uploads",
                TrainingPhotoService.FOLDER);
        Files.createDirectories(folder);

        Path stray = folder.resolve("notatka.txt");
        Files.write(stray, "put here by hand".getBytes());

        String orphan = UUID.randomUUID() + ".jpg";
        Path orphanPath = folder.resolve(orphan);
        Files.write(orphanPath, "leaked".getBytes());
        Files.setLastModifiedTime(orphanPath,
                java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));

        try {
            assertDoesNotThrow(() -> retention.deleteOrphans());

            assertFalse(onDisk(orphan), "the real orphan must still be swept");
            assertTrue(Files.exists(stray), "an unrecognised file is left alone, not deleted");
        } finally {
            Files.deleteIfExists(stray);
        }
    }

    /** Backdates the photos of the given comments so the retention pass picks them up. */
    private void expire(String... commentIds) {
        for (String id : List.of(commentIds)) {
            TrainingComment comment = commentRepository.findById(UUID.fromString(id)).orElseThrow();
            comment.attachPhoto(comment.getPhotoFilename(),
                    comment.getPhotoWidth(), comment.getPhotoHeight(),
                    java.time.Instant.now().minusSeconds(60));
            commentRepository.save(comment);
        }
    }
}
