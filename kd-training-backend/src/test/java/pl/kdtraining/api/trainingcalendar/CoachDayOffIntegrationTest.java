package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The coach marks days off once; every client sees them in their own calendar. */
class CoachDayOffIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);

    private String flagClient() {
        String token = createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private String addDayOff(String admin, LocalDate start, LocalDate end, String note) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"startDate":"%s","endDate":"%s","note":"%s"}""".formatted(start, end, note)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void shouldShowTheCoachsDayOffInTheClientsCalendar() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        addDayOff(admin, START, START.plusDays(6), "Urlop");

        mockMvc.perform(get("/api/user/my-training/calendar")
                        .param("from", START.minusDays(3).toString())
                        .param("to", START.plusDays(3).toString())
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysOff", hasSize(1)))
                .andExpect(jsonPath("$.daysOff[0].startDate").value(START.toString()))
                .andExpect(jsonPath("$.daysOff[0].endDate").value(START.plusDays(6).toString()))
                .andExpect(jsonPath("$.daysOff[0].note").value("Urlop"));
    }

    @Test
    void shouldLeaveOutDaysOffOutsideTheRange() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        addDayOff(admin, START, START.plusDays(2), "Urlop");

        mockMvc.perform(get("/api/user/my-training/calendar")
                        .param("from", START.plusDays(3).toString())
                        .param("to", START.plusDays(9).toString())
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysOff", hasSize(0)));
    }

    @Test
    void shouldListOnlyCurrentAndUpcomingDaysOffForTheCoach() throws Exception {
        String admin = adminToken();
        addDayOff(admin, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5), "Było");
        addDayOff(admin, START, START, "Kurs");

        mockMvc.perform(get("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note").value("Kurs"));
    }

    @Test
    void shouldDeleteADayOff() throws Exception {
        String admin = adminToken();
        String id = addDayOff(admin, START, START, "Kurs");

        mockMvc.perform(delete("/api/admin/personal-trainings/days-off/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldRefuseAnEndBeforeTheStart() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"startDate":"%s","endDate":"%s"}""".formatted(START, START.minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefuseADayOffLongerThanAQuarter() throws Exception {
        // A mistyped year must not black out the calendar for every client.
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"startDate":"%s","endDate":"%s"}""".formatted(START, START.plusYears(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotLetAClientMarkADayOff() throws Exception {
        String client = flagClient();

        mockMvc.perform(post("/api/admin/personal-trainings/days-off")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content("""
                            {"startDate":"%s","endDate":"%s"}""".formatted(START, START)))
                .andExpect(status().isForbidden());
    }
}
