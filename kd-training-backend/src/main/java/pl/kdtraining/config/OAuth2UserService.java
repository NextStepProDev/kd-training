package pl.kdtraining.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.mail.AuthMailService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@org.springframework.context.annotation.Profile("oauth2")
public class OAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("pl");

    private final UserRepository userRepository;
    private final AdminEmailConfig adminEmailConfig;
    private final AuthMailService authMailService;

    public OAuth2UserService(UserRepository userRepository, AdminEmailConfig adminEmailConfig, AuthMailService authMailService) {
        this.userRepository = userRepository;
        this.adminEmailConfig = adminEmailConfig;
        this.authMailService = authMailService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        return processOAuth2User(provider, oAuth2User);
    }

    // Package-private test seam: bypasses the networked super.loadUser() to test account-mapping logic alone.
    OAuth2User processOAuth2User(String provider, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String oauthId = extractOAuthId(provider, attributes);
        String email = extractEmail(attributes);
        String firstName = extractFirstName(provider, attributes);
        String lastName = extractLastName(provider, attributes);

        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId(provider, oauthId);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            promoteToAdminIfConfigured(user);
        } else {
            Optional<User> userByEmail = userRepository.findByEmailIgnoreCase(email);
            if (userByEmail.isPresent()) {
                user = userByEmail.get();
                user.setOauthProvider(provider);
                user.setOauthId(oauthId);
                if (!user.isEmailVerified()) {
                    user.markEmailVerified();
                }
                promoteToAdminIfConfigured(user);
                userRepository.save(user);
            } else {
                user = createNewUser(provider, oauthId, email, firstName, lastName);
            }
        }

        return new CustomOAuth2User(oAuth2User, user);
    }

    private User createNewUser(String provider, String oauthId, String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName, "");
        user.setOauthProvider(provider);
        user.setOauthId(oauthId);
        user.markEmailVerified();
        user.setPreferredLanguage(detectLanguage());
        user.setRole(adminEmailConfig.isAdminEmail(email) ? UserRole.ADMIN : UserRole.USER);
        if (user.isAdmin()) {
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during OAuth2 registration", email);
        }
        User savedUser = userRepository.save(user);
        authMailService.sendWelcomeEmail(savedUser);
        authMailService.sendNewUserAdminNotification(savedUser);
        return savedUser;
    }

    private void promoteToAdminIfConfigured(User user) {
        if (!user.isAdmin() && adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during OAuth2 login", user.getEmail());
        }
    }

    // Guarded like extractEmail, and for the same reason: this is a cast off a map the provider
    // filled, so the declared String was a promise this method could not keep. Google always sends
    // "sub" — but an absent one used to sail through as a null oauthId, match no user, fall into
    // the e-mail branch and create an account with no provider identity to log back in with. Since
    // config became @NullMarked the repository refuses the null argument instead, turning that
    // silent bad account into a 500. Naming the condition here makes it a refused login, which is
    // what it always was.
    private String extractOAuthId(String provider, Map<String, Object> attributes) {
        String oauthId = switch (provider) {
            case "google" -> (String) attributes.get("sub");
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
        if (oauthId == null || oauthId.isBlank()) {
            throw new OAuth2AuthenticationException("Account identifier not provided by OAuth provider");
        }
        return oauthId;
    }

    private String extractEmail(Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by OAuth provider");
        }
        return email;
    }

    private String extractFirstName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "given_name", "User");
            default -> "User";
        };
    }

    private String extractLastName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> getStringOrDefault(attributes, "family_name", "");
            default -> "";
        };
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String s ? s : defaultValue;
    }

    private String detectLanguage() {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        return SUPPORTED_LANGUAGES.contains(lang) ? lang : "pl";
    }
}
