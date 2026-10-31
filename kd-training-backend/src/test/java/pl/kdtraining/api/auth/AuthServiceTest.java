package pl.kdtraining.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.kdtraining.api.auth.AuthDtos.*;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.mail.AuthMailService;
import pl.kdtraining.infrastructure.security.JwtService;
import pl.kdtraining.infrastructure.security.PasswordPolicyValidator;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthMailService authMailService;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private MessageService msg;
    @Mock private PasswordPolicyValidator passwordPolicy;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User existingUser;

    @BeforeEach
    void setUp() throws Exception {
        registerRequest = new RegisterRequest(
            "new@example.com", "Password123", "Jan", "Kowalski", null, null, true, true, false
        );

        loginRequest = new LoginRequest("test@example.com", "Password123");

        existingUser = new User("test@example.com", "Jan", "Kowalski", null);
        setId(existingUser, UUID.randomUUID());
        existingUser.setPasswordHash("encoded-password");
        existingUser.markEmailVerified();
    }

    // --- Register ---

    @Test
    void shouldRegisterNewUser() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(adminEmailConfig.isAdminEmail("new@example.com")).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("secure-token");
        when(jwtService.hashToken("secure-token")).thenReturn("hashed-token");
        when(msg.get("auth.register.success")).thenReturn("Rejestracja zakończona");

        MessageResponse result = authService.register(registerRequest);

        assertNotNull(result);
        assertEquals("Rejestracja zakończona", result.message());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("Jan", saved.getFirstName());
        assertEquals(UserRole.USER, saved.getRole());
        assertTrue(saved.hasPrivacyAccepted());
        assertTrue(saved.hasTermsAccepted(), "the acceptance moment is the proof — it must be stored");

        verify(authMailService).sendVerificationEmail(any(User.class), eq("secure-token"));
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(true);
        when(msg.get("auth.email.exists")).thenReturn("Email zajęty");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        assertEquals("Email zajęty", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldValidatePasswordPolicyBeforeTouchingTheDatabase() {
        // Given: the policy check (which calls Have I Been Pwned over the network) rejects the password
        doThrow(new IllegalArgumentException("Hasło z wycieku"))
            .when(passwordPolicy).validate("Password123", "new@example.com", "Jan", "Kowalski");

        // When
        var ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));

        // Then: it ran before any repository call, so the slow network wait never holds a DB connection
        assertEquals("Hasło z wycieku", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldReportDuplicateEmailWhenTwoRegistrationsRace() {
        // Given: another request saved the same address between the existence check and this save
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(adminEmailConfig.isAdminEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        when(msg.get("auth.email.exists")).thenReturn("Email zajęty");

        // When / Then: the UNIQUE violation surfaces as the ordinary 400, not a 500
        var ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        assertEquals("Email zajęty", ex.getMessage());
        verify(authMailService, never()).sendVerificationEmail(any(User.class), anyString());
    }

    @Test
    void shouldAutoPromoteAdminOnRegister() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(adminEmailConfig.isAdminEmail("new@example.com")).thenReturn(true);
        when(jwtService.generateSecureToken()).thenReturn("token");
        when(jwtService.hashToken("token")).thenReturn("hash");
        when(msg.get("auth.register.success")).thenReturn("ok");

        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(UserRole.ADMIN, captor.getValue().getRole());
    }

    @Test
    void shouldDefaultLanguageToPlWhenNull() {
        var request = new RegisterRequest("new@example.com", "Password123", "Jan", "Kowalski", null, null, true, true, false);
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(adminEmailConfig.isAdminEmail(anyString())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("t");
        when(jwtService.hashToken("t")).thenReturn("h");
        when(msg.get("auth.register.success")).thenReturn("ok");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("pl", captor.getValue().getPreferredLanguage());
    }

    @Test
    void shouldDefaultLanguageToPlWhenUnsupported() {
        var request = new RegisterRequest("new@example.com", "Password123", "Jan", "Kowalski", null, "de", true, true, false);
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(adminEmailConfig.isAdminEmail(anyString())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("t");
        when(jwtService.hashToken("t")).thenReturn("h");
        when(msg.get("auth.register.success")).thenReturn("ok");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("pl", captor.getValue().getPreferredLanguage());
    }

    // --- Login ---

    @Test
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("refresh-jwt");
        when(jwtService.hashToken("refresh-jwt")).thenReturn("refresh-hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthTokensResponse result = authService.login(loginRequest);

        assertEquals("access-jwt", result.accessToken());
        assertEquals("refresh-jwt", result.refreshToken());
        assertEquals(900L, result.expiresIn());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        when(msg.get("auth.login.invalid")).thenReturn("Nieprawidłowe dane");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals("Nieprawidłowe dane", ex.getMessage());
    }

    @Test
    void shouldThrowWhenOAuthUserTriesToLoginWithPassword() {
        existingUser.setPasswordHash(null);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(msg.get("auth.login.oauth")).thenReturn("Zaloguj przez OAuth");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals("Zaloguj przez OAuth", ex.getMessage());
    }

    /** The owner — someone who knows the password — is told why they cannot get in. */
    @Test
    void shouldThrowWhenAccountLocked() throws Exception {
        existingUser.setPasswordHash("encoded-password");
        lockAccount(existingUser);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(msg.get(eq("auth.login.locked"), anyLong())).thenReturn("Konto zablokowane");

        var ex = assertThrows(IllegalStateException.class, () -> authService.login(loginRequest));
        assertEquals("Konto zablokowane", ex.getMessage());
    }

    /**
     * The oracle this endpoint used to be. Five wrong guesses locked an existing account, and the
     * sixth then answered "locked for N minutes" while an address with no account kept answering
     * "invalid" — so anyone could ask whether a given person had signed up. Every other
     * anti-enumeration path in this service is deliberate; this one has to match them.
     */
    @Test
    void shouldNotRevealALockedAccountToSomebodyWhoDoesNotKnowThePassword() throws Exception {
        existingUser.setPasswordHash("encoded-password");
        lockAccount(existingUser);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(false);
        when(msg.get("auth.login.invalid")).thenReturn("Nieprawidłowe dane");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals("Nieprawidłowe dane", ex.getMessage());
        verify(msg, never()).get(eq("auth.login.locked"), anyLong());
    }

    /**
     * The other half of the same claim: an unknown address must be answered exactly as the locked
     * account above. Asserted together, because "they are the same" is the property — either one
     * alone can drift.
     */
    @Test
    void shouldAnswerAnUnknownAddressExactlyAsALockedOne() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        when(msg.get("auth.login.invalid")).thenReturn("Nieprawidłowe dane");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals("Nieprawidłowe dane", ex.getMessage());
    }

    /** A locked account does not count the attempt again — the window cannot be stacked past 15 min. */
    @Test
    void shouldNotExtendTheLockoutOnFurtherAttempts() throws Exception {
        existingUser.setPasswordHash("encoded-password");
        lockAccount(existingUser);
        Instant lockedUntilBefore = existingUser.getLockedUntil();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(false);
        when(msg.get("auth.login.invalid")).thenReturn("Nieprawidłowe dane");

        assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));
        assertEquals(lockedUntilBefore, existingUser.getLockedUntil());
        verify(userRepository, never()).save(any());
    }

    private static void lockAccount(User user) throws Exception {
        Field lockedField = User.class.getDeclaredField("lockedUntil");
        lockedField.setAccessible(true);
        lockedField.set(user, Instant.now().plusSeconds(600));
    }

    @Test
    void shouldIncrementFailedAttemptsOnWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(false);
        when(msg.get("auth.login.invalid")).thenReturn("Nieprawidłowe dane");

        assertThrows(IllegalArgumentException.class, () -> authService.login(loginRequest));

        verify(userRepository).save(existingUser);
        assertEquals(1, existingUser.getFailedLoginAttempts());
    }

    @Test
    void shouldLockAccountAfterFiveFailedAttempts() throws Exception {
        Field attemptsField = User.class.getDeclaredField("failedLoginAttempts");
        attemptsField.setAccessible(true);
        attemptsField.set(existingUser, 4);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(false);
        when(msg.get(eq("auth.login.locked"), anyLong())).thenReturn("Konto zablokowane");

        assertThrows(IllegalStateException.class, () -> authService.login(loginRequest));
        assertTrue(existingUser.isAccountLocked());
    }

    @Test
    void shouldThrowWhenEmailNotVerified() {
        existingUser.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(msg.get("auth.email.not.verified")).thenReturn("Email niezweryfikowany");

        var ex = assertThrows(IllegalStateException.class, () -> authService.login(loginRequest));
        assertEquals("Email niezweryfikowany", ex.getMessage());
    }

    @Test
    void shouldResetFailedAttemptsOnSuccessfulLogin() throws Exception {
        Field attemptsField = User.class.getDeclaredField("failedLoginAttempts");
        attemptsField.setAccessible(true);
        attemptsField.set(existingUser, 3);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("refresh");
        when(jwtService.hashToken("refresh")).thenReturn("hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        authService.login(loginRequest);

        assertEquals(0, existingUser.getFailedLoginAttempts());
        assertNull(existingUser.getLockedUntil());
    }

    @Test
    void shouldAutoPromoteAdminOnLogin() {
        existingUser.setRole(UserRole.USER);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(adminEmailConfig.isAdminEmail("test@example.com")).thenReturn(true);
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("refresh");
        when(jwtService.hashToken("refresh")).thenReturn("hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        authService.login(loginRequest);

        assertEquals(UserRole.ADMIN, existingUser.getRole());
    }

    // --- Verify Email ---

    @Test
    void shouldVerifyEmailSuccessfully() {
        AuthToken authToken = new AuthToken(existingUser, "hash", TokenType.EMAIL_VERIFICATION, Instant.now().plusSeconds(600));
        existingUser.setEmailVerified(false);

        when(jwtService.hashToken("raw-token")).thenReturn("hash");
        when(authTokenRepository.findValidToken(eq("hash"), eq(TokenType.EMAIL_VERIFICATION), any())).thenReturn(Optional.of(authToken));
        when(msg.get("auth.verify.success")).thenReturn("Zweryfikowano");

        MessageResponse result = authService.verifyEmail("raw-token");

        assertEquals("Zweryfikowano", result.message());
        assertTrue(existingUser.isEmailVerified());
        assertTrue(authToken.isUsed());
        verify(authMailService).sendWelcomeEmail(existingUser);
        verify(authMailService).sendNewUserAdminNotification(existingUser);
    }

    @Test
    void shouldThrowWhenVerificationTokenInvalid() {
        when(jwtService.hashToken("bad-token")).thenReturn("bad-hash");
        when(authTokenRepository.findValidToken(eq("bad-hash"), eq(TokenType.EMAIL_VERIFICATION), any())).thenReturn(Optional.empty());
        when(msg.get("auth.verify.invalid")).thenReturn("Token nieprawidłowy");

        var ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("bad-token"));
        assertEquals("Token nieprawidłowy", ex.getMessage());
    }

    // --- Resend Verification ---

    @Test
    void shouldResendVerificationEmail() {
        existingUser.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(existingUser.getId()), eq(TokenType.EMAIL_VERIFICATION), any())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("token");
        when(jwtService.hashToken("token")).thenReturn("hash");
        when(msg.get("auth.resend.success")).thenReturn("Wysłano");

        MessageResponse result = authService.resendVerification(new ResendVerificationRequest("test@example.com"));

        assertEquals("Wysłano", result.message());
        verify(authMailService).sendVerificationEmail(existingUser, "token");
    }

    @Test
    void shouldReturnSuccessForNonExistentEmailOnResend() {
        when(userRepository.findByEmailIgnoreCase("none@example.com")).thenReturn(Optional.empty());
        when(msg.get("auth.resend.success")).thenReturn("Wysłano");

        MessageResponse result = authService.resendVerification(new ResendVerificationRequest("none@example.com"));

        assertEquals("Wysłano", result.message());
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldReturnGenericSuccessWhenAlreadyVerified() {
        // Uniform message (anti-enumeration) — we do not reveal that the account is already verified,
        // and we do not send a mail.
        existingUser.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(msg.get("auth.resend.success")).thenReturn("Wysłano");

        MessageResponse result = authService.resendVerification(new ResendVerificationRequest("test@example.com"));

        assertEquals("Wysłano", result.message());
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldReturnGenericSuccessWhenResendCooldownActive() {
        // The cooldown no longer throws — it returns the same success, just without sending a mail.
        existingUser.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(existingUser.getId()), eq(TokenType.EMAIL_VERIFICATION), any())).thenReturn(true);
        when(msg.get("auth.resend.success")).thenReturn("Wysłano");

        MessageResponse result = authService.resendVerification(new ResendVerificationRequest("test@example.com"));

        assertEquals("Wysłano", result.message());
        verify(authMailService, never()).sendVerificationEmail(any(), any());
    }

    // --- Forgot Password ---

    @Test
    void shouldSendPasswordResetEmail() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(existingUser.getId()), eq(TokenType.PASSWORD_RESET), any())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("reset-token");
        when(jwtService.hashToken("reset-token")).thenReturn("reset-hash");
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");

        MessageResponse result = authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));

        assertEquals("Wysłano reset", result.message());
        verify(authMailService).sendPasswordResetEmail(existingUser, "reset-token");
    }

    @Test
    void shouldReturnSuccessForNonExistentEmailOnForgotPassword() {
        when(userRepository.findByEmailIgnoreCase("none@example.com")).thenReturn(Optional.empty());
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");

        MessageResponse result = authService.forgotPassword(new ForgotPasswordRequest("none@example.com"));

        assertEquals("Wysłano reset", result.message());
        verify(authMailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void shouldReturnSuccessForOAuthUserOnForgotPassword() {
        existingUser.setPasswordHash(null);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");

        MessageResponse result = authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));

        assertEquals("Wysłano reset", result.message());
        verify(authMailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void shouldReturnGenericSuccessWhenForgotPasswordCooldownActive() {
        // The cooldown no longer throws — it returns the same success, just without sending a mail.
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(existingUser.getId()), eq(TokenType.PASSWORD_RESET), any())).thenReturn(true);
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");

        MessageResponse result = authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));

        assertEquals("Wysłano reset", result.message());
        verify(authMailService, never()).sendPasswordResetEmail(any(), any());
    }

    // --- Per-address mail quota ---

    /**
     * Both flows mail a stranger on request, so the hourly allowance belongs to the recipient, not to
     * the endpoint — otherwise emptying one and moving to the other doubles what lands in the inbox.
     * The key is normalised, or changing the capitalisation would buy a fresh allowance.
     */
    @Test
    void shouldStopMailingAnAddressOnceItsHourlyQuotaIsSpent() {
        existingUser.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(existingUser));
        when(authTokenRepository.hasRecentUnusedToken(eq(existingUser.getId()), any(), any())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("reset-token");
        when(jwtService.hashToken("reset-token")).thenReturn("reset-hash");
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");
        when(msg.get("auth.resend.success")).thenReturn("Wysłano");

        // Given: three reset requests for the same address use up the allowance
        for (int i = 0; i < 3; i++) {
            authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));
        }
        verify(authMailService, times(3)).sendPasswordResetEmail(eq(existingUser), anyString());

        // When: a fourth request arrives, and the flooder switches flow and capitalisation
        MessageResponse fourth = authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));
        MessageResponse resend = authService.resendVerification(new ResendVerificationRequest("TEST@Example.com "));

        // Then: no further mail leaves — and the caller is told exactly what it was told the first time,
        // because a "you have had enough" answer would reveal that the account exists
        verify(authMailService, times(3)).sendPasswordResetEmail(any(), anyString());
        verify(authMailService, never()).sendVerificationEmail(any(), anyString());
        assertEquals("Wysłano reset", fourth.message());
        assertEquals("Wysłano", resend.message());
    }

    @Test
    void shouldKeepTheQuotaPerAddress() throws Exception {
        User other = new User("other@example.com", "Anna", "Nowak", null);
        setId(other, UUID.randomUUID());
        other.setPasswordHash("encoded-password");
        other.markEmailVerified();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(other));
        when(authTokenRepository.hasRecentUnusedToken(any(), eq(TokenType.PASSWORD_RESET), any())).thenReturn(false);
        when(jwtService.generateSecureToken()).thenReturn("reset-token");
        when(jwtService.hashToken("reset-token")).thenReturn("reset-hash");
        when(msg.get("auth.forgot.success")).thenReturn("Wysłano reset");

        // Given: one address is flooded past its allowance
        for (int i = 0; i < 5; i++) {
            authService.forgotPassword(new ForgotPasswordRequest("test@example.com"));
        }

        // When: somebody else asks for a reset
        authService.forgotPassword(new ForgotPasswordRequest("other@example.com"));

        // Then: they get their mail — one flooded inbox must not lock everyone else out of resets
        verify(authMailService).sendPasswordResetEmail(eq(other), anyString());
    }

    // --- Reset Password ---

    @Test
    void shouldResetPasswordSuccessfully() {
        AuthToken authToken = new AuthToken(existingUser, "hash", TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        when(jwtService.hashToken("reset-token")).thenReturn("hash");
        when(authTokenRepository.findValidToken(eq("hash"), eq(TokenType.PASSWORD_RESET), any())).thenReturn(Optional.of(authToken));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded");
        when(msg.get("auth.reset.success")).thenReturn("Hasło zmienione");

        MessageResponse result = authService.resetPassword(new ResetPasswordRequest("reset-token", "NewPassword123"));

        assertEquals("Hasło zmienione", result.message());
        assertEquals("new-encoded", existingUser.getPasswordHash());
        assertTrue(authToken.isUsed());
        verify(authTokenRepository).deleteByUserIdAndTokenType(existingUser.getId(), TokenType.REFRESH_TOKEN);
        verify(authMailService).sendPasswordChangedNotification(existingUser);
    }

    @Test
    void shouldThrowWhenResetTokenInvalid() {
        when(jwtService.hashToken("bad")).thenReturn("bad-hash");
        when(authTokenRepository.findValidToken(eq("bad-hash"), eq(TokenType.PASSWORD_RESET), any())).thenReturn(Optional.empty());
        when(msg.get("auth.reset.invalid")).thenReturn("Token wygasł");

        assertThrows(IllegalArgumentException.class,
            () -> authService.resetPassword(new ResetPasswordRequest("bad", "NewPass123")));
    }

    // --- Refresh Tokens ---

    @Test
    void shouldRefreshTokensSuccessfully() {
        AuthToken storedToken = new AuthToken(existingUser, "old-hash", TokenType.REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtService.validateToken("old-refresh")).thenReturn(true);
        when(jwtService.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtService.hashToken("old-refresh")).thenReturn("old-hash");
        when(authTokenRepository.findRefreshableToken(eq("old-hash"), eq(TokenType.REFRESH_TOKEN), any(), any())).thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("new-refresh");
        when(jwtService.hashToken("new-refresh")).thenReturn("new-hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthTokensResponse result = authService.refreshTokens(new RefreshTokenRequest("old-refresh"));

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        assertTrue(storedToken.isUsed());
    }

    @Test
    void shouldRefreshWithAlreadyUsedTokenWithinGraceWindowWithoutReRotating() {
        AuthToken storedToken = new AuthToken(existingUser, "old-hash", TokenType.REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        storedToken.markAsUsed();
        Instant firstUsedAt = storedToken.getUsedAt();
        when(jwtService.validateToken("old-refresh")).thenReturn(true);
        when(jwtService.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtService.hashToken("old-refresh")).thenReturn("old-hash");
        when(authTokenRepository.findRefreshableToken(eq("old-hash"), eq(TokenType.REFRESH_TOKEN), any(), any())).thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(existingUser)).thenReturn("new-refresh");
        when(jwtService.hashToken("new-refresh")).thenReturn("new-hash");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthTokensResponse result = authService.refreshTokens(new RefreshTokenRequest("old-refresh"));

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        // Grace window counts from the first rotation — usedAt must not be refreshed
        assertEquals(firstUsedAt, storedToken.getUsedAt());
        // Only the new token gets saved; the already-used one is not re-rotated
        verify(authTokenRepository, never()).save(storedToken);
        verify(authTokenRepository, times(1)).save(any(AuthToken.class));
    }

    @Test
    void shouldThrowWhenRefreshTokenInvalid() {
        when(jwtService.validateToken("bad")).thenReturn(false);
        when(msg.get("auth.refresh.invalid")).thenReturn("Token nieprawidłowy");

        assertThrows(IllegalArgumentException.class,
            () -> authService.refreshTokens(new RefreshTokenRequest("bad")));
    }

    @Test
    void shouldThrowWhenRefreshTokenIsNotRefreshType() {
        when(jwtService.validateToken("access-token")).thenReturn(true);
        when(jwtService.isRefreshToken("access-token")).thenReturn(false);
        when(msg.get("auth.refresh.invalid.type")).thenReturn("Zły typ tokenu");

        assertThrows(IllegalArgumentException.class,
            () -> authService.refreshTokens(new RefreshTokenRequest("access-token")));
    }

    @Test
    void shouldThrowWhenRefreshTokenRevoked() {
        when(jwtService.validateToken("revoked")).thenReturn(true);
        when(jwtService.isRefreshToken("revoked")).thenReturn(true);
        when(jwtService.hashToken("revoked")).thenReturn("revoked-hash");
        when(authTokenRepository.findRefreshableToken(eq("revoked-hash"), eq(TokenType.REFRESH_TOKEN), any(), any())).thenReturn(Optional.empty());
        when(msg.get("auth.refresh.revoked")).thenReturn("Token unieważniony");

        assertThrows(IllegalArgumentException.class,
            () -> authService.refreshTokens(new RefreshTokenRequest("revoked")));
    }

    // --- Logout ---

    @Test
    void shouldLogoutSuccessfully() {
        AuthToken storedToken = new AuthToken(existingUser, "hash", TokenType.REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtService.hashToken("refresh")).thenReturn("hash");
        when(authTokenRepository.findByTokenHashAndTokenType(eq("hash"), eq(TokenType.REFRESH_TOKEN))).thenReturn(Optional.of(storedToken));

        authService.logout("refresh");

        assertTrue(storedToken.isUsed());
        verify(authTokenRepository).save(storedToken);
    }

    @Test
    void shouldHandleLogoutWithNonExistentToken() {
        when(jwtService.hashToken("unknown")).thenReturn("unknown-hash");
        when(authTokenRepository.findByTokenHashAndTokenType(eq("unknown-hash"), eq(TokenType.REFRESH_TOKEN))).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.logout("unknown"));
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
