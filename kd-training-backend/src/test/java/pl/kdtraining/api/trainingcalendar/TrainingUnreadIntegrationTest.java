package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Every source that can light a dot, tested one at a time.
 * <p>
 * The failure mode of this feature is silent: a forgotten source simply never notifies anyone, and
 * nobody finds out until a change goes unnoticed in real use. So each source gets its own test
 * rather than one happy path that would pass with half of them missing.
 */
class TrainingUnreadIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate NEXT_WEEK = LocalDate.now().plusDays(7);

    private String flagAthlete(String email) {
        String token = createUserAndGetToken(email, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        // The client side of the calendar sits behind the GDPR art. 9 consent gate (users.training_consent_at)
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail("client@kdtraining.test").orElseThrow().getId();
    }

    private String createAsCoach(String admin, UUID athleteId, LocalDate date, String title) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"%s"}""".formatted(date, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /**
     * Every date this file uses sits inside {@link #OPENED_THROUGH}, so "the calendar was opened"
     * means the whole fixture was on screen — which is what these tests are about. The window itself
     * is exercised in {@link TrainingUnreadRangeIntegrationTest}.
     */
    private static final LocalDate OPENED_THROUGH = LocalDate.now().plusMonths(1);

    private void coachOpensCalendar(String admin, UUID athleteId) throws Exception {
        mockMvc.perform(post("/api/admin/personal-trainings/mark-seen?athleteId=" + athleteId
                        + "&to=" + OPENED_THROUGH)
                .header("Authorization", "Bearer " + admin));
    }

    private void clientOpensCalendar(String client) throws Exception {
        mockMvc.perform(post("/api/user/my-training/mark-seen?to=" + OPENED_THROUGH)
                .header("Authorization", "Bearer " + client));
    }

    private long coachUnread(String admin, UUID athleteId) throws Exception {
        String json = mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].unreadCount")).longValue();
    }

    private long clientUnread(String client) throws Exception {
        String json = mockMvc.perform(get("/api/user/my-training/summary")
                        .header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.unreadCount")).longValue();
    }

    // --- Sources that notify the CLIENT ---------------------------------------------------------

    @Test
    void shouldNotifyClientWhenCoachPlansATraining() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        clientOpensCalendar(client);

        createAsCoach(admin, clientId(), TOMORROW, "Siła");

        org.junit.jupiter.api.Assertions.assertEquals(1, clientUnread(client));
    }

    @Test
    void shouldNotifyClientWhenCoachComments() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), TOMORROW, "Siła");
        clientOpensCalendar(client);

        mockMvc.perform(post("/api/admin/personal-trainings/" + id + "/comments")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Pamiętaj o rozgrzewce"}"""))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(1, clientUnread(client));
    }

    @Test
    void shouldNotifyClientWhenCoachDeletesAFutureTraining() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), NEXT_WEEK, "Sparing");
        clientOpensCalendar(client);

        mockMvc.perform(delete("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(1, clientUnread(client));
        // And the loss is spelled out — the grid alone shows nothing where the training used to be
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.deletions.length()").value(1))
                .andExpect(jsonPath("$.deletions[0].title").value("Sparing"));
    }

    // --- Sources that notify the COACH ----------------------------------------------------------

    @Test
    void shouldNotifyCoachWhenClientTicksOffAndWhenTheyUndoIt() throws Exception {
        // Undoing must notify too. Keying the counter on completedAt would miss it entirely, because
        // undoing clears that column — hence updatedAt plus the authorship flag.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), YESTERDAY, "Bieg");
        coachOpensCalendar(admin, clientId());

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":7}"""))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(admin, clientId()));

        coachOpensCalendar(admin, clientId());
        org.junit.jupiter.api.Assertions.assertEquals(0, coachUnread(admin, clientId()));

        mockMvc.perform(delete("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(admin, clientId()));
    }

    @Test
    void shouldNotifyCoachWhenClientAddsTheirOwnTraining() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        coachOpensCalendar(admin, clientId());

        mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Rower"}""".formatted(TOMORROW)))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(admin, clientId()));
    }

    @Test
    void shouldNotifyCoachWhenClientComments() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), TOMORROW, "Siła");
        coachOpensCalendar(admin, clientId());

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/comments")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Boli mnie kolano"}"""))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(admin, clientId()));
    }

    @Test
    void shouldNotifyCoachWhenClientDeletesAFutureTraining() throws Exception {
        // Their own entry — what the coach assigned cannot be deleted from this side at all
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String json = mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sparing"}""".formatted(NEXT_WEEK)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        coachOpensCalendar(admin, clientId());

        mockMvc.perform(delete("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(admin, clientId()));
    }

    // --- Rules that keep the counters honest -----------------------------------------------------

    @Test
    void shouldNotNotifyAnyoneAboutTheirOwnActions() throws Exception {
        // The dot means "the other side did something". Seeing your own work highlighted as news
        // would make the signal worthless.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        coachOpensCalendar(admin, clientId());
        clientOpensCalendar(client);

        createAsCoach(admin, clientId(), TOMORROW, "Siła");

        org.junit.jupiter.api.Assertions.assertEquals(0, coachUnread(admin, clientId()));
    }

    @Test
    void shouldNotAnnounceDeletionOfAPastTraining() throws Exception {
        // Clearing out old entries is housekeeping. Alerting on it would train people to ignore
        // the banner, and then a real loss would slip past.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), YESTERDAY, "Bieg");
        clientOpensCalendar(client);

        mockMvc.perform(delete("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.deletions.length()").value(0));
    }

    @Test
    void shouldKeepDismissingTheBannerSeparateFromOpeningTheCalendar() throws Exception {
        // Seeing the week is not the same as accepting that Tuesday is gone, so mark-seen must not
        // silently clear the deletion banner.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), NEXT_WEEK, "Sparing");
        mockMvc.perform(delete("/api/admin/personal-trainings/" + id)
                .header("Authorization", "Bearer " + admin));

        clientOpensCalendar(client);
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.deletions.length()").value(1));

        mockMvc.perform(post("/api/user/my-training/deletions/dismiss")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.deletions.length()").value(0));
    }

    @Test
    void shouldKeepCoachCountersIndependentPerAdmin() throws Exception {
        // Two coaches must not clear each other's dots — the marker is per (viewer, client) pair.
        String admin = adminToken();
        String otherAdmin = createUserAndGetToken("coach2@kdtraining.test", "Drugi", "Trener", UserRole.ADMIN);
        String client = flagAthlete("client@kdtraining.test");
        coachOpensCalendar(admin, clientId());
        coachOpensCalendar(otherAdmin, clientId());

        mockMvc.perform(post("/api/user/my-training/trainings")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"date":"%s","title":"Rower"}""".formatted(TOMORROW)));

        coachOpensCalendar(admin, clientId());

        org.junit.jupiter.api.Assertions.assertEquals(0, coachUnread(admin, clientId()));
        org.junit.jupiter.api.Assertions.assertEquals(1, coachUnread(otherAdmin, clientId()));
    }

    @Test
    void shouldFlagTheChangedTrainingItselfSoTheDotHasSomewhereToLand() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), TOMORROW, "Siła");
        coachOpensCalendar(admin, clientId());

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/comments")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"body":"Pytanie"}"""));

        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId()
                        + "&from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings[0].unread").value(true))
                .andExpect(jsonPath("$.trainings[0].commentCount").value(1));
    }

    @Test
    void shouldNotCostMoreQueriesAsTheRosterGrows() throws Exception {
        // The coach opens this page constantly. Counting per athlete would be 1 + 4N queries and
        // would get slower with every client they take on.
        String admin = adminToken();
        flagAthlete("client@kdtraining.test");
        // Warm up: the very first request also resolves and caches the admin account, which would
        // otherwise be counted against whichever measurement happened to run first.
        countRosterQueries(admin);
        long oneAthlete = countRosterQueries(admin);

        for (int i = 0; i < 5; i++) {
            flagAthlete("client" + i + "@kdtraining.test");
        }
        long sixAthletes = countRosterQueries(admin);

        org.junit.jupiter.api.Assertions.assertEquals(oneAthlete, sixAthletes,
                "roster cost must be flat, not per athlete");
    }

    private long countRosterQueries(String admin) throws Exception {
        var stats = webApplicationContext.getBean(jakarta.persistence.EntityManagerFactory.class)
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }

    @Test
    void shouldFreezeCommentAuthorshipAtWritingTime() throws Exception {
        // Deriving the author's role from users.role today would relabel a client's old comments the
        // day they are promoted to admin — and flip everyone's dots with them.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        String id = createAsCoach(admin, clientId(), TOMORROW, "Siła");
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/comments")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"body":"Napisane jako podopieczny"}"""));

        User promoted = userRepository.findByEmail("client@kdtraining.test").orElseThrow();
        promoted.setRole(UserRole.ADMIN);
        userRepository.save(promoted);

        mockMvc.perform(get("/api/admin/personal-trainings/" + id + "/comments")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].fromCoach").value(false));
    }
}
