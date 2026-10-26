package pl.kdtraining.api.admin;

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
 * Erasing one person's 1-on-1 plan — and, just as importantly, what it must leave alone: their
 * account, and every other client's plan.
 */
class AdminTrainingPlanErasureIntegrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private String flagAthlete(String email) {
        String token = createUserAndGetToken(email, "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private long count(String table, String where, Object arg) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Long.class, arg);
        return n == null ? 0 : n;
    }

    /** A client with a training, a goal and a private note on that training. */
    private void seedPlan(String admin, UUID athleteId) throws Exception {
        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + LocalDate.now() + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + athleteId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"horizon\":\"SHORT\",\"content\":\"10 podciągnięć\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/api/admin/notes/training/" + trainingId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"body\":\"o jego treningu\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldEraseThePlanAndTheNotesButLeaveTheAccountAndOtherClientsStanding() throws Exception {
        String admin = adminToken();
        flagAthlete("marek@kdtraining.test");
        flagAthlete("ola@kdtraining.test");
        UUID id = idOf("marek@kdtraining.test");
        UUID other = idOf("ola@kdtraining.test");
        seedPlan(admin, id);
        seedPlan(admin, other);

        mockMvc.perform(delete("/api/admin/users/" + id + "/training-plan")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trainings").value(1))
            .andExpect(jsonPath("$.goals").value(1))
            // The note leaves through the cascade with the training, and still has to be counted.
            .andExpect(jsonPath("$.notes").value(1));

        // Gone: the whole 1-on-1 side.
        assertEquals(0, count("personal_trainings", "athlete_id = ?", id));
        assertEquals(0, count("athlete_goals", "athlete_id = ?", id));
        assertEquals(1, count("admin_private_notes", "author_id = ?", adminUserId()),
            "only the other client's note may survive");

        // Untouched: the account itself, and the other client's plan.
        assertEquals(1, count("users", "id = ?", id));
        assertEquals(1, count("personal_trainings", "athlete_id = ?", other));
        assertEquals(1, count("athlete_goals", "athlete_id = ?", other));

        User after = userRepository.findById(id).orElseThrow();
        assertEquals(false, after.isAthlete());
        assertEquals(null, after.getTrainingConsentAt());
    }

    @Test
    void shouldStillEraseAfterTheAthleteFlagWasAlreadyCleared() throws Exception {
        // The stranded case: with the flag gone the coach's calendar is unreachable, so this is the
        // only route left to the data. If it refused here, the notes could never be removed at all.
        String admin = adminToken();
        flagAthlete("marek@kdtraining.test");
        UUID id = idOf("marek@kdtraining.test");
        seedPlan(admin, id);

        mockMvc.perform(delete("/api/admin/users/" + id + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        assertEquals(1, count("personal_trainings", "athlete_id = ?", id));

        mockMvc.perform(delete("/api/admin/users/" + id + "/training-plan")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        assertEquals(0, count("personal_trainings", "athlete_id = ?", id));
        assertEquals(0, count("admin_private_notes", "author_id = ?", adminUserId()));
    }

    @Test
    void shouldRefuseTheErasureToANonAdmin() throws Exception {
        flagAthlete("marek@kdtraining.test");
        mockMvc.perform(delete("/api/admin/users/" + idOf("marek@kdtraining.test") + "/training-plan")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }
}
