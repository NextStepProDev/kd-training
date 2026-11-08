package pl.mocnygrzbiet.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mocnygrzbiet.BaseIntegrationTest;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRole;
import pl.mocnygrzbiet.infrastructure.mail.TrainingMailService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trainings with several clients (the client's answer: pairs, who see each other's names). One row
 * per person, tied by a group id: the coach plans, moves and cancels the session once, and what each
 * person writes or ticks off stays theirs.
 */
class GroupTrainingIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);

    @MockitoBean private TrainingMailService trainingMail;
    @Autowired private JdbcTemplate jdbc;

    private String flagClient(String email, String firstName) {
        String token = createUserAndGetToken(email, firstName, "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private String groupBody(LocalDate date, String title, UUID... partners) {
        String ids = String.join(",", java.util.Arrays.stream(partners).map(p -> "\"" + p + "\"").toList());
        return """
            {"date":"%s","startTime":"18:00","title":"%s","partnerIds":[%s]}""".formatted(date, title, ids);
    }

    private String coachCreates(String admin, UUID athleteId, String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** The client's own view of their calendar around the given days. */
    private String calendarOf(String token, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/user/my-training/calendar?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String calendarOf(String token) throws Exception {
        return calendarOf(token, YESTERDAY, TOMORROW.plusDays(14));
    }

    private int rows(UUID athleteId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM personal_trainings WHERE athlete_id = ?",
                Integer.class, athleteId);
        return n == null ? 0 : n;
    }

    @Test
    void shouldPutTheTrainingInEveryParticipantsCalendarWithThePartnersNames() throws Exception {
        String admin = adminToken();
        String ania = flagClient("ania@mocnygrzbiet.test", "Ania");
        String kasia = flagClient("kasia@mocnygrzbiet.test", "Kasia");
        String ola = flagClient("ola@mocnygrzbiet.test", "Ola");

        coachCreates(admin, idOf("ania@mocnygrzbiet.test"), groupBody(TOMORROW, "Siła w parze",
                idOf("kasia@mocnygrzbiet.test"), idOf("ola@mocnygrzbiet.test")));

        String forAnia = calendarOf(ania);
        String forKasia = calendarOf(kasia);
        String forOla = calendarOf(ola);
        assertEquals(List.of("Kasia", "Ola"), JsonPath.read(forAnia, "$.trainings[0].partners"));
        assertEquals(List.of("Ania", "Ola"), JsonPath.read(forKasia, "$.trainings[0].partners"));
        assertEquals(List.of("Ania", "Kasia"), JsonPath.read(forOla, "$.trainings[0].partners"));
        assertEquals("Siła w parze", JsonPath.read(forKasia, "$.trainings[0].title"));
        String group = JsonPath.read(forAnia, "$.trainings[0].groupId");
        assertEquals(group, JsonPath.read(forOla, "$.trainings[0].groupId"));
        // Three rows, three ids: each person's entry is their own.
        assertNotEquals(JsonPath.<String>read(forAnia, "$.trainings[0].id"), JsonPath.read(forKasia, "$.trainings[0].id"));
    }

    @Test
    void shouldKeepWhatEachPersonWritesAndRatesToThemselves() throws Exception {
        String admin = adminToken();
        String ania = flagClient("ania@mocnygrzbiet.test", "Ania");
        String kasia = flagClient("kasia@mocnygrzbiet.test", "Kasia");
        String aniasRow = coachCreates(admin, idOf("ania@mocnygrzbiet.test"),
                groupBody(YESTERDAY, "Siła", idOf("kasia@mocnygrzbiet.test")));

        mockMvc.perform(post("/api/user/my-training/trainings/" + aniasRow + "/complete")
                        .header("Authorization", "Bearer " + ania)
                        .contentType(APPLICATION_JSON).content("""
                            {"rpe":9,"feedback":"Bolało kolano"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/user/my-training/trainings/" + aniasRow + "/comments")
                        .header("Authorization", "Bearer " + ania)
                        .contentType(APPLICATION_JSON).content("""
                            {"body":"Kolano po urazie"}"""))
                .andExpect(status().isCreated());

        String forKasia = calendarOf(kasia);
        assertEquals("MISSED", JsonPath.read(forKasia, "$.trainings[0].status"));
        assertNull(JsonPath.read(forKasia, "$.trainings[0].rpe"));
        assertNull(JsonPath.read(forKasia, "$.trainings[0].feedback"));
        assertEquals(0, (int) JsonPath.read(forKasia, "$.trainings[0].commentCount"));
        // And Kasia cannot reach Ania's row by its id either.
        mockMvc.perform(get("/api/user/my-training/trainings/" + aniasRow + "/comments")
                        .header("Authorization", "Bearer " + kasia))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRefuseAGroupFromAClientForATaskOrWithABadPartnerList() throws Exception {
        String admin = adminToken();
        String ania = flagClient("ania@mocnygrzbiet.test", "Ania");
        flagClient("kasia@mocnygrzbiet.test", "Kasia");
        UUID aniaId = idOf("ania@mocnygrzbiet.test");
        UUID kasiaId = idOf("kasia@mocnygrzbiet.test");
        createUserAndGetToken("piotr@mocnygrzbiet.test", "Piotr", "Testowy", UserRole.USER);

        mockMvc.perform(post("/api/user/my-training/trainings").header("Authorization", "Bearer " + ania)
                        .contentType(APPLICATION_JSON).content(groupBody(TOMORROW, "Razem", kasiaId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + aniaId)
                        .header("Authorization", "Bearer " + admin).contentType(APPLICATION_JSON)
                        .content("""
                            {"kind":"TASK","date":"%s","title":"Kalorie","partnerIds":["%s"]}"""
                                .formatted(TOMORROW, kasiaId)))
                .andExpect(status().isBadRequest());
        for (String body : List.of(groupBody(TOMORROW, "Sama ze sobą", aniaId),
                groupBody(TOMORROW, "Dwa razy Kasia", kasiaId, kasiaId))) {
            mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + aniaId)
                            .header("Authorization", "Bearer " + admin).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        // Someone who is not a client has no calendar to put it in.
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + aniaId)
                        .header("Authorization", "Bearer " + admin).contentType(APPLICATION_JSON)
                        .content(groupBody(TOMORROW, "Z Piotrem", idOf("piotr@mocnygrzbiet.test"))))
                .andExpect(status().isNotFound());

        assertEquals(0, rows(aniaId), "a refused group leaves nothing behind");
        assertEquals(0, rows(kasiaId));
    }

    @Test
    void shouldCarryTheCoachsEditToEveryParticipant() throws Exception {
        String admin = adminToken();
        flagClient("ania@mocnygrzbiet.test", "Ania");
        String kasia = flagClient("kasia@mocnygrzbiet.test", "Kasia");
        String aniasRow = coachCreates(admin, idOf("ania@mocnygrzbiet.test"),
                groupBody(TOMORROW, "Siła", idOf("kasia@mocnygrzbiet.test")));

        mockMvc.perform(put("/api/admin/personal-trainings/" + aniasRow)
                        .header("Authorization", "Bearer " + admin).contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","startTime":"19:30","title":"Siła i oddech","description":"Bez skakania","version":0}"""
                                .formatted(TOMORROW.plusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partners[0]").value("Kasia"));

        String forKasia = calendarOf(kasia);
        assertEquals(TOMORROW.plusDays(2).toString(), JsonPath.read(forKasia, "$.trainings[0].date"));
        assertEquals("19:30:00", JsonPath.read(forKasia, "$.trainings[0].startTime"));
        assertEquals("Siła i oddech", JsonPath.read(forKasia, "$.trainings[0].title"));
        assertEquals("Bez skakania", JsonPath.read(forKasia, "$.trainings[0].description"));
        // The partner's calendar gets its dot: the coach changed something there.
        assertEquals(true, JsonPath.read(forKasia, "$.trainings[0].lastModifiedByAdmin"));
    }

    @Test
    void shouldCancelTheSessionForEveryoneOrTakeOnePersonOff() throws Exception {
        String admin = adminToken();
        String ania = flagClient("ania@mocnygrzbiet.test", "Ania");
        flagClient("kasia@mocnygrzbiet.test", "Kasia");
        UUID aniaId = idOf("ania@mocnygrzbiet.test");
        UUID kasiaId = idOf("kasia@mocnygrzbiet.test");

        // "Kasia cannot come": only her row goes, Ania still trains — and alone now.
        String first = coachCreates(admin, aniaId, groupBody(TOMORROW, "Siła", kasiaId));
        String kasiasRow = jdbc.queryForObject("SELECT id::text FROM personal_trainings WHERE athlete_id = ?",
                String.class, kasiaId);
        mockMvc.perform(delete("/api/admin/personal-trainings/" + kasiasRow + "?onlyThis=true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(1, rows(aniaId));
        assertEquals(0, rows(kasiaId));
        assertEquals(List.of(), JsonPath.read(calendarOf(ania), "$.trainings[0].partners"));

        // Cancelled outright: gone from both calendars.
        mockMvc.perform(delete("/api/admin/personal-trainings/" + first).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String second = coachCreates(admin, aniaId, groupBody(TOMORROW, "Siła", kasiaId));
        mockMvc.perform(delete("/api/admin/personal-trainings/" + second).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(0, rows(aniaId));
        assertEquals(0, rows(kasiaId));
    }

    @Test
    void shouldNotDeleteAPartnersTickedOffRecordWithTheSession() throws Exception {
        String admin = adminToken();
        flagClient("ania@mocnygrzbiet.test", "Ania");
        String kasia = flagClient("kasia@mocnygrzbiet.test", "Kasia");
        UUID kasiaId = idOf("kasia@mocnygrzbiet.test");
        String aniasRow = coachCreates(admin, idOf("ania@mocnygrzbiet.test"), groupBody(YESTERDAY, "Siła", kasiaId));
        String kasiasRow = jdbc.queryForObject("SELECT id::text FROM personal_trainings WHERE athlete_id = ?",
                String.class, kasiaId);
        mockMvc.perform(post("/api/user/my-training/trainings/" + kasiasRow + "/complete")
                        .header("Authorization", "Bearer " + kasia).contentType(APPLICATION_JSON).content("""
                            {"rpe":6}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/personal-trainings/" + aniasRow).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertEquals(0, rows(idOf("ania@mocnygrzbiet.test")));
        assertEquals(1, rows(kasiaId), "her effort rating is her record, not part of the session's plan");
    }

    @Test
    void shouldDuplicateAndMoveTheWholeGroupButCopyToAnotherCalendarAlone() throws Exception {
        String admin = adminToken();
        String ania = flagClient("ania@mocnygrzbiet.test", "Ania");
        String kasia = flagClient("kasia@mocnygrzbiet.test", "Kasia");
        flagClient("ola@mocnygrzbiet.test", "Ola");
        UUID aniaId = idOf("ania@mocnygrzbiet.test");
        UUID kasiaId = idOf("kasia@mocnygrzbiet.test");
        String aniasRow = coachCreates(admin, aniaId, groupBody(TOMORROW, "Siła", kasiaId));

        // "The same again next week": the pair again, as a new session of its own.
        mockMvc.perform(post("/api/admin/personal-trainings/" + aniasRow + "/duplicate")
                        .header("Authorization", "Bearer " + admin).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partners[0]").value("Kasia"));
        assertEquals(2, rows(kasiaId));
        List<String> groups = jdbc.queryForList("SELECT DISTINCT group_id::text FROM personal_trainings", String.class);
        assertEquals(2, groups.size(), "the copy is its own session");

        // Moved in Ania's calendar → moved in Kasia's.
        mockMvc.perform(post("/api/admin/personal-trainings/paste").header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"MOVE"}""".formatted(aniasRow, TOMORROW.plusDays(1))))
                .andExpect(status().isOk());
        assertEquals(List.of(TOMORROW.plusDays(1).toString(), TOMORROW.plusDays(7).toString()),
                JsonPath.read(calendarOf(kasia), "$.trainings[*].date"));

        // Copied into Ola's calendar: a plain training of hers, nobody else's session.
        mockMvc.perform(post("/api/admin/personal-trainings/paste").header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY","targetAthleteId":"%s"}"""
                                .formatted(aniasRow, TOMORROW, idOf("ola@mocnygrzbiet.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").doesNotExist())
                .andExpect(jsonPath("$.partners.length()").value(0));
        assertEquals(List.of("Kasia"), JsonPath.read(calendarOf(ania), "$.trainings[0].partners"));
    }

    @Test
    void shouldGiveEachOccurrenceOfAGroupSeriesItsOwnSessionAndEachPersonTheirOwnSeries() throws Exception {
        String admin = adminToken();
        flagClient("ania@mocnygrzbiet.test", "Ania");
        flagClient("kasia@mocnygrzbiet.test", "Kasia");
        UUID aniaId = idOf("ania@mocnygrzbiet.test");
        UUID kasiaId = idOf("kasia@mocnygrzbiet.test");

        String first = coachCreates(admin, aniaId, """
            {"date":"%s","title":"Siła","repeatWeeklyUntil":"%s","partnerIds":["%s"]}"""
                .formatted(TOMORROW, TOMORROW.plusWeeks(1), kasiaId));

        assertEquals(2, rows(aniaId));
        assertEquals(2, rows(kasiaId));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT group_id) FROM personal_trainings", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT series_id) FROM personal_trainings", Integer.class),
                "a series per person, never one reaching across two calendars");

        // "This and the following ones", for the whole session: the pair's block is over.
        mockMvc.perform(delete("/api/admin/personal-trainings/" + first + "?following=true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(0, rows(aniaId));
        assertEquals(0, rows(kasiaId));
    }

    @Test
    void shouldQueueAChangeMailForEveryParticipant() throws Exception {
        String admin = adminToken();
        flagClient("ania@mocnygrzbiet.test", "Ania");
        flagClient("kasia@mocnygrzbiet.test", "Kasia");

        coachCreates(admin, idOf("ania@mocnygrzbiet.test"), groupBody(TOMORROW, "Siła", idOf("kasia@mocnygrzbiet.test")));

        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT athlete_id) FROM training_change_notices", Integer.class));
    }
}
