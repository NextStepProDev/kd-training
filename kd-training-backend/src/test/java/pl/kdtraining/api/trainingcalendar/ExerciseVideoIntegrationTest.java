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

class ExerciseVideoIntegrationTest extends BaseIntegrationTest {

    private static final String ID = "dQw4w9WgXcQ";
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private String addVideo(String admin, String name, String url) throws Exception {
        String json = mockMvc.perform(post("/api/admin/exercise-videos")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"name":"%s","url":"%s"}""".formatted(name, url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private UUID flagClient(String admin) throws Exception {
        createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@kdtraining.test").orElseThrow();
        user.setAthlete(true);
        // The client side of the calendar sits behind the GDPR art. 9 consent gate (users.training_consent_at)
        user.grantTrainingConsent();
        userRepository.save(user);
        return user.getId();
    }

    @Test
    void shouldStoreCanonicalPlayerUrlRatherThanThePastedLink() throws Exception {
        String admin = adminToken();

        String json = mockMvc.perform(post("/api/admin/exercise-videos")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"name":"Przysiad ze sztangą","url":"https://youtu.be/%s?t=42"}""".formatted(ID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // The iframe src is built from the id, so a messy share link cannot reach the browser
        org.junit.jupiter.api.Assertions.assertEquals(
                "https://www.youtube-nocookie.com/embed/" + ID, JsonPath.read(json, "$.embedUrl"));
    }

    @Test
    void shouldRejectTheSameVideoPastedInAnotherShape() throws Exception {
        // Deduplication keys on the video id: these two URLs are one clip, and the coach should be
        // told which library entry it already is rather than ending up with two.
        String admin = adminToken();
        addVideo(admin, "Przysiad", "https://www.youtube.com/watch?v=" + ID);

        mockMvc.perform(post("/api/admin/exercise-videos")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"name":"Przysiad inaczej","url":"https://youtu.be/%s"}""".formatted(ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Przysiad")));
    }

    @Test
    void shouldRejectLinksThatAreNotYouTube() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/exercise-videos")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"name":"Cokolwiek","url":"https://vimeo.com/123456"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFindVideosWithoutTypingPolishAccents() throws Exception {
        // Nobody types accents into a search box mid-session.
        String admin = adminToken();
        addVideo(admin, "Ćwiczenie na barki", "https://youtu.be/" + ID);

        mockMvc.perform(get("/api/admin/exercise-videos?query=cwiczenie")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // and a fragment from the middle of the name works too
        mockMvc.perform(get("/api/admin/exercise-videos?query=barki")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void shouldRefuseToDeleteAVideoInUseAndOfferArchivingInstead() throws Exception {
        // The client's history must keep the demonstration that went with a session they already did.
        String admin = adminToken();
        String videoId = addVideo(admin, "Przysiad", "https://youtu.be/" + ID);
        UUID clientId = flagClient(admin);

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[{"kind":"VIDEO","videoId":"%s"}]}"""
                                .formatted(TOMORROW, videoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].videoName").value("Przysiad"));

        mockMvc.perform(delete("/api/admin/exercise-videos/" + videoId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        // Archiving hides it from the picker but leaves the training intact
        mockMvc.perform(post("/api/admin/exercise-videos/" + videoId + "/archive")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/api/admin/exercise-videos/search?query=przysiad")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings[0].attachments.length()").value(1));
    }

    @Test
    void shouldTreatMissingAttachmentsAsLeaveAloneAndEmptyAsClear() throws Exception {
        // The three-way contract. A re-date sends the whole training; treating the absent list as
        // "clear" would silently drop materials the edit never touched.
        String admin = adminToken();
        String videoId = addVideo(admin, "Przysiad", "https://youtu.be/" + ID);
        UUID clientId = flagClient(admin);

        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[{"kind":"VIDEO","videoId":"%s"}]}"""
                                .formatted(TOMORROW, videoId)))
                .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");
        int version = JsonPath.read(created, "$.version");

        // No attachments key at all -> untouched
        String moved = mockMvc.perform(put("/api/admin/personal-trainings/" + trainingId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","version":%d}"""
                                .formatted(TOMORROW.plusDays(1), version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        int nextVersion = JsonPath.read(moved, "$.version");

        // Explicit empty list -> cleared
        mockMvc.perform(put("/api/admin/personal-trainings/" + trainingId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[],"version":%d}"""
                                .formatted(TOMORROW.plusDays(1), nextVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(0));
    }

    @Test
    void shouldCarryMaterialsOntoCopiesAndDuplicates() throws Exception {
        // The videos ARE the plan — a copied session that arrives as a bare title is not the session.
        String admin = adminToken();
        String videoId = addVideo(admin, "Przysiad", "https://youtu.be/" + ID);
        UUID clientId = flagClient(admin);
        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[
                              {"kind":"VIDEO","videoId":"%s"},
                              {"kind":"LINK","url":"https://plan.example","label":"Rozpiska"}]}"""
                                .formatted(TOMORROW, videoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/admin/personal-trainings/" + trainingId + "/duplicate")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(2))
                // A VIDEO copy points back at the same library row, so a later rename still fixes both
                .andExpect(jsonPath("$.attachments[0].videoId").value(videoId))
                .andExpect(jsonPath("$.attachments[1].label").value("Rozpiska"));

        mockMvc.perform(post("/api/admin/personal-trainings/paste")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sourceId":"%s","targetDate":"%s","mode":"COPY"}"""
                                .formatted(trainingId, TOMORROW.plusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(2));
    }

    @Test
    void shouldRejectMoreThanThreeMaterials() throws Exception {
        String admin = adminToken();
        UUID clientId = flagClient(admin);

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[
                              {"kind":"LINK","url":"https://a.example"},
                              {"kind":"LINK","url":"https://b.example"},
                              {"kind":"LINK","url":"https://c.example"},
                              {"kind":"LINK","url":"https://d.example"}]}""".formatted(TOMORROW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectLinksThatCouldNotBeSafelyPutInAnHref() throws Exception {
        String admin = adminToken();
        UUID clientId = flagClient(admin);

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła","attachments":[
                              {"kind":"LINK","url":"javascript:alert(1)"}]}""".formatted(TOMORROW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepTheLibraryOutOfReachForClients() throws Exception {
        String client = createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);

        mockMvc.perform(get("/api/admin/exercise-videos").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldIgnoreMaterialsAClientSendsWithTheirOwnTraining() throws Exception {
        // Hiding the picker kept the rule in the form alone: a request built by hand named a clip
        // by id and the attachment layer looked it up without asking who was calling. Nothing leaks
        // that way — a client can only name a clip already shown to them — but a clip they pin can
        // no longer be deleted from the library, and the coach cannot see where it is being used.
        String admin = adminToken();
        String videoId = addVideo(admin, "Przysiad", "https://youtu.be/" + ID);
        UUID clientId = flagClient(admin);
        String client = createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);

        mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sam sobie","attachments":[
                              {"kind":"VIDEO","videoId":"%s"},
                              {"kind":"LINK","url":"https://example.test/plan","label":"Rozpiska"}]}"""
                                .formatted(TOMORROW, videoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(0));

        // Still unused, so the coach can retire it
        mockMvc.perform(delete("/api/admin/exercise-videos/" + videoId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        // The client's own entry is otherwise untouched — only the materials were dropped
        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings[0].title").value("Sam sobie"));
    }

    @Test
    void shouldLeaveTheCoachsMaterialsAloneWhenTheClientEditsTheTrainingAroundThem() throws Exception {
        // Why the client's list is IGNORED rather than refused. The form always sends the whole
        // list, hidden section or not, so re-dating a training the coach had attached a clip to
        // sends that clip's id straight back; an error there would break an edit that has nothing
        // to do with materials. Neither may the client clear them by sending an empty list.
        String admin = adminToken();
        String videoId = addVideo(admin, "Przysiad", "https://youtu.be/" + ID);
        UUID clientId = flagClient(admin);
        String client = createUserAndGetToken("client@kdtraining.test", "Ala", "Testowa", UserRole.USER);

        String created = mockMvc.perform(post("/api/user/my-training/trainings")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sam sobie"}""".formatted(TOMORROW)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");
        int version = JsonPath.read(created, "$.version");

        // The coach may reshape anything on this calendar, including what the client logged
        String equipped = mockMvc.perform(put("/api/admin/personal-trainings/" + trainingId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sam sobie","version":%d,
                             "attachments":[{"kind":"VIDEO","videoId":"%s"}]}"""
                                .formatted(TOMORROW, version, videoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        int equippedVersion = JsonPath.read(equipped, "$.version");

        String moved = mockMvc.perform(put("/api/user/my-training/trainings/" + trainingId)
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sam sobie","version":%d,
                             "attachments":[{"kind":"VIDEO","videoId":"%s"}]}"""
                                .formatted(TOMORROW.plusDays(1), equippedVersion, videoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].videoName").value("Przysiad"))
                .andReturn().getResponse().getContentAsString();
        int movedVersion = JsonPath.read(moved, "$.version");

        mockMvc.perform(put("/api/user/my-training/trainings/" + trainingId)
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Sam sobie","version":%d,"attachments":[]}"""
                                .formatted(TOMORROW.plusDays(1), movedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1));
    }

    @Test
    void shouldKeepTemplateEditsFromRewritingTrainingsAlreadyHandedOut() throws Exception {
        // A template is a starting point, not a live link: a session someone already did must not
        // change because the coach renamed the template afterwards.
        String admin = adminToken();
        String json = mockMvc.perform(post("/api/admin/training-templates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"title":"Siła A","description":"5x5","defaultDurationMinutes":90}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String templateId = JsonPath.read(json, "$.id");

        UUID clientId = flagClient(admin);
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"date":"%s","title":"Siła A","description":"5x5"}""".formatted(TOMORROW)));

        mockMvc.perform(put("/api/admin/training-templates/" + templateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"title":"Siła A v2","description":"3x8"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/personal-trainings?athleteId=" + clientId
                        + "&from=" + TOMORROW + "&to=" + TOMORROW)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.trainings[0].title").value("Siła A"))
                .andExpect(jsonPath("$.trainings[0].description").value("5x5"));
    }
}
