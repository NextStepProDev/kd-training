package pl.kdtraining.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);


    @Test
    void shouldGetCurrentUserProfile() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("testuser@kdtraining.test"))
            .andExpect(jsonPath("$.firstName").value("User"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void shouldUpdateUserProfile() throws Exception {
        String token = userToken();
        mockMvc.perform(put("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Updated","lastName":"UserName","phone":"111222333"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    void shouldRejectUpdateWithInvalidData() throws Exception {
        String token = userToken();
        mockMvc.perform(put("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Ab","lastName":"C","phone":null}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldChangePassword() throws Exception {
        User user = new User("changepw-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("OldPassword123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"OldPassword123","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongCurrentPassword() throws Exception {
        User user = new User("wrongpw-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("CorrectPassword"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"WrongPassword","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().isBadRequest());
    }

    /**
     * A wrong current password counts against the login lockout. The count is written in its own
     * transaction: in the caller's it would be rolled back by the very refusal that follows it, and
     * the limit would never trip — which is why this goes through the real controller and database.
     */
    @Test
    void shouldLockTheAccountAfterFiveWrongCurrentPasswords() throws Exception {
        User user = new User("lockout-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("CorrectPassword"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/api/user/me/password")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"currentPassword":"WrongPassword","newPassword":"NewPassword456"}
                        """))
                .andExpect(status().is4xxClientError());
        }

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(5, stored.getFailedLoginAttempts());
        assertTrue(stored.isAccountLocked());

        // Locked: even the right password is refused now, like at login.
        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"CorrectPassword","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().isConflict());
        assertTrue(ENCODER.matches("CorrectPassword",
            userRepository.findById(user.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void shouldClearEarlierFailedAttemptsWhenTheCurrentPasswordIsRight() throws Exception {
        User user = new User("lockout-reset-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("CorrectPassword"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        user.incrementFailedLoginAttempts();
        user.incrementFailedLoginAttempts();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"CorrectPassword","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().is2xxSuccessful());

        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts());
    }

    @Test
    void shouldCountAWrongPasswordOnAccountDeletion() throws Exception {
        User user = new User("delete-wrong-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("DeleteMe123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Wrong\"}"))
            .andExpect(status().isBadRequest());

        assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts());
    }

    /**
     * The only admin's account takes every private note about every athlete with it
     * (admin_private_notes cascades on its author), and leaves nobody able to open the coach panel.
     */
    @Test
    void shouldRefuseToDeleteTheOnlyAdministratorAccount() throws Exception {
        User admin = new User("only-admin-it@test.com", "Kacper", "Trener", null);
        admin.setPasswordHash(ENCODER.encode("DeleteMe123"));
        admin.setRole(UserRole.ADMIN);
        admin.markEmailVerified();
        userRepository.save(admin);
        String token = jwtService.generateAccessToken(admin);

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"DeleteMe123\"}"))
            .andExpect(status().isConflict());

        assertTrue(userRepository.findById(admin.getId()).isPresent());
    }

    /** An upload route hit without a multipart body is the caller's mistake: 400, not 500. */
    @Test
    void shouldAnswerBadRequestToAnAvatarUploadWithoutAMultipartBody() throws Exception {
        mockMvc.perform(post("/api/user/me/avatar")
                .header("Authorization", "Bearer " + userToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteAccount() throws Exception {
        User user = new User("delete-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("DeleteMe123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"DeleteMe123\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldDeleteTheAccountWhenSelfDeleting() throws Exception {
        User user = new User("erase-it@test.com", "Jan", "Kowalski", "123456789");
        user.setPasswordHash(ENCODER.encode("DeleteMe123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"DeleteMe123\"}"))
            .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(user.getId()).isEmpty());
    }

    @Test
    void shouldRecordMandatoryPrivacyConsentForOauthUser() throws Exception {
        User user = new User("consents-it@test.com", "Ola", "Google", null);
        user.setOauthProvider("google");
        user.setOauthId("oauth-consents-1");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        assertFalse(user.hasPrivacyAccepted());
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/user/me/consents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acceptedPrivacy\":true,\"acceptedTerms\":true,\"acceptedMarketing\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privacyAccepted").value(true))
            .andExpect(jsonPath("$.termsAccepted").value(true))
            .andExpect(jsonPath("$.marketingConsent").value(true));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(refreshed.hasPrivacyAccepted());
        assertTrue(refreshed.hasMarketingConsent());
    }

    @Test
    void shouldRejectConsentsWhenMandatoryPrivacyDeclined() throws Exception {
        User user = new User("consents-decline-it@test.com", "Ola", "Google", null);
        user.setOauthProvider("google");
        user.setOauthId("oauth-consents-2");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/user/me/consents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acceptedPrivacy\":false,\"acceptedTerms\":false,\"acceptedMarketing\":false}"))
            .andExpect(status().isBadRequest());

        // Privacy policy is mandatory: declining must not save any consent.
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(refreshed.hasPrivacyAccepted());
        assertFalse(refreshed.hasTermsAccepted());
        assertFalse(refreshed.hasMarketingConsent());
    }

    @Test
    void shouldReturnAdminProfileForAdminUser() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.isAdmin").value(true));
    }
}
