package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The badge and the dots have to be able to agree.
 * <p>
 * They are two readings of one thing — "the other side did something" — but they were scoped
 * differently: the dots only ever consider the page on screen, while the number considers the whole
 * plan. Opening the calendar then stamped "seen" over everything, so a month the client never looked
 * at was cleared from the badge without a single dot ever appearing on it. What the coach wrote was
 * announced and then quietly swallowed.
 * <p>
 * The rule pinned here: being caught up runs to the last day you actually reached.
 */
class TrainingUnreadRangeIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate WEEK_END = TODAY.plusDays(6);
    /** Far enough out that no sane calendar page shows it alongside today. */
    private static final LocalDate FAR = TODAY.plusMonths(3);
    /** The page that holds FAR — stated as a window rather than a month, so a training on the 31st
     *  is inside it just like one on the 1st. */
    private static final LocalDate FAR_FROM = FAR.minusDays(3);
    private static final LocalDate FAR_TO = FAR.plusDays(3);

    private String flagAthlete(String email) {
        String token = createUserAndGetToken(email, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail("client@kdtraining.test").orElseThrow().getId();
    }

    private void createAsCoach(String admin, UUID athleteId, LocalDate date, String title) throws Exception {
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"%s"}""".formatted(date, title)))
                .andExpect(status().isCreated());
    }

    private void clientOpens(String client, LocalDate from, LocalDate to) throws Exception {
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + from + "&to=" + to)
                .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/user/my-training/mark-seen?from=" + from + "&to=" + to)
                .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());
    }

    private long clientUnread(String client) throws Exception {
        String json = mockMvc.perform(get("/api/user/my-training/summary")
                        .header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.unreadCount")).longValue();
    }

    /** How many cards on that page carry a dot. */
    private int clientDots(String client, LocalDate from, LocalDate to) throws Exception {
        String json = mockMvc.perform(get("/api/user/my-training/calendar?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Boolean> unread = JsonPath.read(json, "$.trainings[*].unread");
        return (int) unread.stream().filter(Boolean::booleanValue).count();
    }

    @Test
    void shouldKeepAChangeOutsideTheViewedWindowUnreadUntilItIsReached() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        clientOpens(client, TODAY, WEEK_END);

        createAsCoach(admin, clientId(), FAR, "Blok siłowy");

        // Looking at this week says nothing about a training three months out.
        clientOpens(client, TODAY, WEEK_END);
        assertEquals(1, clientUnread(client),
                "Opening this week must not clear a change the client could not have seen");

        // And when they get there, it is marked.
        assertEquals(1, clientDots(client, FAR_FROM, FAR_TO));
    }

    @Test
    void shouldClearOnceTheWindowHoldingTheChangeHasBeenOpened() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        clientOpens(client, TODAY, WEEK_END);

        createAsCoach(admin, clientId(), FAR, "Blok siłowy");
        clientOpens(client, FAR_FROM, FAR_TO);

        assertEquals(0, clientUnread(client));
        assertEquals(0, clientDots(client, FAR_FROM, FAR_TO));
    }

    @Test
    void shouldNeverMoveSeenThroughBackwards() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");

        createAsCoach(admin, clientId(), FAR, "Blok siłowy");
        clientOpens(client, FAR_FROM, FAR_TO);
        assertEquals(0, clientUnread(client));

        // Paging back to this week must not re-open everything that was already read.
        clientOpens(client, TODAY, WEEK_END);
        assertEquals(0, clientUnread(client));
    }

    /** The badge counts what the calendar can show; anything else is a number nobody can act on. */
    @Test
    void shouldAgreeBetweenTheBadgeAndTheDotsOnThePageThatHoldsTheChange() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        clientOpens(client, TODAY, WEEK_END);

        createAsCoach(admin, clientId(), FAR, "Blok siłowy");
        createAsCoach(admin, clientId(), FAR.plusDays(1), "Technika");
        clientOpens(client, TODAY, WEEK_END);

        long badge = clientUnread(client);
        // Asserted before the comparison: with both at zero the equality below holds while the
        // feature is entirely broken, which is the same as not testing it.
        assertEquals(2, badge, "Two trainings the client has not seen are two pieces of news");
        assertEquals(badge, clientDots(client, FAR_FROM, FAR_TO),
                "The number promises something findable; the dots are where it is found");
    }

    /**
     * A new goal was the one source the badge counted with nowhere on the page to point at: the
     * number went up and every card looked identical. It is marked for the client and absent for the
     * coach — a goal they wrote is not news to them, same shape as the overtraining signal.
     */
    @Test
    void shouldMarkANewGoalAsUnreadForTheClientOnly() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test");
        clientOpens(client, TODAY, WEEK_END);

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Podciagniecie x10"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[0].unread").value(true));

        mockMvc.perform(get("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[0].unread").doesNotExist());

        // Opening the plan is what clears it — the goals board lives on that same screen.
        clientOpens(client, TODAY, WEEK_END);
        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active[0].unread").value(false));
    }
}
