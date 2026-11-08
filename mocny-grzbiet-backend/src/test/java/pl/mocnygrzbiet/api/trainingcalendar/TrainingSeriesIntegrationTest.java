package pl.mocnygrzbiet.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.mocnygrzbiet.BaseIntegrationTest;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrainingSeriesIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate START = LocalDate.now().plusDays(1);

    @Autowired private JdbcTemplate jdbc;

    private String flagClient() {
        String token = createUserAndGetToken("client@mocnygrzbiet.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@mocnygrzbiet.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail("client@mocnygrzbiet.test").orElseThrow().getId();
    }

    private String createSeries(String admin, LocalDate until) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","startTime":"18:00","title":"Siła","repeatWeeklyUntil":"%s"}"""
                            .formatted(START, until)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private List<LocalDate> datesInPlan() {
        return jdbc.queryForList("SELECT training_date FROM personal_trainings WHERE athlete_id = ? ORDER BY training_date",
                LocalDate.class, clientId());
    }

    private UUID idOn(LocalDate date) {
        return jdbc.queryForObject("SELECT id FROM personal_trainings WHERE athlete_id = ? AND training_date = ?",
                UUID.class, clientId(), date);
    }

    @Test
    void shouldPlanOneEntryPerWeekUpToAndIncludingTheEndDate() throws Exception {
        String admin = adminToken();
        flagClient();

        createSeries(admin, START.plusWeeks(3));

        assertEquals(List.of(START, START.plusWeeks(1), START.plusWeeks(2), START.plusWeeks(3)), datesInPlan());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(DISTINCT series_id) FROM personal_trainings WHERE series_id IS NOT NULL", Long.class));
    }

    @Test
    void shouldDeleteThisAndTheFollowingOccurrencesButKeepTheEarlierOnes() throws Exception {
        String admin = adminToken();
        flagClient();
        createSeries(admin, START.plusWeeks(3));

        mockMvc.perform(delete("/api/admin/personal-trainings/" + idOn(START.plusWeeks(2)) + "?following=true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertEquals(List.of(START, START.plusWeeks(1)), datesInPlan());
    }

    @Test
    void shouldDeleteOnlyOneOccurrenceByDefault() throws Exception {
        String admin = adminToken();
        flagClient();
        createSeries(admin, START.plusWeeks(2));

        mockMvc.perform(delete("/api/admin/personal-trainings/" + idOn(START.plusWeeks(1)))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertEquals(List.of(START, START.plusWeeks(2)), datesInPlan());
    }

    @Test
    void shouldKeepADuplicateOutOfTheSeries() throws Exception {
        // A copy is a new entry; "delete the following ones" must not take it along.
        String admin = adminToken();
        flagClient();
        String first = createSeries(admin, START.plusWeeks(1));

        mockMvc.perform(post("/api/admin/personal-trainings/" + first + "/duplicate")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"offsetDays":21}"""))
                .andExpect(status().isCreated());

        assertNull(jdbc.queryForObject("SELECT series_id FROM personal_trainings WHERE training_date = ?",
                UUID.class, START.plusWeeks(3)));
        mockMvc.perform(delete("/api/admin/personal-trainings/" + first + "?following=true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(List.of(START.plusWeeks(3)), datesInPlan());
    }

    @Test
    void shouldRefuseASeriesLongerThanHalfAYear() throws Exception {
        // Every occurrence is written at once; a mistyped year must not fill the calendar.
        String admin = adminToken();
        flagClient();

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","title":"Siła","repeatWeeklyUntil":"%s"}"""
                            .formatted(START, START.plusYears(1))))
                .andExpect(status().isBadRequest());

        assertEquals(List.of(), datesInPlan());
    }

    @Test
    void shouldRefuseAnEndDateNotAfterTheStart() throws Exception {
        String admin = adminToken();
        flagClient();

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","title":"Siła","repeatWeeklyUntil":"%s"}""".formatted(START, START)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotLetTheClientCreateASeries() throws Exception {
        String client = flagClient();

        mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","title":"Bieg","repeatWeeklyUntil":"%s"}"""
                            .formatted(START, START.plusWeeks(2))))
                .andExpect(status().isBadRequest());

        assertEquals(List.of(), datesInPlan());
    }

    @Test
    void shouldKeepOccurrencesTheClientAlreadyTickedOff() throws Exception {
        // "This and the following ones" clicked on a past occurrence must not wipe the sessions the
        // client completed after it: their effort ratings and comments are the client's record.
        String admin = adminToken();
        String client = flagClient();
        LocalDate start = LocalDate.now().minusWeeks(3);
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","title":"Siła","repeatWeeklyUntil":"%s"}"""
                            .formatted(start, start.plusWeeks(5))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/user/my-training/trainings/" + idOn(start.plusWeeks(1)) + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content("""
                            {"rpe":7}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/personal-trainings/" + idOn(start) + "?following=true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertEquals(List.of(start.plusWeeks(1)), datesInPlan());
    }
}
