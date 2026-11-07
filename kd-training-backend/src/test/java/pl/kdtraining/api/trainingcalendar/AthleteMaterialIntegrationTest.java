package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.training.ExerciseVideo;
import pl.kdtraining.domain.training.ExerciseVideoRepository;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteMaterialIntegrationTest extends BaseIntegrationTest {

    private static final String ADMIN_BASE = "/api/admin/personal-trainings/materials";
    private static final String CLIENT_BASE = "/api/user/my-training/materials";

    @Autowired private ExerciseVideoRepository videoRepository;
    @Autowired private JdbcTemplate jdbc;

    private String flagClient(String email) {
        String token = createUserAndGetToken(email, "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private UUID video() {
        return videoRepository.save(new ExerciseVideo("Przysiad", "https://youtu.be/abc12345678", "abc12345678", null)).getId();
    }

    private String addMaterial(String admin, UUID athleteId, String body) throws Exception {
        String json = mockMvc.perform(post(ADMIN_BASE + "?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void ask(String token, String base, String materialId, String text) throws Exception {
        mockMvc.perform(post(base + "/" + materialId + "/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("""
                            {"body":"%s"}""".formatted(text)))
                .andExpect(status().isCreated());
    }

    private void opensPlan(String token, String markSeenUrl) throws Exception {
        mockMvc.perform(post(markSeenUrl + "to=" + LocalDate.now()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private long clientUnread(String client) throws Exception {
        String json = mockMvc.perform(get("/api/user/my-training/summary").header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.unreadCount")).longValue();
    }

    private long coachUnread(String admin) throws Exception {
        String json = mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].unreadCount")).longValue();
    }

    @Test
    void shouldShowTheCoachsMaterialToTheClientWithItsVideo() throws Exception {
        String admin = adminToken();
        String client = flagClient("client@kdtraining.test");
        UUID videoId = video();

        addMaterial(admin, idOf("client@kdtraining.test"), """
            {"title":"Rozgrzewka barków","note":"Codziennie rano","videoId":"%s"}""".formatted(videoId));

        mockMvc.perform(get(CLIENT_BASE).header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Rozgrzewka barków"))
                .andExpect(jsonPath("$[0].note").value("Codziennie rano"))
                .andExpect(jsonPath("$[0].video.embedUrl").exists())
                .andExpect(jsonPath("$[0].unread").value(true));
    }

    @Test
    void shouldRefuseAMaterialWithNothingButATitle() throws Exception {
        String admin = adminToken();
        flagClient("client@kdtraining.test");

        mockMvc.perform(post(ADMIN_BASE + "?athleteId=" + idOf("client@kdtraining.test"))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"title":"Sam tytuł","note":"   "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepEveryClientToTheirOwnMaterials() throws Exception {
        // The client may only read and ask; another client's material is a 404, never a 403.
        String admin = adminToken();
        flagClient("client@kdtraining.test");
        String other = flagClient("other@kdtraining.test");
        String material = addMaterial(admin, idOf("client@kdtraining.test"), """
            {"title":"Oddech","note":"Wdech nosem"}""");

        mockMvc.perform(get(CLIENT_BASE).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(CLIENT_BASE + "/" + material + "/questions")
                        .header("Authorization", "Bearer " + other)
                        .contentType(APPLICATION_JSON).content("""
                            {"body":"Cudze pytanie"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN_BASE + "/" + material).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCountQuestionsAndRepliesAsNewsForTheOtherSide() throws Exception {
        String admin = adminToken();
        String client = flagClient("client@kdtraining.test");
        UUID clientId = idOf("client@kdtraining.test");
        String material = addMaterial(admin, clientId, """
            {"title":"Oddech","note":"Wdech nosem"}""");

        // A new material is news for the client, until they open the plan page.
        assertEquals(1, clientUnread(client));
        opensPlan(client, "/api/user/my-training/mark-seen?");
        assertEquals(0, clientUnread(client));

        // The client's question lights the coach's roster; the coach's reply lights the client's badge.
        opensPlan(admin, "/api/admin/personal-trainings/mark-seen?athleteId=" + clientId + "&");
        ask(client, CLIENT_BASE, material, "Ile powtórzeń?");
        assertEquals(1, coachUnread(admin));
        mockMvc.perform(get(ADMIN_BASE + "?athleteId=" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].unread").value(true))
                .andExpect(jsonPath("$[0].questions[0].fromCoach").value(false));

        ask(admin, ADMIN_BASE, material, "Dziesięć");
        assertEquals(1, clientUnread(client));
        mockMvc.perform(get(CLIENT_BASE).header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$[0].questions.length()").value(2))
                .andExpect(jsonPath("$[0].questions[1].fromCoach").value(true))
                .andExpect(jsonPath("$[0].unread").value(true));
    }

    @Test
    void shouldNotDeleteALibraryVideoAMaterialStillShows() throws Exception {
        // Both foreign keys onto exercise_videos are RESTRICT; without the check this was a 500.
        String admin = adminToken();
        flagClient("client@kdtraining.test");
        UUID videoId = video();
        addMaterial(admin, idOf("client@kdtraining.test"), """
            {"title":"Przysiad","videoId":"%s"}""".formatted(videoId));

        mockMvc.perform(delete("/api/admin/exercise-videos/" + videoId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldEraseMaterialsWithTheTrainingPlan() throws Exception {
        String admin = adminToken();
        flagClient("client@kdtraining.test");
        UUID clientId = idOf("client@kdtraining.test");
        String material = addMaterial(admin, clientId, """
            {"title":"Oddech","note":"Wdech nosem"}""");
        ask(admin, ADMIN_BASE, material, "Pamiętaj o przeponie");

        mockMvc.perform(delete("/api/admin/users/" + clientId + "/training-plan").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM athlete_materials", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM material_questions", Long.class));
    }

    @Test
    void shouldPutMaterialsAwayWithTheClientFlag() throws Exception {
        // Switching the plan off puts the whole record away; a material left writable behind it
        // would be the one piece that never got put away with it.
        String admin = adminToken();
        flagClient("client@kdtraining.test");
        UUID clientId = idOf("client@kdtraining.test");
        String material = addMaterial(admin, clientId, """
            {"title":"Oddech","note":"Wdech nosem"}""");
        User user = userRepository.findById(clientId).orElseThrow();
        user.setAthlete(false);
        userRepository.save(user);

        mockMvc.perform(get(ADMIN_BASE + "?athleteId=" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ADMIN_BASE + "/" + material + "/questions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"body":"Jeszcze jedno"}"""))
                .andExpect(status().isNotFound());
    }
}
