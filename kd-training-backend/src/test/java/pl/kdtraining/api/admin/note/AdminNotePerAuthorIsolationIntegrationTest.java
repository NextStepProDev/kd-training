package pl.kdtraining.api.admin.note;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * One notebook per admin, checked on every surface rather than on the one that happened to get a
 * test first.
 *
 * <p>The premise of the whole feature is that a note reaches its author and nobody else — not the
 * client, and not a second admin. A second admin is the easiest of those to forget, because there is
 * usually only one; the accounts exist though, since admins can be promoted from the panel.
 *
 * <p>Two admins, ALFA and BETA. ALFA writes; BETA must find nothing, overwrite nothing, delete
 * nothing, and learn nothing — including from a count.
 */
class AdminNotePerAuthorIsolationIntegrationTest extends BaseIntegrationTest {

    private static final String NOTES_ABOUT_ATHLETE =
        "SELECT count(*) FROM admin_private_notes n JOIN personal_trainings t ON t.id = n.training_id"
            + " WHERE t.athlete_id = ?";

    @Autowired private JdbcTemplate jdbc;

    private String beta() {
        return createUserAndGetToken("beta@kdtraining.test", "Beta", "Adminka", UserRole.ADMIN);
    }

    private UUID flagAthlete() {
        createUserAndGetToken("marek@kdtraining.test", "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail("marek@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return user.getId();
    }

    private String createTraining(String token, UUID athlete, LocalDate date) throws Exception {
        return JsonPath.read(mockMvc.perform(
                post("/api/admin/personal-trainings?athleteId=" + athlete)
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"date\":\"" + date + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void write(String path, String token, String body) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isNoContent());
    }

    private void expectBody(String path, String token, String expected) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value(expected));
    }

    private void expectNothing(String path, String token) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void shouldHideANoteFromASecondAdmin() throws Exception {
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        String trainingPath = "/api/admin/notes/training/" + createTraining(alfa, athlete, LocalDate.now());

        write(trainingPath, alfa, "ALFA-o-treningu");
        expectNothing(trainingPath, beta);

        // BETA writing on the same training makes their OWN note — it does not overwrite ALFA's.
        write(trainingPath, beta, "BETA-o-treningu");
        expectBody(trainingPath, alfa, "ALFA-o-treningu");
        expectBody(trainingPath, beta, "BETA-o-treningu");

        // BETA deleting removes only their own row.
        mockMvc.perform(delete(trainingPath).header("Authorization", "Bearer " + beta))
            .andExpect(status().isNoContent());
        expectBody(trainingPath, alfa, "ALFA-o-treningu");
        expectNothing(trainingPath, beta);
    }

    @Test
    void shouldKeepMarkersPerAdmin() throws Exception {
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        LocalDate today = LocalDate.now();
        String trainingId = createTraining(alfa, athlete, today);
        write("/api/admin/notes/training/" + trainingId, alfa, "ALFA-training");

        String markers = "/api/admin/notes/markers?athleteId=" + athlete
            + "&from=" + today + "&to=" + today.plusDays(6);
        mockMvc.perform(get(markers).header("Authorization", "Bearer " + beta))
            .andExpect(jsonPath("$.trainingIds").isEmpty());

        // ...and ALFA still sees it, so the emptiness above is isolation and not a broken query.
        mockMvc.perform(get(markers).header("Authorization", "Bearer " + alfa))
            .andExpect(jsonPath("$.trainingIds[0]").value(trainingId));
    }

    @Test
    void shouldNotTellOneAdminHowManyNotesTheOtherKeeps() throws Exception {
        // Erasing a plan deletes every note about that person, whoever wrote it — that is the point
        // of erasure. The COUNT that comes back must still describe only the caller's own, or the
        // response quietly reports the existence and size of the other admin's notebook.
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        String trainingPath = "/api/admin/notes/training/" + createTraining(alfa, athlete, LocalDate.now());
        write(trainingPath, alfa, "ALFA-o-treningu");
        write(trainingPath, beta, "BETA-o-treningu");
        assertEquals(2, jdbc.queryForObject(NOTES_ABOUT_ATHLETE, Long.class, athlete));

        mockMvc.perform(delete("/api/admin/users/" + athlete + "/training-plan")
                .header("Authorization", "Bearer " + beta))
            .andExpect(status().isOk())
            // BETA wrote exactly one. Two would be BETA learning that somebody else keeps notes here.
            .andExpect(jsonPath("$.notes").value(1));

        // Both are gone from the database — erasure is still complete.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM admin_private_notes", Long.class));
    }
}
