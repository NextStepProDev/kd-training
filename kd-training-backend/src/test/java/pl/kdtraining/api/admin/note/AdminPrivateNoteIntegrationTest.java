package pl.kdtraining.api.admin.note;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The notebook's rules: one note per (author, training), private to its author, gone with its training. */
class AdminPrivateNoteIntegrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    // --- fixtures --------------------------------------------------------------------------------

    private String secondAdminToken() {
        return createUserAndGetToken("other-admin@kdtraining.test", "Druga", "Adminka", UserRole.ADMIN);
    }

    private String flagAthlete() {
        String token = createUserAndGetToken("athlete@kdtraining.test", "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail("athlete@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return token;
    }

    private UUID athleteId() {
        return userRepository.findByEmail("athlete@kdtraining.test").orElseThrow().getId();
    }

    /** A training on the flagged athlete's plan, created through the API like the coach would. */
    private String seedTraining(LocalDate date) throws Exception {
        if (userRepository.findByEmail("athlete@kdtraining.test").isEmpty()) {
            flagAthlete();
        }
        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(created, "$.id");
    }

    private String seedTraining() throws Exception {
        return seedTraining(LocalDate.now());
    }

    private static String notePath(String trainingId) {
        return "/api/admin/notes/training/" + trainingId;
    }

    private void writeNote(String path, String token, String body, int status) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().is(status));
    }

    private long countNotes() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM admin_private_notes", Long.class);
        return n == null ? 0 : n;
    }

    // --- privacy ---------------------------------------------------------------------------------

    @Test
    void shouldKeepOneAdminsNoteInvisibleToAnother() throws Exception {
        String path = notePath(seedTraining());
        writeNote(path, adminToken(), "Bolało kolano przy przysiadzie", 204);

        mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("Bolało kolano przy przysiadzie"));

        // The other admin asks the same question about the same training and is told there is nothing.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + secondAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void shouldRefuseTheNotebookToANonAdmin() throws Exception {
        String path = notePath(seedTraining());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldRefuseTheNotebookToTheClientItIsAbout() throws Exception {
        String athlete = flagAthlete();
        String path = notePath(seedTraining());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + athlete))
            .andExpect(status().isForbidden());
    }

    // --- upsert ----------------------------------------------------------------------------------

    @Test
    void shouldTreatASecondWriteAsACorrectionRatherThanASecondNote() throws Exception {
        String path = notePath(seedTraining());
        writeNote(path, adminToken(), "Pierwsza wersja", 204);
        writeNote(path, adminToken(), "Druga wersja", 204);

        assertEquals(1, countNotes());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken()))
            .andExpect(jsonPath("$.body").value("Druga wersja"));
    }

    @Test
    void shouldKeepQuotesAndApostrophesExactlyAsTyped() throws Exception {
        String path = notePath(seedTraining());
        // No HTML escaping on the way in: the author is the only reader, and escaping here would
        // force every render site to undo it.
        writeNote(path, adminToken(), "Powiedział \\\"pas\\\" & wyszedł", 204);

        mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken()))
            .andExpect(jsonPath("$.body").value("Powiedział \"pas\" & wyszedł"));
    }

    @Test
    void shouldRefuseABlankNoteBecauseTheBinIsWhatDeletes() throws Exception {
        writeNote(notePath(seedTraining()), adminToken(), "   ", 400);
        assertEquals(0, countNotes());
    }

    @Test
    void shouldRejectAnUnknownTargetSegment() throws Exception {
        writeNote("/api/admin/notes/kalendarz/" + UUID.randomUUID(), adminToken(), "cokolwiek", 400);
    }

    @Test
    void shouldAnswer404ForATargetThatDoesNotExist() throws Exception {
        writeNote(notePath(UUID.randomUUID().toString()), adminToken(), "o niczym", 404);
    }

    // --- the athlete gate, and the hole it must NOT have -----------------------------------------

    @Test
    void shouldRefuseWritingAfterTheAthleteFlagIsClearedButStillAllowDeleting() throws Exception {
        // This is one test on purpose: the gate and the escape hatch are the same decision. Giving
        // all three operations the same guard would strand another person's data — invisible AND
        // undeletable at once.
        String admin = adminToken();
        String path = notePath(seedTraining(LocalDate.now().minusDays(7)));

        writeNote(path, admin, "Dalej boi się martwego ciągu", 204);
        assertEquals(1, countNotes());

        User athlete = userRepository.findById(athleteId()).orElseThrow();
        athlete.setAthlete(false);
        userRepository.saveAndFlush(athlete);

        // Reading and writing are gated: the calendar is out of reach, so the notebook is too.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
        writeNote(path, admin, "Poprawka", 404);

        // Deleting is NOT gated — otherwise this row could never be removed.
        mockMvc.perform(delete(path).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        assertEquals(0, countNotes());
    }

    @Test
    void shouldDeleteIdempotentlyWhenThereIsNoNote() throws Exception {
        mockMvc.perform(delete(notePath(seedTraining()))
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());
    }

    // --- what the database refuses, checked through raw SQL --------------------------------------

    /** Asserting the exception alone would pass for ANY reason — the constraint name is the assertion. */
    private void assertRefusedBy(String constraint, Runnable statement) {
        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, statement::run);
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains(constraint),
            "expected " + constraint + " to refuse this, but the database said: " + message);
    }

    @Test
    void shouldRefuseANoteWithNoTraining() {
        adminToken();
        assertRefusedBy("training_id",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, body, updated_at)"
                + " VALUES (?, 'tekst', now())", adminUserId()));
    }

    @Test
    void shouldRefuseAWhitespaceOnlyBodyAtTheDatabaseToo() throws Exception {
        UUID trainingId = UUID.fromString(seedTraining());
        assertRefusedBy("chk_apn_body_not_blank",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, training_id, body, updated_at)"
                + " VALUES (?, ?, '   ', now())", adminUserId(), trainingId));
    }

    @Test
    void shouldRefuseASecondNoteOnTheSameTrainingByTheSameAuthor() throws Exception {
        String trainingId = seedTraining();
        writeNote(notePath(trainingId), adminToken(), "pierwsza", 204);
        // The unique constraint is what makes a second write a correction rather than a pile.
        assertRefusedBy("uq_apn_training",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, training_id, body, updated_at)"
                + " VALUES (?, ?, 'druga', now())", adminUserId(), UUID.fromString(trainingId)));
    }

    @Test
    void shouldAllowTwoNotesOnOneTrainingOnlyWhenTheyBelongToDifferentAuthors() throws Exception {
        String path = notePath(seedTraining());
        writeNote(path, adminToken(), "za ciężko", 204);
        writeNote(path, secondAdminToken(), "moim zdaniem w porządku", 204);
        assertEquals(2, countNotes());
    }

    // --- markers ---------------------------------------------------------------------------------

    @Test
    void shouldReportMarkersAsIdentifiersAndNeverAsText() throws Exception {
        String admin = adminToken();
        LocalDate today = LocalDate.now();
        String trainingId = seedTraining(today);
        writeNote(notePath(trainingId), admin, "o treningu", 204);

        String json = mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId()
                + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trainingIds[0]").value(trainingId))
            .andReturn().getResponse().getContentAsString();

        // The whole point of a separate endpoint per note is undone if the week view hands back the
        // notebook. This answers "is there a note here" and nothing else.
        assertFalse(json.contains("o treningu"), json);
    }

    @Test
    void shouldNotShowOneAdminsMarkersToAnother() throws Exception {
        LocalDate today = LocalDate.now();
        writeNote(notePath(seedTraining(today)), adminToken(), "moja uwaga", 204);

        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId()
                + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + secondAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trainingIds").isEmpty());
    }

    @Test
    void shouldLeaveOutTrainingsOutsideTheWindow() throws Exception {
        String admin = adminToken();
        LocalDate today = LocalDate.now();
        // Both edges: a window that only checked its start would still mark next month's trainings.
        writeNote(notePath(seedTraining(today.minusDays(10))), admin, "dawno", 204);
        writeNote(notePath(seedTraining(today.plusDays(10))), admin, "za dziesięć dni", 204);

        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId()
                + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trainingIds").isEmpty());
    }

    @Test
    void shouldRefuseAMarkerQueryWithoutAWindow() throws Exception {
        flagAthlete();
        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefuseAMarkerWindowWiderThanTheCalendarEverAsksFor() throws Exception {
        flagAthlete();
        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId()
                + "&from=2026-01-01&to=2026-12-31")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());
    }

    // --- what the API refuses -------------------------------------------------------------------

    @Test
    void shouldRefuseANoteLongerThanTheColumnAndAcceptOneExactlyAtTheLimit() throws Exception {
        String admin = adminToken();
        String path = notePath(seedTraining());
        writeNote(path, admin, "x".repeat(4001), 400);
        assertEquals(0, countNotes());

        writeNote(path, admin, "x".repeat(4000), 204);
        assertEquals(1, countNotes());
    }

    @Test
    void shouldBumpUpdatedAtWhenTheNoteIsCorrected() throws Exception {
        String admin = adminToken();
        String path = notePath(seedTraining());
        writeNote(path, admin, "pierwsza", 204);
        String first = mockMvc.perform(get(path).header("Authorization", "Bearer " + admin))
            .andReturn().getResponse().getContentAsString();

        Thread.sleep(10);
        writeNote(path, admin, "druga", 204);
        String second = mockMvc.perform(get(path).header("Authorization", "Bearer " + admin))
            .andReturn().getResponse().getContentAsString();

        // The timestamp comes from the JVM clock, not SQL now(): now() is the transaction start
        // time, which would freeze this across two writes in one request.
        String before = com.jayway.jsonpath.JsonPath.read(first, "$.updatedAt");
        String after = com.jayway.jsonpath.JsonPath.read(second, "$.updatedAt");
        org.junit.jupiter.api.Assertions.assertNotEquals(before, after,
            "a corrected note kept its old updatedAt");
    }

    // --- cascades --------------------------------------------------------------------------------

    @Test
    void shouldTakeTheNoteDownWithItsTraining() throws Exception {
        String trainingId = seedTraining();
        writeNote(notePath(trainingId), adminToken(), "o tym treningu", 204);
        assertEquals(1, countNotes());

        jdbc.update("DELETE FROM personal_trainings WHERE id = ?", UUID.fromString(trainingId));
        assertEquals(0, countNotes());
    }

    @Test
    void shouldTakeTheNoteDownWithItsAuthorsAccount() throws Exception {
        String path = notePath(seedTraining());
        String other = secondAdminToken();
        writeNote(path, other, "notatka drugiej adminki", 204);
        assertEquals(1, countNotes());

        UUID otherId = userRepository.findByEmail("other-admin@kdtraining.test").orElseThrow().getId();
        jdbc.update("DELETE FROM users WHERE id = ?", otherId);
        assertEquals(0, countNotes());
    }

    @Test
    void shouldTakeTheNoteDownWithTheClientItIsAbout() throws Exception {
        writeNote(notePath(seedTraining()), adminToken(), "spóźnił się", 204);
        assertEquals(1, countNotes());

        jdbc.update("DELETE FROM users WHERE id = ?", athleteId());
        assertEquals(0, countNotes());
    }
}
