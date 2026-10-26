package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.training.TrainingComment;
import pl.kdtraining.domain.training.TrainingCommentRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.scheduler.TrainingPhotoRetentionScheduler;
import pl.kdtraining.infrastructure.storage.FileStorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The nightly sweep must actually run inside a transaction — and the only path that matters is the
 * one the scheduler takes.
 * <p>
 * This is the test the previous one could not be. {@code TrainingPhotoCleanupIntegrationTest}
 * injects the bean and calls a pass directly, so it enters through the Spring proxy, where
 * {@code @Transactional} has always worked. Production enters through {@code sweep()}, and a pass
 * invoked from a sibling method on {@code this} never touches that proxy — the annotation is silent
 * there. Asserting on the observable consequence (files deleted, rows cleared) cannot tell the two
 * apart, because every repository call opens a transaction of its own and the work completes either
 * way. Only the presence of a surrounding transaction distinguishes them, so that is what is
 * asserted, at the one moment it is provable: inside the pass, mid-loop.
 */
class TrainingPhotoTransactionIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private TrainingCommentRepository commentRepository;
    @Autowired private TrainingPhotoRetentionScheduler scheduler;
    @Autowired private TrainingPhotoRetentionService retention;
    @MockitoSpyBean private FileStorageService storage;

    /**
     * The sweep runs two passes over rows it has already read. Losing the surrounding transaction
     * costs atomicity of the batch: a crash mid-loop leaves some photos unlinked and some not.
     * Harmless in itself — the pass is idempotent and runs again the next night — but it is the
     * guarantee the annotation claims to give, and today it does not give it.
     */
    @Test
    void shouldRunTheRetentionPassInsideATransactionWhenDrivenByTheScheduler() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("tx-sweep@kdtraining.test");
        UUID clientId = userRepository.findByEmail("tx-sweep@kdtraining.test").orElseThrow().getId();
        String commentId = upload(client, planTraining(admin, clientId));
        // Read before the sweep: this comment is nothing but a photo, so the row goes with it
        String filename = filenameOf(commentId);
        expire(commentId);

        AtomicBoolean transactionWasActive = recordTransactionStateOnDelete();

        scheduler.sweep();

        assertFalse(onDisk(filename), "the expired photo must be gone either way");
        assertTrue(transactionWasActive.get(),
                "the retention pass must run inside a transaction when reached through sweep()");
    }

    /**
     * The path a test naturally takes — injected bean, called straight — has always been
     * transactional, and has to stay that way. Kept alongside the one above so that the pair says
     * what the pair is for: the two entry points must behave identically, and until the passes moved
     * out of the scheduler they did not.
     */
    @Test
    void shouldRunTheRetentionPassInsideATransactionWhenCalledDirectly() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("tx-direct@kdtraining.test");
        UUID clientId = userRepository.findByEmail("tx-direct@kdtraining.test").orElseThrow().getId();
        String commentId = upload(client, planTraining(admin, clientId));
        String filename = filenameOf(commentId);
        expire(commentId);

        AtomicBoolean transactionWasActive = recordTransactionStateOnDelete();

        retention.deleteExpired();

        assertFalse(onDisk(filename));
        assertTrue(transactionWasActive.get());
    }

    // --- helpers --------------------------------------------------------------------------------

    /**
     * Watches the one call the pass makes from inside its own loop. The real method still runs, so
     * the sweep behaves exactly as it does in production while being observed.
     */
    private AtomicBoolean recordTransactionStateOnDelete() {
        AtomicBoolean active = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                active.set(true);
            }
            return invocation.callRealMethod();
        }).when(storage).delete(eq(TrainingPhotoService.FOLDER), anyString());
        return active;
    }

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

    private String upload(String token, String trainingId) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB), "jpg", out);
        String json = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(new MockMultipartFile("file", "g.jpg", "image/jpeg", out.toByteArray()))
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + token))
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

    /** Backdates the photo so the retention pass picks it up. */
    private void expire(String commentId) {
        TrainingComment comment = commentRepository.findById(UUID.fromString(commentId)).orElseThrow();
        comment.attachPhoto(comment.getPhotoFilename(), comment.getPhotoWidth(), comment.getPhotoHeight(),
                Instant.now().minusSeconds(60));
        commentRepository.save(comment);
    }
}
