package pl.kdtraining.api.trainingcalendar;

import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The GDPR art. 9(2)(a) gate in front of the client's own calendar (users.training_consent_at).
 * <p>
 * Fixtures here deliberately do NOT grant consent — this is the state every client who predates the
 * consent screen starts in, and the one the interceptor exists for.
 */
class TrainingConsentIntegrationTest extends BaseIntegrationTest {

    private String flagClientWithoutConsent() {
        String token = userToken();
        User user = userRepository.findById(regularUserId()).orElseThrow();
        user.setAthlete(true);
        userRepository.save(user);
        return token;
    }

    @Test
    void shouldRefuseTheClientsCalendarUntilConsentIsGiven() throws Exception {
        String client = flagClientWithoutConsent();

        mockMvc.perform(get("/api/user/my-training/calendar?from=" + LocalDate.now()
                        + "&to=" + LocalDate.now().plusDays(7))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"weightKg":74.2}"""))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldLetTheClientInOnceConsentIsRecorded() throws Exception {
        String client = flagClientWithoutConsent();

        mockMvc.perform(post("/api/user/me/training-consent").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingConsent").value(true));

        mockMvc.perform(get("/api/user/my-training/calendar?from=" + LocalDate.now()
                        + "&to=" + LocalDate.now().plusDays(7))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());
    }

    @Test
    void shouldServeTheBadgeCounterWithoutConsent() throws Exception {
        // The account tile polls this from a page the client sees before consenting; it carries
        // counters only, so gating it would just fill their console with errors.
        String client = flagClientWithoutConsent();

        mockMvc.perform(get("/api/user/my-training/summary").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepTheCoachPlanningForAClientWhoHasNotConsented() throws Exception {
        // The plan itself runs on the contract, not on consent — only the health data the client
        // alone can write waits for the tick. A coach blocked here could not prepare a first session.
        String admin = adminToken();
        flagClientWithoutConsent();
        UUID clientId = regularUserId();

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła"}""".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + LocalDate.now() + "&to=" + LocalDate.now().plusDays(7))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainings.length()").value(1));
    }

    @Test
    void shouldKeepTheFirstTimestampWhenConsentIsGivenTwice() throws Exception {
        String client = flagClientWithoutConsent();

        mockMvc.perform(post("/api/user/me/training-consent").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());
        var first = userRepository.findById(regularUserId()).orElseThrow().getTrainingConsentAt();

        mockMvc.perform(post("/api/user/me/training-consent").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());

        assertEquals(first, userRepository.findById(regularUserId()).orElseThrow().getTrainingConsentAt(),
                "re-accepting must not rewrite the proof-of-consent timestamp");
    }

    @Test
    void shouldRefuseConsentFromSomebodyWithoutTheClientFlag() throws Exception {
        // No calendar, nothing to consent to — storing one would leave a health-data declaration
        // on an account that has no health data.
        String plain = userToken();

        mockMvc.perform(post("/api/user/me/training-consent").header("Authorization", "Bearer " + plain))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDropConsentWhenTheClientFlagIsCleared() throws Exception {
        String admin = adminToken();
        String client = flagClientWithoutConsent();
        UUID clientId = regularUserId();
        mockMvc.perform(post("/api/user/me/training-consent").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        assertFalse(userRepository.findById(clientId).orElseThrow().hasTrainingConsent(),
                "ending the arrangement ends the consent that belonged to it");
    }
}
