package pl.mocnygrzbiet.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mocnygrzbiet.BaseIntegrationTest;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRole;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Changed;
import pl.mocnygrzbiet.infrastructure.mail.TrainingChangeDigest.Slot;
import pl.mocnygrzbiet.infrastructure.mail.TrainingMailService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client's answer: a mail when the coach adds, changes or cancels a training. Through the real
 * endpoints, with the mailer called directly instead of waiting for the scheduler: {@code later()} is
 * a cutoff past every change, i.e. "the quiet period is over".
 */
class TrainingChangeMailIntegrationTest extends BaseIntegrationTest {

    private static final String CLIENT = "client@mocnygrzbiet.test";
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final LocalDate IN_A_WEEK = LocalDate.now().plusDays(7);

    @MockitoBean private TrainingMailService trainingMail;
    @Autowired private TrainingChangeMailer mailer;
    @Autowired private JdbcTemplate jdbc;

    private String flagClient() {
        String token = createUserAndGetToken(CLIENT, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(CLIENT).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail(CLIENT).orElseThrow().getId();
    }

    private static Instant later() {
        return Instant.now().plusSeconds(60);
    }

    private String create(String token, String url, String body) throws Exception {
        String json = mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String coachCreates(String admin, String body) throws Exception {
        return create(admin, "/api/admin/personal-trainings?athleteId=" + clientId(), body);
    }

    private void coachUpdates(String admin, String id, String body) throws Exception {
        mockMvc.perform(put("/api/admin/personal-trainings/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void coachDeletes(String admin, String id) throws Exception {
        mockMvc.perform(delete("/api/admin/personal-trainings/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    /** Sends whatever is queued and forgets the mail, so the next step starts from "client is up to date". */
    private void flush() {
        mailer.sendDigest(clientId(), later());
        clearInvocations(trainingMail);
    }

    private TrainingChangeDigest sentDigest() {
        ArgumentCaptor<TrainingChangeDigest> digest = ArgumentCaptor.forClass(TrainingChangeDigest.class);
        verify(trainingMail).sendTrainingChanges(eq(CLIENT), eq("Ala"), digest.capture());
        return digest.getValue();
    }

    private int queued() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM training_change_notices", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void shouldMailOnceForATrainingTheCoachAdded() throws Exception {
        String admin = adminToken();
        flagClient();
        coachCreates(admin, """
            {"date":"%s","startTime":"18:00","endTime":"19:00","title":"Siła"}""".formatted(TOMORROW));

        assertTrue(mailer.sendDigest(clientId(), later()));

        assertEquals(List.of(new Slot(TOMORROW, LocalTime.of(18, 0))), sentDigest().added());
        assertEquals(0, queued(), "a sent change stays sent");
    }

    @Test
    void shouldWaitWhileTheCoachIsStillEditing() throws Exception {
        String admin = adminToken();
        flagClient();
        coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));

        // A cutoff before the change: the quiet period has not run out yet.
        assertFalse(mailer.sendDigest(clientId(), Instant.now().minusSeconds(60)));
        assertTrue(mailer.athletesDue(Instant.now().minusSeconds(60)).isEmpty());

        verifyNoInteractions(trainingMail);
        assertEquals(1, queued(), "nothing is lost while waiting");
        assertEquals(List.of(clientId()), mailer.athletesDue(later()));
    }

    @Test
    void shouldSendTheFinalSlotWhenATrainingIsAddedAndThenMoved() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","startTime":"17:00","title":"Siła"}""".formatted(TOMORROW));
        coachUpdates(admin, id, """
            {"date":"%s","startTime":"18:00","title":"Siła","version":0}""".formatted(TOMORROW));

        mailer.sendDigest(clientId(), later());

        TrainingChangeDigest digest = sentDigest();
        // The client never heard of 17:00: one new training, at the hour it ended up at.
        assertEquals(List.of(new Slot(TOMORROW, LocalTime.of(18, 0))), digest.added());
        assertTrue(digest.changed().isEmpty());
    }

    @Test
    void shouldStaySilentWhenATrainingIsAddedAndRemovedBeforeTheMail() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","title":"Pomyłka"}""".formatted(TOMORROW));
        coachDeletes(admin, id);

        assertFalse(mailer.sendDigest(clientId(), later()));

        verifyNoInteractions(trainingMail);
        assertEquals(0, queued());
    }

    @Test
    void shouldPutAWholeSeriesInOneMail() throws Exception {
        String admin = adminToken();
        flagClient();
        coachCreates(admin, """
            {"date":"%s","title":"Siła","repeatWeeklyUntil":"%s"}""".formatted(TOMORROW, TOMORROW.plusWeeks(2)));

        mailer.sendDigest(clientId(), later());

        assertEquals(List.of(new Slot(TOMORROW, null), new Slot(TOMORROW.plusWeeks(1), null),
                new Slot(TOMORROW.plusWeeks(2), null)), sentDigest().added());
    }

    @Test
    void shouldTellTheOldAndTheNewSlotWhenAKnownTrainingMoves() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","startTime":"17:00","title":"Siła"}""".formatted(TOMORROW));
        flush();

        coachUpdates(admin, id, """
            {"date":"%s","startTime":"18:30","title":"Siła","version":0}""".formatted(IN_A_WEEK));
        coachUpdates(admin, id, """
            {"date":"%s","startTime":"18:30","title":"Siła","description":"Rozgrzewka 10 min","version":1}"""
                .formatted(IN_A_WEEK));
        mailer.sendDigest(clientId(), later());

        assertEquals(List.of(new Changed(new Slot(IN_A_WEEK, LocalTime.of(18, 30)),
                new Slot(TOMORROW, LocalTime.of(17, 0)))), sentDigest().changed());
    }

    @Test
    void shouldReportAnEditInPlaceWithoutAnOldSlot() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));
        flush();

        coachUpdates(admin, id, """
            {"date":"%s","title":"Siła i mobilność","version":0}""".formatted(TOMORROW));
        mailer.sendDigest(clientId(), later());

        assertEquals(List.of(new Changed(new Slot(TOMORROW, null), null)), sentDigest().changed());
    }

    @Test
    void shouldNotMailASaveThatChangedNothing() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));
        flush();

        coachUpdates(admin, id, """
            {"date":"%s","title":"Siła","version":0}""".formatted(TOMORROW));

        assertEquals(0, queued());
    }

    @Test
    void shouldMailACancellationInTheSlotTheClientKnew() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","startTime":"18:00","title":"Siła"}""".formatted(TOMORROW));
        flush();

        coachUpdates(admin, id, """
            {"date":"%s","startTime":"19:00","title":"Siła","version":0}""".formatted(TOMORROW));
        coachDeletes(admin, id);
        mailer.sendDigest(clientId(), later());

        TrainingChangeDigest digest = sentDigest();
        assertEquals(List.of(new Slot(TOMORROW, LocalTime.of(18, 0))), digest.cancelled());
        assertTrue(digest.changed().isEmpty());
    }

    @Test
    void shouldMailACopyPastedIntoTheClientsCalendar() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));
        flush();

        create(admin, "/api/admin/personal-trainings/" + id + "/duplicate", "{}");
        mailer.sendDigest(clientId(), later());

        assertEquals(List.of(new Slot(TOMORROW.plusDays(7), null)), sentDigest().added());
    }

    @Test
    void shouldMailATrainingMovedByCutAndPaste() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","startTime":"18:00","title":"Siła"}""".formatted(TOMORROW));
        flush();

        mockMvc.perform(post("/api/admin/personal-trainings/paste").header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"MOVE"}""".formatted(id, IN_A_WEEK)))
                .andExpect(status().isOk());
        mailer.sendDigest(clientId(), later());

        assertEquals(List.of(new Changed(new Slot(IN_A_WEEK, LocalTime.of(18, 0)),
                new Slot(TOMORROW, LocalTime.of(18, 0)))), sentDigest().changed());
    }

    @Test
    void shouldNotQueueTheClientsOwnEntriesTasksOrThePast() throws Exception {
        String admin = adminToken();
        String client = flagClient();

        create(client, "/api/user/my-training/trainings", """
            {"date":"%s","title":"Własny bieg"}""".formatted(TOMORROW));
        coachCreates(admin, """
            {"kind":"TASK","date":"%s","title":"Limit kalorii","targetCalories":2200}""".formatted(TOMORROW));
        coachCreates(admin, """
            {"date":"%s","title":"Wczorajszy"}""".formatted(LocalDate.now().minusDays(1)));

        assertEquals(0, queued());
    }

    @Test
    void shouldDropTheQueueOfABlockedClientWithoutMailing() throws Exception {
        String admin = adminToken();
        flagClient();
        coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));
        User user = userRepository.findByEmail(CLIENT).orElseThrow();
        user.block();
        userRepository.save(user);

        assertFalse(mailer.sendDigest(clientId(), later()));

        verify(trainingMail, never()).sendTrainingChanges(anyString(), anyString(), any());
        assertEquals(0, queued());
    }

    @Test
    void shouldForgetTheQueueWhenThePlanIsErased() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = coachCreates(admin, """
            {"date":"%s","title":"Siła"}""".formatted(TOMORROW));
        flush();
        coachUpdates(admin, id, """
            {"date":"%s","title":"Siła","version":0}""".formatted(IN_A_WEEK));

        mockMvc.perform(delete("/api/admin/users/" + clientId() + "/training-plan")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // Otherwise a plan switched back on would open with "cancelled" for every erased training.
        assertEquals(0, queued());
    }
}
