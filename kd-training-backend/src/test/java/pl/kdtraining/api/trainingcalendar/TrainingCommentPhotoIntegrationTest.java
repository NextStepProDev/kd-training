package pl.kdtraining.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Photos attached to comments in the 1-on-1 plan — a screenshot from a sports watch.
 * <p>
 * The access rules matter more here than anywhere else in this package: these files are health data
 * under GDPR art. 9, and the 404-not-403 discipline plus the consent gate are what stand between
 * them and anyone else. Each is pinned below.
 */
class TrainingCommentPhotoIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    // --- helpers --------------------------------------------------------------------------------

    private String flagAthlete(String email, String firstName) {
        String token = createUserAndGetToken(email, firstName, "Testowy", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.save(user);
        return token;
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private String planTraining(String admin, UUID athleteId) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Bieg"}""".formatted(TOMORROW)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** A real JPEG, because the server decodes and re-encodes everything on this path. */
    private static MockMultipartFile screenshot() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1179, 2556, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new MockMultipartFile("file", "garmin.jpg", "image/jpeg", out.toByteArray());
    }

    private String uploadAsClient(String client, String trainingId, String body) throws Exception {
        var request = MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                .file(screenshot())
                .param("trainingId", trainingId)
                .header("Authorization", "Bearer " + client);
        if (body != null) {
            request = request.param("body", body);
        }
        String json = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    // --- the happy path -------------------------------------------------------------------------

    @Test
    void shouldLetClientAttachAScreenshotAndCoachSeeIt() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-client@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-client@kdtraining.test"));

        String commentId = uploadAsClient(client, trainingId, "Tak wyszło");

        // The coach reads the thread and gets the photo, addressed at their own endpoint
        mockMvc.perform(get("/api/admin/personal-trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Tak wyszło"))
                .andExpect(jsonPath("$[0].photo.url")
                        .value("/api/admin/personal-trainings/comments/" + commentId + "/photo"))
                // Re-encoded by us: 1179x2556 fits into 1280 on the long side
                .andExpect(jsonPath("$[0].photo.height").value(1280))
                .andExpect(jsonPath("$[0].photo.expiresAt").exists())
                .andExpect(jsonPath("$[0].photo.canDelete").value(true));

        // And the bytes come back as JPEG, uncacheable
        mockMvc.perform(get("/api/admin/personal-trainings/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /** A photo says "here is how it went" on its own — the words are optional. */
    @Test
    void shouldAcceptAPhotoWithNoText() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-silent@kdtraining.test", "Ola");
        String trainingId = planTraining(admin, idOf("photo-silent@kdtraining.test"));

        String commentId = uploadAsClient(client, trainingId, null);

        mockMvc.perform(get("/api/user/my-training/trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$[0].body").doesNotExist())
                .andExpect(jsonPath("$[0].photo.url")
                        .value("/api/user/my-training/comments/" + commentId + "/photo"));
    }

    @Test
    void shouldLetCoachAttachAPhotoToo() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-both@kdtraining.test", "Ela");
        String trainingId = planTraining(admin, idOf("photo-both@kdtraining.test"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(screenshot())
                        .param("trainingId", trainingId)
                        .param("body", "Tak ma wyglądać ustawienie")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCoach").value(true));

        // The client sees it and may NOT delete what the coach sent
        mockMvc.perform(get("/api/user/my-training/trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$[0].photo.canDelete").value(false));
    }

    // --- access ---------------------------------------------------------------------------------

    /** A stranger must not be able to tell the photo exists — 404, never 403. */
    @Test
    void shouldHideAPhotoFromAnotherAthlete() throws Exception {
        String admin = adminToken();
        String owner = flagAthlete("photo-owner@kdtraining.test", "Ala");
        String stranger = flagAthlete("photo-stranger@kdtraining.test", "Ula");
        String trainingId = planTraining(admin, idOf("photo-owner@kdtraining.test"));
        String commentId = uploadAsClient(owner, trainingId, null);

        mockMvc.perform(get("/api/user/my-training/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/user/my-training/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    /**
     * The consent gate covers the upload path for free, because it sits under
     * {@code /api/user/my-training/**}. That is the whole point of guarding the prefix rather than
     * each handler — a new endpoint is protected without anyone remembering to protect it.
     */
    @Test
    void shouldBlockUploadUntilConsentIsGiven() throws Exception {
        String admin = adminToken();
        String client = createUserAndGetToken("photo-noconsent@kdtraining.test", "Iga", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("photo-noconsent@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        userRepository.save(user);
        String trainingId = planTraining(admin, user.getId());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(screenshot())
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
    }

    /** No coaching relationship at all: the feature must not even appear to exist. */
    @Test
    void shouldReturnNotFoundForAnOrdinaryAccount() throws Exception {
        String admin = adminToken();
        String athlete = flagAthlete("photo-real@kdtraining.test", "Ala");
        String outsider = createUserAndGetToken("photo-outsider@kdtraining.test", "Jan", "Testowy", UserRole.USER);
        String trainingId = planTraining(admin, idOf("photo-real@kdtraining.test"));
        String commentId = uploadAsClient(athlete, trainingId, null);

        mockMvc.perform(get("/api/user/my-training/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectAnUnauthenticatedRead() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-anon@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-anon@kdtraining.test"));
        String commentId = uploadAsClient(client, trainingId, null);

        mockMvc.perform(get("/api/user/my-training/comments/" + commentId + "/photo"))
                .andExpect(status().isUnauthorized());
    }

    // --- limits and removal ---------------------------------------------------------------------

    @Test
    void shouldRefuseAFourthPhotoOnOneTraining() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-limit@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-limit@kdtraining.test"));

        uploadAsClient(client, trainingId, null);
        uploadAsClient(client, trainingId, null);
        uploadAsClient(client, trainingId, null);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(screenshot())
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict());
    }

    /**
     * Withdrawal has to be possible: a screenshot can show more than its sender intended, and
     * comments have no delete of their own. Removing the photo leaves the words behind.
     */
    @Test
    void shouldRefuseTheDaysTwentySixthPhotoInOneCalendar() throws Exception {
        // The per-training cap bounds one tile; a client can open as many tiles as they like. This is
        // the ceiling that bounds the disk the photos share with the database.
        String admin = adminToken();
        String client = flagAthlete("photo-daily@kdtraining.test", "Ola");
        UUID athlete = idOf("photo-daily@kdtraining.test");

        // 25 photos = nine trainings at three each, minus the last two slots
        for (int i = 0; i < 9; i++) {
            String trainingId = planTraining(admin, athlete);
            int room = Math.min(3, 25 - i * 3);
            for (int p = 0; p < room; p++) {
                uploadAsClient(client, trainingId, null);
            }
        }

        String fresh = planTraining(admin, athlete);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(screenshot())
                        .param("trainingId", fresh)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("25")));
    }

    @Test
    void shouldNotSpendOneClientsAllowanceOnAnother() throws Exception {
        // The reason this is counted per calendar and not per uploader: planning for a roster means
        // the coach uploads into many calendars in one sitting. Twenty clients at three photos each
        // would stop a per-account cap at the seventh person, having touched nobody's calendar hard.
        String admin = adminToken();
        String first = flagAthlete("photo-quota-a@kdtraining.test", "Ala");
        flagAthlete("photo-quota-b@kdtraining.test", "Bea");

        String firstTraining = planTraining(admin, idOf("photo-quota-a@kdtraining.test"));
        uploadAsClient(first, firstTraining, null);
        uploadAsClient(first, firstTraining, null);
        uploadAsClient(first, firstTraining, null);

        // The second client's calendar is untouched by any of that
        String secondTraining = planTraining(admin, idOf("photo-quota-b@kdtraining.test"));
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(screenshot())
                        .param("trainingId", secondTraining)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldCountTheCoachsUploadsAgainstTheClientsDayToo() throws Exception {
        // Whoever puts the photo there, it lands in the client's folder and takes the same disk. A
        // cap the coach can walk around is not a cap.
        String admin = adminToken();
        String client = flagAthlete("photo-daily-coach@kdtraining.test", "Kuba");
        UUID athlete = idOf("photo-daily-coach@kdtraining.test");

        for (int i = 0; i < 9; i++) {
            String trainingId = planTraining(admin, athlete);
            int room = Math.min(3, 25 - i * 3);
            for (int p = 0; p < room; p++) {
                uploadAsClient(client, trainingId, null);
            }
        }

        String fresh = planTraining(admin, athlete);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/training-photos")
                        .file(screenshot())
                        .param("trainingId", fresh)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldLetAuthorRemoveTheirOwnPhotoAndKeepTheText() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-withdraw@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-withdraw@kdtraining.test"));
        String commentId = uploadAsClient(client, trainingId, "Ups");

        mockMvc.perform(delete("/api/user/my-training/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/my-training/trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Ups"))
                .andExpect(jsonPath("$[0].photo").doesNotExist());
    }

    /** Nothing left to read once the picture is gone, so the bubble goes with it. */
    @Test
    void shouldRemoveTheWholeCommentWhenItWasOnlyAPhoto() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-only@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-only@kdtraining.test"));
        String commentId = uploadAsClient(client, trainingId, null);

        mockMvc.perform(delete("/api/user/my-training/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/my-training/trainings/" + trainingId + "/comments")
                        .header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** The coach answers for what the club holds, so they can remove anything in the thread. */
    @Test
    void shouldLetCoachRemoveAClientPhoto() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-coachdel@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-coachdel@kdtraining.test"));
        String commentId = uploadAsClient(client, trainingId, null);

        mockMvc.perform(delete("/api/admin/personal-trainings/comments/" + commentId + "/photo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    // --- validation -----------------------------------------------------------------------------

    /** PNG is fine for catalog artwork and refused here — see StorePolicy.TRAINING_PHOTO. */
    @Test
    void shouldRejectANonJpegUpload() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-png@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-png@kdtraining.test"));

        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(new MockMultipartFile("file", "a.png", "image/png", out.toByteArray()))
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAFileThatIsNotAnImage() throws Exception {
        String admin = adminToken();
        String client = flagAthlete("photo-shell@kdtraining.test", "Ala");
        String trainingId = planTraining(admin, idOf("photo-shell@kdtraining.test"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/user/my-training/photos")
                        .file(new MockMultipartFile("file", "shell.jpg", "image/jpeg",
                                "<?php system($_GET['c']); ?>".getBytes()))
                        .param("trainingId", trainingId)
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isBadRequest());
    }
}
