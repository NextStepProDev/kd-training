package pl.kdtraining.api.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.mail.AuthMailService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End of cooperation (the client's answer "b albo c"): the coach blocks the account, the person loses
 * every way in, and the data stays for a possible return.
 */
class AccountBlockingIntegrationTest extends BaseIntegrationTest {

    private static final String EMAIL = "client@kdtraining.test";
    private static final String PASSWORD = "TestPassword123";

    @MockitoBean private AuthMailService authMail;

    private UUID client() {
        User user = new User(EMAIL, "Ala", "Testowa", null);
        user.setPasswordHash(new BCryptPasswordEncoder(12).encode(PASSWORD));
        user.markEmailVerified();
        user.setAthlete(true);
        return userRepository.save(user).getId();
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"%s"}""".formatted(EMAIL, password)));
    }

    private void block(String admin, UUID id) throws Exception {
        mockMvc.perform(post("/api/admin/users/" + id + "/block").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void shouldRefuseLoginToABlockedAccountAndPointAtTheCoach() throws Exception {
        String admin = adminToken();
        block(admin, client());

        login(PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("To konto jest zablokowane. Skontaktuj się z trenerem."));
    }

    @Test
    void shouldNotTellAGuessingStrangerThatTheAccountIsBlocked() throws Exception {
        // The block is reported only past the password, like the lockout: a wrong password gets the
        // same answer as an address with no account.
        String admin = adminToken();
        block(admin, client());

        login("WrongPassword123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nieprawidłowy email lub hasło"));
    }

    @Test
    void shouldEndTheOpenSessionsAtOnce() throws Exception {
        String admin = adminToken();
        UUID id = client();
        String json = login(PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(json, "$.accessToken");
        String refresh = JsonPath.read(json, "$.refreshToken");
        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        block(admin, id);

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSendNoResetLinkToABlockedAccount() throws Exception {
        // A new password must not be a way back in; the answer stays the same as for anyone.
        String admin = adminToken();
        block(admin, client());

        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk());

        verify(authMail, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void shouldLetThePersonBackInAfterTheUnblock() throws Exception {
        String admin = adminToken();
        UUID id = client();
        block(admin, id);

        mockMvc.perform(delete("/api/admin/users/" + id + "/block").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));

        login(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldKeepTheBlockedClientOutOfTheRosterButKeepTheData() throws Exception {
        String admin = adminToken();
        UUID id = client();
        block(admin, id);

        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAthlete").value(true))
                .andExpect(jsonPath("$.blockedAt").isNotEmpty());
    }

    @Test
    void shouldRefuseToBlockOneselfOrAnotherAdmin() throws Exception {
        String admin = adminToken();
        createUserAndGetToken("second-admin@kdtraining.test", "Ewa", "Druga", UserRole.ADMIN);
        UUID other = userRepository.findByEmail("second-admin@kdtraining.test").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/" + adminUserId() + "/block").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/users/" + other + "/block").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotLetAClientBlockAnybody() throws Exception {
        String user = userToken();
        UUID id = client();

        mockMvc.perform(post("/api/admin/users/" + id + "/block").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
    }
}
