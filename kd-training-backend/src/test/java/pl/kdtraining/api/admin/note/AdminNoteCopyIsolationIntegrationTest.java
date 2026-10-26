package pl.kdtraining.api.admin.note;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A copied training must never bring the note along.
 *
 * <p>The dangerous case is the cross-client one: a note reading "still afraid of heights" is an
 * observation about ONE person, and duplicating their training into somebody else's calendar must
 * not duplicate it. Nothing in the code does that today — {@code duplicate} and {@code paste} copy
 * ATTACHMENTS and nothing else — but that is a fact about what somebody did not write, and it holds
 * only until the day someone adds notes to {@code copyBetweenTrainings} "for consistency" with
 * videos. Videos are part of the prescription and are meant to travel; a note is a record of what
 * happened on one day to one person, and is not.
 *
 * <p>Three shapes, because they behave differently and only one of them is obvious:
 * a COPY makes a new row (note stays behind), a same-client MOVE re-dates the SAME row (note travels
 * with it, correctly), and a cross-client MOVE is a copy plus a deletion (the note dies with the
 * original rather than moving to the other person).
 */
class AdminNoteCopyIsolationIntegrationTest extends BaseIntegrationTest {

    private static final String NOTE = "PRYWATNA-boi-sie-wysokosci";

    @Autowired private JdbcTemplate jdbc;

    private UUID flagAthlete(String email, String firstName) {
        createUserAndGetToken(email, firstName, "Testowy", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return user.getId();
    }

    private String createTraining(String admin, UUID athleteId, LocalDate date) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void writeNote(String admin, String trainingId) throws Exception {
        mockMvc.perform(put("/api/admin/notes/training/" + trainingId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"body\":\"" + NOTE + "\"}"))
            .andExpect(status().isNoContent());
    }

    private long notesOnTraining(String trainingId) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM admin_private_notes WHERE training_id = ?::uuid", Long.class, trainingId);
        return n == null ? 0 : n;
    }

    private long notesAboutAthlete(UUID athleteId) {
        Long n = jdbc.queryForObject("""
            SELECT count(*) FROM admin_private_notes n
            JOIN personal_trainings t ON t.id = n.training_id
            WHERE t.athlete_id = ?""", Long.class, athleteId);
        return n == null ? 0 : n;
    }

    @Test
    void shouldNotCarryTheNoteWhenATrainingIsDuplicated() throws Exception {
        String admin = adminToken();
        UUID marek = flagAthlete("marek@kdtraining.test", "Marek");
        String source = createTraining(admin, marek, LocalDate.now());
        writeNote(admin, source);

        String copy = JsonPath.read(mockMvc.perform(
                post("/api/admin/personal-trainings/" + source + "/duplicate")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn().getResponse().getContentAsString(), "$.id");

        assertEquals(1, notesOnTraining(source), "the original lost its note");
        assertEquals(0, notesOnTraining(copy), "the duplicate arrived carrying a private note");

        // And the copy really reads as empty through the API, not just in the table.
        mockMvc.perform(get("/api/admin/notes/training/" + copy)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist());
    }

    @Test
    void shouldNotCarryTheNoteIntoAnotherPersonsCalendarOnCopy() throws Exception {
        // The leak this test exists for: an observation about Marek appearing under Ania's name.
        String admin = adminToken();
        UUID marek = flagAthlete("marek@kdtraining.test", "Marek");
        UUID ania = flagAthlete("ania@kdtraining.test", "Ania");
        String source = createTraining(admin, marek, LocalDate.now());
        writeNote(admin, source);

        mockMvc.perform(post("/api/admin/personal-trainings/paste")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"sourceId":"%s","targetDate":"%s","mode":"COPY","targetAthleteId":"%s"}"""
                    .formatted(source, LocalDate.now().plusDays(1), ania)))
            .andExpect(status().is2xxSuccessful());

        assertEquals(1, notesAboutAthlete(marek), "Marek's own note should be untouched");
        assertEquals(0, notesAboutAthlete(ania), "a note about Marek reached Ania's calendar");
    }

    @Test
    void shouldKeepTheNoteWhenTheSamePersonsTrainingIsMovedToAnotherDay() throws Exception {
        // A same-client MOVE re-dates the SAME row, so the note stays attached — the training did not
        // become a different training, it moved from Tuesday to Thursday.
        String admin = adminToken();
        UUID marek = flagAthlete("marek@kdtraining.test", "Marek");
        String source = createTraining(admin, marek, LocalDate.now());
        writeNote(admin, source);

        mockMvc.perform(post("/api/admin/personal-trainings/paste")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"sourceId":"%s","targetDate":"%s","mode":"MOVE"}"""
                    .formatted(source, LocalDate.now().plusDays(2))))
            .andExpect(status().is2xxSuccessful());

        assertEquals(1, notesOnTraining(source), "moving a training within one calendar lost its note");
    }

    @Test
    void shouldDestroyRatherThanTransferTheNoteWhenMovingBetweenPeople() throws Exception {
        // A cross-client MOVE is a fresh copy plus a deletion of the original. The note goes with the
        // original through the cascade: it never lands on the other person, and it does not linger
        // attached to a training that no longer exists.
        String admin = adminToken();
        UUID marek = flagAthlete("marek@kdtraining.test", "Marek");
        UUID ania = flagAthlete("ania@kdtraining.test", "Ania");
        String source = createTraining(admin, marek, LocalDate.now());
        writeNote(admin, source);

        mockMvc.perform(post("/api/admin/personal-trainings/paste")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"sourceId":"%s","targetDate":"%s","mode":"MOVE","targetAthleteId":"%s"}"""
                    .formatted(source, LocalDate.now().plusDays(1), ania)))
            .andExpect(status().is2xxSuccessful());

        assertEquals(0, notesAboutAthlete(ania), "a note about Marek reached Ania's calendar");
        assertEquals(0, notesAboutAthlete(marek), "the note outlived the training it described");
        assertEquals(0, notesOnTraining(source));
    }
}
