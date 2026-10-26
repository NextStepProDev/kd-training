package pl.kdtraining.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.user.User;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Test
    void shouldRegisterNewUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "newuser-register@test.com",
                        "password": "StrongPass123",
                        "firstName": "Jan",
                        "lastName": "Kowalski",
                        "acceptedPrivacy": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        assertTrue(userRepository.existsByEmail("newuser-register@test.com"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"dup@test.com","password":"StrongPass123","firstName":"Jan","lastName":"Kowalski","acceptedPrivacy":true}
                """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"dup@test.com","password":"StrongPass123","firstName":"Anna","lastName":"Nowak","acceptedPrivacy":true}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectInvalidRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invalid","password":"short","firstName":"","lastName":"","acceptedPrivacy":true}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldLoginWithVerifiedUser() throws Exception {
        User user = new User("logintest@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode("TestPassword123"));
        user.markEmailVerified();
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"logintest@test.com","password":"TestPassword123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        User user = new User("wrongpw@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode("CorrectPassword123"));
        user.markEmailVerified();
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"wrongpw@test.com","password":"WrongPassword123"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectLoginWithUnverifiedEmail() throws Exception {
        User user = new User("unverified@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode("Password123"));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"unverified@test.com","password":"Password123"}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldVerifyEmail() throws Exception {
        User user = new User("verify@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash("hash");
        userRepository.save(user);

        String rawToken = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(rawToken);
        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.EMAIL_VERIFICATION,
            Instant.now().plusSeconds(900));
        authTokenRepository.save(authToken);

        mockMvc.perform(post("/api/auth/verify-email")
                .param("token", rawToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        User verified = userRepository.findByEmail("verify@test.com").orElseThrow();
        assertTrue(verified.isEmailVerified());
    }

    @Test
    void shouldRejectInvalidVerificationToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                .param("token", "invalid-token"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefreshTokens() throws Exception {
        User user = new User("refresh@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash("hash");
        user.markEmailVerified();
        userRepository.save(user);

        String refreshToken = jwtService.generateRefreshToken(user);
        String refreshHash = jwtService.hashToken(refreshToken);
        AuthToken storedToken = new AuthToken(user, refreshHash, TokenType.REFRESH_TOKEN,
            Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        authTokenRepository.save(storedToken);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    /**
     * The status matters as much as the rejection. The frontend ends a session on 401/403 and on
     * nothing else — deliberately, so that a 429 from the rate limiter or a 502 mid-deploy cannot log
     * out a valid account. A refused refresh token answered with 400 would land in that same
     * "transient" bucket, and the app would keep insisting the user was logged in while every request
     * failed, with no way back but clearing storage by hand.
     */
    @Test
    void shouldAnswerUnauthorizedWhenTheRefreshTokenWasRevoked() throws Exception {
        User user = new User("revoked@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash("hash");
        user.markEmailVerified();
        userRepository.save(user);

        // A well-formed token that was never stored — the shape of one that was logged out or rotated
        // past its grace window.
        String refreshToken = jwtService.generateRefreshToken(user);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void shouldAnswerUnauthorizedWhenTheRefreshTokenIsNotEvenAToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"not-a-jwt\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void shouldLogout() throws Exception {
        User user = new User("logout@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash("hash");
        user.markEmailVerified();
        userRepository.save(user);

        String refreshToken = jwtService.generateRefreshToken(user);
        String refreshHash = jwtService.hashToken(refreshToken);
        AuthToken storedToken = new AuthToken(user, refreshHash, TokenType.REFRESH_TOKEN,
            Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        authTokenRepository.save(storedToken);

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldAutoPromoteAdminEmailOnRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "admin@kdtraining.test",
                        "password": "StrongPass123",
                        "firstName": "Admin",
                        "lastName": "Fire",
                        "acceptedPrivacy": true
                    }
                    """))
            .andExpect(status().isOk());

        User admin = userRepository.findByEmail("admin@kdtraining.test").orElseThrow();
        assertEquals(pl.kdtraining.domain.user.UserRole.ADMIN, admin.getRole());
    }

    @Test
    void shouldHandleForgotPasswordForNonExistentEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent@test.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }
}
