package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonalTrainingFlowIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private String flagAthlete(String email, String firstName) {
        String token = createUserAndGetToken(email, firstName, "Testowy", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        // The client side of the calendar sits behind the GDPR art. 9 consent gate (users.training_consent_at)
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private String createAsCoach(String admin, UUID athleteId, LocalDate date, String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String createAsClient(String client, String body) throws Exception {
        String json = mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private static String trainingBody(LocalDate date, String title) {
        return """
            {"date":"%s","title":"%s"}""".formatted(date, title);
    }

    private static String taskBody(LocalDate date, String title, Integer calories) {
        return """
            {"kind":"TASK","date":"%s","title":"%s","targetCalories":%d}"""
                .formatted(date, title, calories);
    }

    @Test
    void shouldLetCoachPlanAnUntimedTrainingAndClientSeeIt() throws Exception {
        // Given: a flagged client
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");

        // When: the coach plans a training with no hour — the default case
        createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        // Then: the client sees it, with no time and authored by the coach
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainings.length()").value(1))
                .andExpect(jsonPath("$.trainings[0].title").value("Siła"))
                .andExpect(jsonPath("$.trainings[0].startTime").doesNotExist())
                .andExpect(jsonPath("$.trainings[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.trainings[0].createdByAdmin").value(true));
    }

    @Test
    void shouldLetClientCompleteAPastTrainingWithRpe() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":7,"feedback":"dobrze"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rpe").value(7))
                // Authorship flips to the client, otherwise they would light their own unread dot
                .andExpect(jsonPath("$.lastModifiedByAdmin").value(false));
    }

    @Test
    void shouldLetClientTickOffATaskWithoutAnEffortRating() throws Exception {
        // Given: a task the coach set for yesterday — "stay under 2200 kcal"
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, taskBody(YESTERDAY, "Limit kalorii", 2200));

        // When: the client ticks it off, saying nothing about effort
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"feedback":"zmieściłam się"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TASK"))
                .andExpect(jsonPath("$.targetCalories").value(2200))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rpe").doesNotExist())
                // Same as a training: authorship flips, so the client does not light their own dot
                .andExpect(jsonPath("$.lastModifiedByAdmin").value(false));
    }

    @Test
    void shouldRefuseAnEffortRatingOnATaskAndDemandOneOnATraining() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String task = createAsCoach(admin, clientId, YESTERDAY, taskBody(YESTERDAY, "Limit kalorii", 2200));
        String training = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));

        // Rating a task would poison the RPE averages the coach reads training load from
        mockMvc.perform(post("/api/user/my-training/trainings/" + task + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":5}"""))
                .andExpect(status().isBadRequest());

        // And a session ticked off with no effort rating tells the coach nothing
        mockMvc.perform(post("/api/user/my-training/trainings/" + training + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldTickOffATaskAndTheDaysTrainingIndependently() throws Exception {
        // Given: a session and a calorie ceiling on the same day — the client can nail one and blow
        // the other, which is the entire reason they are two entries
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String training = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));
        createAsCoach(admin, clientId, YESTERDAY, taskBody(YESTERDAY, "Limit kalorii", 2200));

        // When: only the training is ticked off
        mockMvc.perform(post("/api/user/my-training/trainings/" + training + "/complete")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"rpe":7}"""));

        // Then: the day reports both truths
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + YESTERDAY)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.trainings.length()").value(2))
                .andExpect(jsonPath("$.trainings[?(@.kind=='TRAINING')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.trainings[?(@.kind=='TASK')].status").value("MISSED"));

        // And the training statistics stay about training: the task is counted on its own, or a held
        // diet would quietly inflate the streak, the attendance and the monthly total
        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                // Every task count carries the denominator it belongs to — a bare 0 cannot tell a
                // blown ceiling from a month where none was set. (Only the 90-day window is asserted
                // here: YESTERDAY falls in last month on the 1st, and the month counts with it.)
                .andExpect(jsonPath("$.tasks.windowDone").value(0))
                .andExpect(jsonPath("$.tasks.windowDue").value(1))
                .andExpect(jsonPath("$.tasks.completionPercent").value(0))
                .andExpect(jsonPath("$.attendancePercent").value(100));
    }

    @Test
    void shouldNotCountATaskAsDueBeforeItsDay() throws Exception {
        // Given: one task behind the client and one still ahead of them
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        createAsCoach(admin, clientId, YESTERDAY, taskBody(YESTERDAY, "Limit kalorii", 2200));
        createAsCoach(admin, clientId, TOMORROW, taskBody(TOMORROW, "Limit kalorii", 2200));

        // Then: only the one that has come due is on the denominator. Counting tomorrow's would
        // report a failure the client has not had the chance to make.
        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.windowDue").value(1))
                .andExpect(jsonPath("$.tasks.windowDone").value(0));
    }

    @Test
    void shouldNotLetAnEntryChangeWhatItIs() throws Exception {
        // Given: a task
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, taskBody(TOMORROW, "Limit kalorii", 2200));

        // When: an update tries to smuggle a different kind in
        // Then: it is ignored — a completed training turning into a task would have to lose its RPE
        mockMvc.perform(put("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"kind":"TRAINING","date":"%s","title":"Limit kalorii","targetCalories":1800,"version":0}"""
                                .formatted(TOMORROW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TASK"))
                .andExpect(jsonPath("$.targetCalories").value(1800));
    }

    @Test
    void shouldRejectCompletingATrainingThatHasNotStarted() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":5}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldClearRpeWhenUncompleting() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"rpe":7}"""));

        mockMvc.perform(delete("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSED"))
                .andExpect(jsonPath("$.rpe").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void shouldLetClientAddAndEditTheirOwnTraining() throws Exception {
        // Given: the client logs activities of their own, not just what the coach plans
        String client = flagAthlete("client@kdtraining.test", "Ala");

        String json = mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content(trainingBody(TOMORROW, "Rower")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByAdmin").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        int version = JsonPath.read(json, "$.version");

        mockMvc.perform(put("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Rower 40 km","version":%d}""".formatted(TOMORROW, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rower 40 km"));
    }

    @Test
    void shouldRefuseClientReshapingWhatTheCoachAssigned() throws Exception {
        // Given: a session the coach put in the plan
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        // When: the client tries to rewrite it, copy it or clear it away
        // Then: every route is refused — the prescription is the coaching, and a plan the client can
        // quietly rewrite stops being one. Deleting is in the list because "delete and add my own
        // version" is otherwise a way straight around the edit refusal.
        mockMvc.perform(put("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Coś lżejszego","version":0}""".formatted(TOMORROW)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/user/my-training/trainings/paste")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY"}"""
                                .formatted(id, LocalDate.now().plusDays(3))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());

        // And: it is still standing, untouched
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.trainings.length()").value(1))
                .andExpect(jsonPath("$.trainings[0].title").value("Siła"));
    }

    @Test
    void shouldStillLetClientTickOffAndCommentOnTheCoachsTraining() throws Exception {
        // Read-only covers reshaping the entry, never doing it: the two acts the plan exists for stay
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/comments")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Boli mnie kolano"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":6}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(delete("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSED"));
    }

    @Test
    void shouldLetClientCopyAndDeleteWhatTheyAddedThemselves() throws Exception {
        // The other half of the rule: their own entries stay fully theirs
        String client = flagAthlete("client@kdtraining.test", "Ala");
        String id = createAsClient(client, trainingBody(TOMORROW, "Rower"));

        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByAdmin").value(false));
        mockMvc.perform(post("/api/user/my-training/trainings/paste")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"MOVE"}"""
                                .formatted(id, LocalDate.now().plusDays(2))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldLetCoachReshapeWhatTheClientAdded() throws Exception {
        // The rule is one-way. The coach reads and edits the whole plan, entries the client logged
        // included — otherwise correcting someone's own note would mean asking them to do it.
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        String id = createAsClient(client, trainingBody(TOMORROW, "Rower"));

        mockMvc.perform(put("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Rower 40 km","version":0}""".formatted(TOMORROW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rower 40 km"));
        mockMvc.perform(delete("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldHideAnotherClientsTrainingBehindTheSameNotFoundAsAMissingOne() throws Exception {
        // Given: two clients, one training belonging to the first
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        String intruder = flagAthlete("other@kdtraining.test", "Ola");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        // When: the other client asks for it, and for an id that does not exist at all
        String foreign = mockMvc.perform(delete("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(delete("/api/user/my-training/trainings/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Then: the two answers are indistinguishable — anything else would confirm the id is real
        String missingMessage = JsonPath.read(missing, "$.message");
        String foreignMessage = JsonPath.read(foreign, "$.message");
        assertEquals(missingMessage, foreignMessage);
    }

    @Test
    void shouldRejectASecondUpdateCarryingAStaleVersion() throws Exception {
        // Given: an entry the client logged themselves — the coach reaches those too, so this is the
        // row both sides can be editing at once (what the coach assigned is read-only to the client)
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        String id = createAsClient(client, trainingBody(TOMORROW, "Rower"));
        String body = """
            {"date":"%s","title":"Zmienione","version":0}""".formatted(TOMORROW);

        // When: both send the version they loaded
        mockMvc.perform(put("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Then: the second one is told to refresh instead of silently overwriting the first
        mockMvc.perform(put("/api/user/my-training/trainings/" + id)
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRefuseToMoveACompletedTrainingIntoTheFuture() throws Exception {
        // Given: a training the client already ticked off
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));
        String completed = mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":7}"""))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(completed, "$.version");

        // When: the coach drags it into next week
        // Then: refused — a session cannot be both done and yet to come
        mockMvc.perform(put("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Bieg","version":%d}"""
                                .formatted(LocalDate.now().plusDays(7), version)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectAnEndTimeWithoutAStart() throws Exception {
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","endTime":"18:30","title":"Siła"}""".formatted(TOMORROW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectImpossibleAndOversizedRanges() throws Exception {
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String base = "/api/admin/personal-trainings?athleteId=" + clientId;

        mockMvc.perform(get(base + "&from=" + TOMORROW + "&to=" + YESTERDAY)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        // 62 days is the ceiling — a month grid is 42, a week is 7
        mockMvc.perform(get(base + "&from=" + YESTERDAY + "&to=" + YESTERDAY.plusDays(62))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "&from=" + YESTERDAY + "&to=" + YESTERDAY.plusDays(61))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHideTheFeatureFromAccountsWithoutTheFlag() throws Exception {
        // Given: an ordinary account — 404, not 403, so it cannot tell the feature exists
        String plain = userToken();

        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + plain))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + plain)
                        .contentType(APPLICATION_JSON)
                        .content(trainingBody(TOMORROW, "Rower")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDuplicateOneWeekAheadWithoutCarryingCompletion() throws Exception {
        // Given: a training the client already ticked off
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, YESTERDAY, trainingBody(YESTERDAY, "Bieg"));
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"rpe":7}"""));

        // When: the coach repeats it
        mockMvc.perform(post("/api/admin/personal-trainings/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(YESTERDAY.plusDays(7).toString()))
                // A copy is a fresh plan, never a fresh achievement
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.rpe").doesNotExist());
    }

    @Test
    void shouldRefuseAnOffsetThatRunsOffTheCalendar() throws Exception {
        // The offset is handed to LocalDate.plusDays, which throws once the result leaves the
        // supported range — unbounded, a typed digit turns into a 500 rather than a rejected form.
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        mockMvc.perform(post("/api/admin/personal-trainings/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"offsetDays":2000000000}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepTheOriginalOnCopyAndReuseItOnMove() throws Exception {
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));
        LocalDate target = LocalDate.now().plusDays(3);

        // COPY leaves the source where it was
        String copied = mockMvc.perform(post("/api/admin/personal-trainings/paste")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY"}""".formatted(id, target)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(id, JsonPath.read(copied, "$.id"));

        // MOVE re-dates the original, so its id — and with it completion and comments — survives
        String moved = mockMvc.perform(post("/api/admin/personal-trainings/paste")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"MOVE"}""".formatted(id, target)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(id, JsonPath.read(moved, "$.id"));
        assertEquals(target.toString(), JsonPath.read(moved, "$.date"));
    }

    @Test
    void shouldCopyIntoTheNamedClientsCalendarRatherThanTheSourcesOwn() throws Exception {
        // Given: two clients, a session planned for the first
        String admin = adminToken();
        flagAthlete("ala@kdtraining.test", "Ala");
        flagAthlete("basia@kdtraining.test", "Basia");
        UUID alaId = idOf("ala@kdtraining.test");
        UUID basiaId = idOf("basia@kdtraining.test");
        String id = createAsCoach(admin, alaId, TOMORROW, trainingBody(TOMORROW, "Siła"));
        LocalDate target = LocalDate.now().plusDays(3);

        // When: the coach copies it while looking at the second client's calendar
        mockMvc.perform(post("/api/admin/personal-trainings/paste")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY","targetAthleteId":"%s"}"""
                                .formatted(id, target, basiaId)))
                .andExpect(status().isOk());

        // Then: it lands there — and NOT back in the source's plan, which is what the missing target
        // id used to cause: every paste after switching client went to the previous one, unseen
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + basiaId
                        + "&from=" + target + "&to=" + target)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings.length()").value(1))
                .andExpect(jsonPath("$.trainings[0].title").value("Siła"));
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + alaId
                        + "&from=" + target + "&to=" + target)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings.length()").value(0));
        // the original stays put
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + alaId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings.length()").value(1));
    }

    @Test
    void shouldMoveAcrossClientsAsAFreshEntryLeavingNothingBehind() throws Exception {
        // Given: a session the first client already commented on
        String admin = adminToken();
        String ala = flagAthlete("ala@kdtraining.test", "Ala");
        flagAthlete("basia@kdtraining.test", "Basia");
        UUID alaId = idOf("ala@kdtraining.test");
        UUID basiaId = idOf("basia@kdtraining.test");
        String id = createAsCoach(admin, alaId, TOMORROW, trainingBody(TOMORROW, "Siła"));
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/comments")
                        .header("Authorization", "Bearer " + ala)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"body":"Boli mnie bark"}"""))
                .andExpect(status().isCreated());
        LocalDate target = LocalDate.now().plusDays(3);

        // When: the coach cuts it and pastes into the second client's calendar
        String pasted = mockMvc.perform(post("/api/admin/personal-trainings/paste")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"MOVE","targetAthleteId":"%s"}"""
                                .formatted(id, target, basiaId)))
                .andExpect(status().isOk())
                // A new row, not the original re-pointed: the comment thread is one person's health
                // data and must not resurface under another name
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(id, JsonPath.read(pasted, "$.id"));

        // Then: it is gone from the source's plan, and they are told — a session vanishing from a
        // client's week is news whether it was deleted or handed to someone else
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + alaId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings.length()").value(0));
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + ala))
                .andExpect(jsonPath("$.trainings.length()").value(0))
                .andExpect(jsonPath("$.deletions.length()").value(1));
    }

    @Test
    void shouldRefuseAClientPastingIntoSomeoneElsesCalendar() throws Exception {
        String admin = adminToken();
        String ala = flagAthlete("ala@kdtraining.test", "Ala");
        flagAthlete("basia@kdtraining.test", "Basia");
        UUID alaId = idOf("ala@kdtraining.test");
        UUID basiaId = idOf("basia@kdtraining.test");
        String id = createAsCoach(admin, alaId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        // Naming another client is the coach's privilege; from here it is a 404, same as any other
        // stranger's calendar, so the roster cannot be probed
        mockMvc.perform(post("/api/user/my-training/trainings/paste")
                        .header("Authorization", "Bearer " + ala)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY","targetAthleteId":"%s"}"""
                                .formatted(id, LocalDate.now().plusDays(3), basiaId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + basiaId
                        + "&from=" + YESTERDAY + "&to=" + LocalDate.now().plusDays(5))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings.length()").value(0));
    }

    @Test
    void shouldOrderUntimedTrainingsBeforeTimedOnesWithinADay() throws Exception {
        // Given: one of each on the same day, created timed-first so insertion order cannot explain it
        String admin = adminToken();
        flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"date":"%s","startTime":"17:00","title":"Sparing"}""".formatted(TOMORROW)));
        createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Rozciąganie"));

        // Then: the untimed one leads — it is the default case, and the card list has no clock axis
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings[0].title").value("Rozciąganie"))
                .andExpect(jsonPath("$.trainings[1].title").value("Sparing"));
    }

    @Test
    void shouldLockTheCalendarWhenTheFlagIsCleared() throws Exception {
        // Given: a client with a plan
        String admin = adminToken();
        String client = flagAthlete("client@kdtraining.test", "Ala");
        UUID clientId = idOf("client@kdtraining.test");
        String id = createAsCoach(admin, clientId, TOMORROW, trainingBody(TOMORROW, "Siła"));

        // When: the coach takes them off the roster
        mockMvc.perform(delete("/api/admin/users/" + clientId + "/athlete")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // Then: the calendar is unreachable from both sides — but nothing was deleted, so re-flagging
        // brings the whole plan back
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/personal-trainings/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin));
        // Re-flagging restores the rows but not the consent — clearing the flag ended the
        // arrangement the client agreed to, so the calendar asks again before opening (users.training_consent_at).
        mockMvc.perform(post("/api/user/me/training-consent")
                .header("Authorization", "Bearer " + client)).andExpect(status().isOk());
        mockMvc.perform(get("/api/user/my-training/calendar?from=" + YESTERDAY + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainings.length()").value(1));
    }
}
