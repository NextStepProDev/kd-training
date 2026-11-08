package pl.kdtraining.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.invitation.Invitation;
import pl.kdtraining.domain.invitation.InvitationRepository;
import pl.kdtraining.domain.user.User;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InvitationRepository invitationRepository;

    /** An open invitation for the address, as the coach's panel would create it; returns the link's token. */
    private String invite(String email) {
        String token = "invite-" + email;
        invitationRepository.save(new Invitation(email, jwtService.hashToken(token), Instant.now().plusSeconds(3600)));
        return token;
    }

    private ResultActions register(String token, String firstName) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"invitationToken":"%s","password":"StrongPass123","firstName":"%s","lastName":"Kowalski","acceptedPrivacy":true,"acceptedTerms":true}
                """.formatted(token, firstName)));
    }

    @Test
    void shouldRegisterAnInvitedUserReadyToLogIn() throws Exception {
        String token = invite("newuser-register@test.com");

        register(token, "Jan")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        User user = userRepository.findByEmail("newuser-register@test.com").orElseThrow();
        assertTrue(user.isEmailVerified(), "the invitation link proved the mailbox");
        assertEquals(0, invitationRepository.count(), "the invitation is used up");

        // No verification step: the password works straight away.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"newuser-register@test.com","password":"StrongPass123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void shouldRefuseRegistrationWithoutAnInvitation() throws Exception {
        register("made-up-token", "Jan")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertEquals(0, userRepository.count());
    }

    @Test
    void shouldRefuseAnExpiredInvitation() throws Exception {
        invitationRepository.save(new Invitation("late@test.com", jwtService.hashToken("late"), Instant.now().minusSeconds(1)));

        register("late", "Jan").andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail("late@test.com"));
    }

    @Test
    void shouldNotLetOneInvitationCreateTwoAccounts() throws Exception {
        String token = invite("dup@test.com");
        register(token, "Jan").andExpect(status().isOk());

        register(token, "Anna")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertEquals("Jan", userRepository.findByEmail("dup@test.com").orElseThrow().getFirstName());
    }

    @Test
    void shouldRejectRegistrationWhenTermsNotAccepted() throws Exception {
        String token = invite("no-terms@test.com");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"invitationToken":"%s","password":"StrongPass123","firstName":"Jan","lastName":"Kowalski","acceptedPrivacy":true,"acceptedTerms":false}
                    """.formatted(token)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertFalse(userRepository.existsByEmail("no-terms@test.com"));
    }

    @Test
    void shouldRejectInvalidRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"invitationToken":"","password":"short","firstName":"","lastName":"","acceptedPrivacy":true,"acceptedTerms":true}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldShowTheAddressBehindAnInvitationLink() throws Exception {
        String token = invite("shown@test.com");

        mockMvc.perform(post("/api/auth/invitation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"%s"}
                    """.formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("shown@test.com"));
    }

    @Test
    void shouldRefuseToReadADeadInvitationLink() throws Exception {
        mockMvc.perform(post("/api/auth/invitation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"nothing-here"}
                    """))
            .andExpect(status().isBadRequest());
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
    void shouldNoLongerServeTheVerificationEndpoints() throws Exception {
        // Accounts come from invitations (and Google) and are created verified; the old confirm-your-
        // address flow is gone, and a stale link from an old mail must not find a live endpoint.
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@test.com\"}"))
            .andExpect(status().isNotFound());
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

        // A well-formed token that was never stored — the shape of one that was logged out (the row is
        // deleted) or wiped by "log out everywhere". A rotated one past its grace window is refused
        // too, but also ends every session — see shouldRevokeEverySessionWhenARotatedRefreshTokenIsReplayed.
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

    /**
     * Logging out deletes the row. Marking it used left it refreshable for the 30-second rotation
     * grace window, so the session outlived the click on "log out".
     */
    @Test
    void shouldRefuseARefreshTokenRightAfterLogout() throws Exception {
        User user = verifiedUser("logout-grace@test.com");
        String refreshToken = storedRefreshToken(user);

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    /**
     * A rotated token presented again after the grace window is either a stolen copy or the owner
     * coming back after the thief rotated it — every session of that person ends.
     */
    @Test
    void shouldRevokeEverySessionWhenARotatedRefreshTokenIsReplayed() throws Exception {
        User user = verifiedUser("reuse@test.com");
        String rotated = storedRefreshToken(user);
        String otherSession = storedRefreshToken(user);
        refresh(rotated).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE auth_tokens SET used_at = now() - interval '1 minute' WHERE token_hash = ?",
            jwtService.hashToken(rotated));

        refresh(rotated)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        refresh(otherSession).andExpect(status().isUnauthorized());
        // Including the pair the first rotation issued: nothing of this person's survives.
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM auth_tokens WHERE user_id = ? AND token_type = 'REFRESH_TOKEN'",
            Integer.class, user.getId()));
    }

    /**
     * Two tabs share one refresh token and race to rotate it; the loser comes in seconds later with
     * the rotated one. Inside the grace window that is not reuse and must end nothing.
     */
    @Test
    void shouldNotTreatAReplayInsideTheGraceWindowAsReuse() throws Exception {
        User user = verifiedUser("grace-tabs@test.com");
        String shared = storedRefreshToken(user);
        String otherSession = storedRefreshToken(user);
        refresh(shared).andExpect(status().isOk());

        refresh(shared).andExpect(status().isOk());

        refresh(otherSession).andExpect(status().isOk());
    }

    @Test
    void shouldKeepOtherSessionsWhenTheRefreshTokenIsMerelyUnknown() throws Exception {
        User user = verifiedUser("unknown-token@test.com");
        String otherSession = storedRefreshToken(user);

        refresh(jwtService.generateRefreshToken(user)).andExpect(status().isUnauthorized());

        refresh(otherSession).andExpect(status().isOk());
    }

    private User verifiedUser(String email) {
        User user = new User(email, "Jan", "Kowalski", null);
        user.setPasswordHash("hash");
        user.markEmailVerified();
        return userRepository.save(user);
    }

    private String storedRefreshToken(User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        authTokenRepository.save(new AuthToken(user, jwtService.hashToken(refreshToken), TokenType.REFRESH_TOKEN,
            Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs())));
        return refreshToken;
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    @Test
    void shouldAutoPromoteAdminEmailOnRegister() throws Exception {
        String token = invite("admin@kdtraining.test");

        register(token, "Admin").andExpect(status().isOk());

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
