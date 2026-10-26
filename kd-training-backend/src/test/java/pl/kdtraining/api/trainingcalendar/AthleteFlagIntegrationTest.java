package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteFlagIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldFlagUserAsAthleteAndListThemOnTheRoster() throws Exception {
        // Given: a plain account, not on the roster yet
        String admin = adminToken();
        userToken();
        var userId = regularUserId();

        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // When: the admin flags them
        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(true));

        // Then: they show up on the roster
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(userId.toString()))
            .andExpect(jsonPath("$[0].firstName").value("User"));
    }

    @Test
    void shouldExposeAthleteFlagOnOwnProfile() throws Exception {
        // Given: a flagged account — the frontend shows the calendar tile off this flag,
        // so it must ride along on /me instead of costing an extra request
        String admin = adminToken();
        String user = userToken();
        var userId = regularUserId();

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + user))
            .andExpect(jsonPath("$.isAthlete").value(false));

        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + user))
            .andExpect(jsonPath("$.isAthlete").value(true));
    }

    @Test
    void shouldRemoveFromRosterWithoutDeletingTheAccount() throws Exception {
        // Given: a flagged account
        String admin = adminToken();
        userToken();
        var userId = regularUserId();
        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        // When: the flag is cleared
        mockMvc.perform(delete("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(false));

        // Then: gone from the roster, but the account itself is untouched
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/users/" + userId).header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(false));
    }

    @Test
    void shouldGiveEverythingBackWhenTheFlagIsSetAgain() throws Exception {
        // The flag is a switch, not a delete. Clearing it hides the plan; setting it again has to
        // return the same rows — trainings, comments, goals and weigh-ins alike. Anything that
        // vanished here would be unrecoverable, and nothing in the UI warns about that.
        String admin = adminToken();
        String client = userToken();
        var clientId = regularUserId();
        mockMvc.perform(post("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/user/me/training-consent")
                .header("Authorization", "Bearer " + client)).andExpect(status().isOk());

        // Given: a client with a filled calendar
        String training = JsonPath.read(mockMvc.perform(
                        post("/api/admin/personal-trainings?athleteId=" + clientId)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                    {"date":"%s","title":"Siła"}""".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/admin/personal-trainings/" + training + "/comments")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Pamiętaj o rozgrzewce"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Podciągnięcie x10"}"""))
                .andExpect(status().isCreated());

        // When: the flag goes off and back on
        mockMvc.perform(delete("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        // While off, even the coach cannot reach the plan — hidden, not deleted
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + LocalDate.now() + "&to=" + LocalDate.now().plusDays(7))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        // The rows come back, but the consent does not: clearing the flag ended the arrangement the
        // client agreed to, so the health data behind it waits for a fresh decision (users.training_consent_at).
        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/user/me/training-consent")
                .header("Authorization", "Bearer " + client)).andExpect(status().isOk());

        // Then: every piece is back, exactly as it was
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + LocalDate.now() + "&to=" + LocalDate.now().plusDays(7))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainings.length()").value(1))
                .andExpect(jsonPath("$.trainings[0].id").value(training))
                .andExpect(jsonPath("$.trainings[0].title").value("Siła"))
                .andExpect(jsonPath("$.trainings[0].commentCount").value(1));
        mockMvc.perform(get("/api/admin/personal-trainings/" + training + "/comments")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Pamiętaj o rozgrzewce"));
        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.active[0].content").value("Podciągnięcie x10"));
    }

    @Test
    void shouldRejectRosterAccessForNonAdmins() throws Exception {
        // Given: an ordinary account — the roster is coach-only
        String user = userToken();

        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFoundWhenFlaggingAnUnknownAccount() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/users/" + java.util.UUID.randomUUID() + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepFlaggingSeparateFromTheAdminRole() throws Exception {
        // Given: flagging someone as a coaching client must not touch their role — it is plain admin
        // work, not the super-admin privilege that promote/demote requires
        String admin = adminToken();
        String coachClient = createUserAndGetToken("client@kdtraining.test", "Ala", "Nowak", UserRole.USER);
        var clientId = userRepository.findByEmail("client@kdtraining.test").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(true))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.role").value("USER"));

        // And: they still cannot reach admin endpoints
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + coachClient))
            .andExpect(status().isForbidden());
    }
}
