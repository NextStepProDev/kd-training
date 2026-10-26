package pl.kdtraining.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.mail.AuthMailService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private AuthMailService authMailService;

    @InjectMocks private OAuth2UserService service;

    private OAuth2User googleUser(String email, String given, String family, String sub) {
        Map<String, Object> attrs = Map.of(
            "sub", sub,
            "email", email,
            "given_name", given,
            "family_name", family
        );
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
    }

    @Test
    void shouldCreateNewUserWithoutAnyConsentsForRodoGate() {
        // GDPR: a Google account is created WITHOUT any consents — the policy/marketing is completed on the /uzupelnij-profil screen.
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("anna@gmail.com")).thenReturn(Optional.empty());
        when(adminEmailConfig.isAdminEmail("anna@gmail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuth2User result = service.processOAuth2User("google", googleUser("anna@gmail.com", "Anna", "Nowak", "sub-1"));

        User created = ((CustomOAuth2User) result).getUser();
        assertEquals("anna@gmail.com", created.getEmail());
        assertEquals("Anna", created.getFirstName());
        assertEquals("Nowak", created.getLastName());
        assertEquals(UserRole.USER, created.getRole());
        assertTrue(created.isEmailVerified());
        assertFalse(created.hasPrivacyAccepted(), "Zgoda RODO nie może być ustawiana po cichu przy OAuth");
        assertFalse(created.hasMarketingConsent(), "Marketing nie może być ustawiany po cichu przy OAuth");
        verify(authMailService).sendWelcomeEmail(any(User.class));
        verify(authMailService).sendNewUserAdminNotification(any(User.class));
    }

    @Test
    void shouldAutoPromoteToAdminWhenEmailMatchesConfigOnRegistration() {
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("boss@kdtraining.test")).thenReturn(Optional.empty());
        when(adminEmailConfig.isAdminEmail("boss@kdtraining.test")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuth2User result = service.processOAuth2User(
            "google", googleUser("boss@kdtraining.test", "Boss", "Admin", "sub-2"));

        assertEquals(UserRole.ADMIN, ((CustomOAuth2User) result).getUser().getRole());
    }

    @Test
    void shouldReuseExistingOauthUserAndNotResendWelcomeMail() {
        User existing = new User("anna@gmail.com", "Anna", "Nowak", null);
        existing.setRole(UserRole.USER);
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-1")).thenReturn(Optional.of(existing));
        when(adminEmailConfig.isAdminEmail("anna@gmail.com")).thenReturn(false);

        OAuth2User result = service.processOAuth2User("google", googleUser("anna@gmail.com", "Anna", "Nowak", "sub-1"));

        assertSame(existing, ((CustomOAuth2User) result).getUser());
        verify(authMailService, never()).sendWelcomeEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldPromoteExistingUserToAdminOnLoginWhenConfigured() {
        User existing = new User("boss@kdtraining.test", "Boss", "Admin", null);
        existing.setRole(UserRole.USER);
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-2")).thenReturn(Optional.of(existing));
        when(adminEmailConfig.isAdminEmail("boss@kdtraining.test")).thenReturn(true);

        service.processOAuth2User("google", googleUser("boss@kdtraining.test", "Boss", "Admin", "sub-2"));

        assertEquals(UserRole.ADMIN, existing.getRole());
        verify(userRepository).save(existing);
    }

    @Test
    void shouldLinkOauthToExistingEmailAccountAndVerifyEmail() {
        User existing = new User("jan@gmail.com", "Jan", "Kowal", "123456789");
        existing.setRole(UserRole.USER);
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-3")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("jan@gmail.com")).thenReturn(Optional.of(existing));
        when(adminEmailConfig.isAdminEmail("jan@gmail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuth2User result = service.processOAuth2User("google", googleUser("jan@gmail.com", "Jan", "Kowal", "sub-3"));

        User linked = ((CustomOAuth2User) result).getUser();
        assertEquals("google", linked.getOauthProvider());
        assertTrue(linked.isEmailVerified());
        verify(authMailService, never()).sendWelcomeEmail(any());
    }

    @Test
    void shouldFallBackToDefaultNamesWhenGoogleOmitsThem() {
        Map<String, Object> attrs = Map.of("sub", "sub-4", "email", "noname@gmail.com");
        OAuth2User input = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
        when(userRepository.findByOauthProviderAndOauthId("google", "sub-4")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("noname@gmail.com")).thenReturn(Optional.empty());
        when(adminEmailConfig.isAdminEmail("noname@gmail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        service.processOAuth2User("google", input);

        verify(userRepository).save(captor.capture());
        assertEquals("User", captor.getValue().getFirstName());
        assertEquals("", captor.getValue().getLastName());
    }

    @Test
    void shouldRejectMissingEmailFromProvider() {
        Map<String, Object> attrs = Map.of("sub", "sub-5", "given_name", "Anna");
        OAuth2User input = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");

        assertThrows(OAuth2AuthenticationException.class,
            () -> service.processOAuth2User("google", input));
    }

    // Mockito rather than DefaultOAuth2User: that class refuses to be built without its own name
    // attribute, so the one shape worth testing here cannot be expressed with it.
    @Test
    void shouldRejectMissingSubjectFromProvider() {
        OAuth2User input = mock(OAuth2User.class);
        when(input.getAttributes()).thenReturn(Map.of("email", "anna@gmail.com", "given_name", "Anna"));

        assertThrows(OAuth2AuthenticationException.class,
            () -> service.processOAuth2User("google", input));

        verifyNoInteractions(userRepository, authMailService);
    }

    @Test
    void shouldRejectBlankSubjectFromProvider() {
        Map<String, Object> attrs = Map.of("sub", "   ", "email", "anna@gmail.com");
        OAuth2User input = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");

        assertThrows(OAuth2AuthenticationException.class,
            () -> service.processOAuth2User("google", input));

        verifyNoInteractions(userRepository, authMailService);
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        OAuth2User input = googleUser("anna@gmail.com", "Anna", "Nowak", "sub-1");

        assertThrows(OAuth2AuthenticationException.class,
            () -> service.processOAuth2User("facebook", input));
    }
}
