package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.mail.TrainingMailService;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client asked for mail ("ludzie pewnie lepiej mail"), the coach for the panel only: the coach's
 * replies notify the client, the client's messages notify nobody by mail.
 */
class CoachReplyNotificationIntegrationTest extends BaseIntegrationTest {

    @MockitoBean private TrainingMailService trainingMail;

    private String flagClient() {
        String token = createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail("client@kdtraining.test").orElseThrow().getId();
    }

    private String training(String admin) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"date":"%s","title":"Siła"}""".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void comment(String token, String url) throws Exception {
        mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("""
                            {"body":"Wiadomość"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldMailTheClientWhenTheCoachReplies() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = training(admin);

        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");

        verify(trainingMail).sendCoachReplyNotice("client@kdtraining.test", "Ala");
    }

    @Test
    void shouldNotMailAnybodyWhenTheClientWrites() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        String id = training(admin);

        comment(client, "/api/user/my-training/trainings/" + id + "/comments");

        verify(trainingMail, never()).sendCoachReplyNotice(anyString(), anyString());
    }

    @Test
    void shouldSendOneMailForSeveralRepliesInARow() throws Exception {
        String admin = adminToken();
        flagClient();
        String id = training(admin);

        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");
        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");
        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");

        verify(trainingMail, times(1)).sendCoachReplyNotice(anyString(), anyString());
    }

    @Test
    void shouldMailTheClientWhenTheCoachRepliesUnderAMaterial() throws Exception {
        String admin = adminToken();
        flagClient();
        String json = mockMvc.perform(post("/api/admin/personal-trainings/materials?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("""
                            {"title":"Oddech","note":"Wdech nosem"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String material = JsonPath.read(json, "$.id");

        comment(admin, "/api/admin/personal-trainings/materials/" + material + "/questions");

        verify(trainingMail).sendCoachReplyNotice("client@kdtraining.test", "Ala");
    }

    @Test
    void shouldMailTheClientWhenTheCoachRepliesWithAPhoto() throws Exception {
        // The photo reply has its own endpoint (multipart); it must not be the one way to write unannounced.
        String admin = adminToken();
        flagClient();
        String id = training(admin);
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpg", out);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(new MockMultipartFile("file", "ustawienie.jpg", "image/jpeg", out.toByteArray()))
                        .param("trainingId", id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());

        verify(trainingMail).sendCoachReplyNotice("client@kdtraining.test", "Ala");
    }

    @Test
    void shouldKeepTheReplySavedWhenTheMailCannotBeQueued() throws Exception {
        // A full mail queue rejects the task, and an exception out of afterCommit reaches the caller:
        // the reply is already saved, so a 500 would make the coach write it twice. And a notice that
        // never left must not use up the quiet period — the next reply tries again.
        String admin = adminToken();
        flagClient();
        String id = training(admin);
        doThrow(new org.springframework.core.task.TaskRejectedException("queue full"))
                .doNothing()
                .when(trainingMail).sendCoachReplyNotice(anyString(), anyString());

        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");
        comment(admin, "/api/admin/personal-trainings/" + id + "/comments");

        verify(trainingMail, times(2)).sendCoachReplyNotice("client@kdtraining.test", "Ala");
    }
}
