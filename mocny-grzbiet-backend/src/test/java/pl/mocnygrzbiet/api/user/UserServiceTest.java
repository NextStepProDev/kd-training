package pl.mocnygrzbiet.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.mocnygrzbiet.api.NotFoundException;
import pl.mocnygrzbiet.api.user.UserDtos.*;
import pl.mocnygrzbiet.domain.auth.AuthTokenRepository;
import pl.mocnygrzbiet.domain.auth.TokenType;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.domain.user.UserRole;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;
import pl.mocnygrzbiet.infrastructure.security.JwtAuthenticationFilter;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private pl.mocnygrzbiet.infrastructure.mail.AdminUserMailService adminUserMailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MessageService msg;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private pl.mocnygrzbiet.infrastructure.storage.FileStorageService fileStorageService;
    @Mock private pl.mocnygrzbiet.config.AdminEmailConfig adminEmailConfig;
    @Mock private pl.mocnygrzbiet.infrastructure.security.PasswordPolicyValidator passwordPolicy;
    @Mock private pl.mocnygrzbiet.api.trainingcalendar.TrainingPhotoService trainingPhotoService;
    @Mock private PasswordAttempts passwordAttempts;

    @InjectMocks private UserService service;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        user = new User("jan@test.com", "Jan", "Kowalski", "123456789");
        setId(user, userId);
        user.setPasswordHash("encoded-password");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
    }

    @Test
    void shouldGetCurrentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = service.getMe(userId);

        assertEquals("jan@test.com", result.email());
        assertEquals("Jan", result.firstName());
        assertEquals("USER", result.role());
        assertFalse(result.isAdmin());
        assertTrue(result.emailVerified());
        assertTrue(result.hasPassword());
        assertFalse(result.oauthLinked());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(msg.get("error.user.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.getMe(userId));
    }

    @Test
    void shouldUpdateUserProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest("Anna", "Nowak", "987654321");
        service.updateMe(userId, request);

        assertEquals("Anna", user.getFirstName());
        assertEquals("Nowak", user.getLastName());
        assertEquals("987654321", user.getPhone());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldChangePassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded");

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword", "NewPassword123");
        service.changePassword(userId, request);

        assertEquals("new-encoded", user.getPasswordHash());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldRevokeRefreshTokensWhenPasswordChanged() {
        // Given: a user securing a compromised account by changing their password
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded");

        // When
        service.changePassword(userId, new ChangePasswordRequest("OldPassword", "NewPassword123"));

        // Then: every refresh token dies with the old password, so a stolen session cannot survive it
        verify(authTokenRepository).deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
    }

    @Test
    void shouldThrowWhenChangingPasswordForOAuthUser() {
        user.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("user.password.oauth")).thenReturn("OAuth user");

        assertThrows(IllegalStateException.class,
            () -> service.changePassword(userId, new ChangePasswordRequest("old", "new12345")));
    }

    @Test
    void shouldThrowWhenCurrentPasswordInvalid() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        when(passwordAttempts.recordFailure(userId)).thenReturn(user);
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.changePassword(userId, new ChangePasswordRequest("wrong", "new12345")));
    }

    @Test
    void shouldDeleteAccountWithPasswordVerification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);

        service.deleteMe(userId, new DeleteAccountRequest("Password123"));

        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldUnlinkPhotosBeforeDeletingAccount() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);

        service.deleteMe(userId, new DeleteAccountRequest("Password123"));

        // The comment rows naming the files go through the DB cascade with the account, so the files
        // must be unlinked first — afterwards nothing could find their names.
        var order = inOrder(trainingPhotoService, userRepository);
        order.verify(trainingPhotoService).purgeForUser(userId);
        order.verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldDeleteOAuthAccountWithoutPassword() {
        user.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.deleteMe(userId, new DeleteAccountRequest(null));

        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteById(userId);
        // The coach always gets an account-deletion notification.
        verify(adminUserMailService).sendAccountSelfDeletedNotification(user.getFullName(), user.getEmail());
    }

    @Test
    void shouldThrowWhenDeletePasswordInvalid() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        when(passwordAttempts.recordFailure(userId)).thenReturn(user);
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.deleteMe(userId, new DeleteAccountRequest("wrong")));
    }

    @Test
    void shouldThrowWhenDeleteWithNullPasswordForPasswordUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordAttempts.recordFailure(userId)).thenReturn(user);
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.deleteMe(userId, new DeleteAccountRequest(null)));
    }

    @Test
    void shouldRefuseToDeleteTheOnlyAdministratorAccount() {
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThrows(IllegalStateException.class,
            () -> service.deleteMe(userId, new DeleteAccountRequest("Password123")));
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void shouldLetAnAdministratorLeaveWhileAnotherOneRemains() {
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);

        service.deleteMe(userId, new DeleteAccountRequest("Password123"));

        verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldRefuseThePasswordChangeWhileTheAccountIsLockedWithoutCheckingThePassword() {
        for (int i = 0; i < 5; i++) user.incrementFailedLoginAttempts();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get(eq("auth.login.locked"), any())).thenReturn("Konto zablokowane");

        assertThrows(IllegalStateException.class,
            () -> service.changePassword(userId, new ChangePasswordRequest("Password123", "new12345")));
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordAttempts, never()).recordFailure(any());
    }

    @Test
    void shouldPurgeAbandonedUnconsentedAccounts() throws Exception {
        User abandoned = new User("ghost@test.com", "Ghost", "User", null);
        UUID abandonedId = UUID.randomUUID();
        setId(abandoned, abandonedId);
        abandoned.setOauthProvider("google");
        when(userRepository.findByOauthProviderIsNotNullAndPrivacyAcceptedAtIsNullAndCreatedAtBefore(any()))
                .thenReturn(java.util.List.of(abandoned));

        int deleted = service.purgeAbandonedUnconsentedAccounts(7);

        assertEquals(1, deleted);
        verify(userRepository).deleteById(abandonedId);
        verify(authTokenRepository).deleteAllByUserId(abandonedId);
        verify(jwtAuthenticationFilter).evictUser(abandonedId);
        // Nobody ever finished signing up here, so there is nothing to tell the coach.
        verifyNoInteractions(adminUserMailService);
    }

    @Test
    void shouldDoNothingWhenNoAbandonedAccounts() {
        when(userRepository.findByOauthProviderIsNotNullAndPrivacyAcceptedAtIsNullAndCreatedAtBefore(any()))
                .thenReturn(java.util.List.of());

        int deleted = service.purgeAbandonedUnconsentedAccounts(7);

        assertEquals(0, deleted);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void shouldGrantMarketingConsentWhenEnabled() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = service.updateMarketing(userId, new UpdateMarketingRequest(true));

        assertTrue(user.hasMarketingConsent());
        assertTrue(result.marketingConsent());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldRevokeMarketingConsentWhenDisabled() {
        user.setMarketingConsentAt(java.time.Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = service.updateMarketing(userId, new UpdateMarketingRequest(false));

        assertFalse(user.hasMarketingConsent());
        assertFalse(result.marketingConsent());
    }

    @Test
    void shouldSetPrivacyAndMarketingOnConsentsForGoogleUser() {
        user.setPrivacyAcceptedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = service.submitConsents(userId, new ConsentsRequest(true, true, true));

        assertTrue(user.hasPrivacyAccepted());
        assertTrue(user.hasTermsAccepted());
        assertTrue(user.hasMarketingConsent());
        assertTrue(result.privacyAccepted());
        assertTrue(result.termsAccepted());
        assertTrue(result.marketingConsent());
    }

    @Test
    void shouldRejectConsentsWhenPrivacyMissingAndNotAccepted() {
        user.setPrivacyAcceptedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("validation.privacy.required")).thenReturn("Wymagana polityka");

        assertThrows(IllegalArgumentException.class,
            () -> service.submitConsents(userId, new ConsentsRequest(false, true, true)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectConsentsWhenTermsMissingAndNotAccepted() {
        user.setPrivacyAcceptedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("validation.terms.required")).thenReturn("Wymagany regulamin");

        assertThrows(IllegalArgumentException.class,
            () -> service.submitConsents(userId, new ConsentsRequest(true, false, true)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotAskForTermsAgainOnceTheCurrentVersionIsAccepted() {
        java.time.Instant accepted = java.time.Instant.parse("2026-01-01T10:00:00Z");
        user.setPrivacyAcceptedAt(accepted);
        user.acceptCurrentTerms(accepted);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.submitConsents(userId, new ConsentsRequest(false, false, true));

        // The first acceptance is the proof; a later request must neither refuse nor overwrite it.
        assertEquals(accepted, user.getTermsAcceptedAt());
    }

    /** Accepted the terms once — under a version that is no longer in force. */
    private void acceptedEarlierTerms(java.time.Instant at) {
        user.setPrivacyAcceptedAt(at);
        user.acceptCurrentTerms(at);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "termsVersion", "2025-01-01");
    }

    @Test
    void shouldAskAgainOnceTheTermsChange() {
        acceptedEarlierTerms(java.time.Instant.parse("2025-01-01T10:00:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse before = service.getMe(userId);

        assertFalse(before.termsAccepted(), "an acceptance of the old text does not cover the new one");
        assertTrue(before.termsUpdated());
    }

    @Test
    void shouldRecordTheNewVersionWhenAcceptedAgain() {
        acceptedEarlierTerms(java.time.Instant.parse("2025-01-01T10:00:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(msg.get("validation.terms.required")).thenReturn("Wymagany regulamin");

        assertThrows(IllegalArgumentException.class,
            () -> service.submitConsents(userId, new ConsentsRequest(false, false, false)));
        UserResponse after = service.submitConsents(userId, new ConsentsRequest(false, true, false));

        assertEquals(pl.mocnygrzbiet.domain.user.TermsOfService.CURRENT_VERSION, user.getTermsVersion());
        assertTrue(user.getTermsAcceptedAt().isAfter(java.time.Instant.parse("2025-01-01T10:00:00Z")));
        assertTrue(after.termsAccepted());
        assertFalse(after.termsUpdated());
    }

    @Test
    void shouldNotLockTheCoachOutWhenTheTermsChange() {
        // The terms are his own: a new version must not hold the panel behind an acceptance screen.
        acceptedEarlierTerms(java.time.Instant.parse("2025-01-01T10:00:00Z"));
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse me = service.getMe(userId);

        assertTrue(me.termsAccepted());
        assertFalse(me.termsUpdated());
    }

    @Test
    void shouldReturnAdminFlagForAdminUser() {
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = service.getMe(userId);

        assertTrue(result.isAdmin());
        assertEquals("ADMIN", result.role());
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
