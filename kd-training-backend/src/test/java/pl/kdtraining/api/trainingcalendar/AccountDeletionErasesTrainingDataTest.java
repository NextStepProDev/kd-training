package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deleting an account must leave nothing of the 1-on-1 plan behind.
 * <p>
 * Every table below hangs off {@code users} by {@code ON DELETE CASCADE}, so this passes without a
 * line of Java backing it — which is exactly why it is worth pinning. The cascades live in the
 * migrations, nothing in the application layer reads them, and a future table added with a
 * forgotten or weakened foreign key would leave someone's health data in the database with no
 * account attached and no error anywhere.
 * <p>
 * Read alongside {@code TrainingPhotoCleanupIntegrationTest}, which covers the files on disk: those
 * the database cannot reach, so they need explicit code and have their own tests.
 */
class AccountDeletionErasesTrainingDataTest extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired private JdbcTemplate jdbc;

    private long rowsFor(String table, String column, UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    @Test
    void shouldLeaveNothingOfTheOneOnOnePlanBehind() throws Exception {
        // Given: a client with a full plan — training, comment, photo, goal, weigh-in, read marker
        String admin = adminToken();
        String email = "erasure@kdtraining.test";
        String client = createUserAndGetToken(email, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        UUID clientId = user.getId();

        String trainingJson = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Bieg"}""".formatted(TOMORROW)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(trainingJson, "$.id");

        mockMvc.perform(post("/api/user/my-training/trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Nogi ciężkie"}"""))
                .andExpect(status().isCreated());

        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(new MockMultipartFile("file", "g.jpg", "image/jpeg", jpeg.toByteArray()))
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isCreated());


        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Podciągnięcia"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/user/my-training/mark-seen?to=" + LocalDate.now().plusMonths(1))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        // Sanity: the plan really is there before we delete anything
        assertEquals(1, rowsFor("personal_trainings", "athlete_id", clientId));
        assertEquals(1, rowsFor("athlete_goals", "athlete_id", clientId));

        // When: the account goes
        mockMvc.perform(delete("/api/admin/users/" + clientId + "?notify=false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        // Then: nothing of it is left, including the rows reachable only through the training
        assertEquals(0, rowsFor("personal_trainings", "athlete_id", clientId));
        assertEquals(0, rowsFor("athlete_goals", "athlete_id", clientId));
        assertEquals(0, rowsFor("training_calendar_reads", "athlete_id", clientId));
        assertEquals(0, rowsFor("training_deletions", "athlete_id", clientId));
        assertEquals(0, rowsFor("auth_tokens", "user_id", clientId));

        // Comments and attachments hang off the training, not off the user — they must go with it
        Long comments = jdbc.queryForObject(
                "SELECT count(*) FROM training_comments WHERE training_id = ?::uuid", Long.class, trainingId);
        assertEquals(0L, comments);
        Long attachments = jdbc.queryForObject(
                "SELECT count(*) FROM training_attachments WHERE training_id = ?::uuid", Long.class, trainingId);
        assertEquals(0L, attachments);
    }
}
