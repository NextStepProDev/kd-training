package pl.kdtraining.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.trainingcalendar.TrainingPhotoService;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.mail.AdminUserMailService;
import pl.kdtraining.infrastructure.security.JwtAuthenticationFilter;
import pl.kdtraining.infrastructure.security.PasswordPolicyValidator;
import pl.kdtraining.infrastructure.storage.FileStorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final AdminUserMailService adminUserMailService;
    private final PasswordEncoder passwordEncoder;
    private final MessageService msg;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FileStorageService fileStorageService;
    private final AdminEmailConfig adminEmailConfig;
    private final PasswordPolicyValidator passwordPolicy;
    private final TrainingPhotoService trainingPhotoService;

    public UserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                       AdminUserMailService adminUserMailService, PasswordEncoder passwordEncoder, MessageService msg, JwtAuthenticationFilter jwtAuthenticationFilter,
                       FileStorageService fileStorageService, AdminEmailConfig adminEmailConfig,
                       PasswordPolicyValidator passwordPolicy, TrainingPhotoService trainingPhotoService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.adminUserMailService = adminUserMailService;
        this.passwordEncoder = passwordEncoder;
        this.msg = msg;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.fileStorageService = fileStorageService;
        this.adminEmailConfig = adminEmailConfig;
        this.passwordPolicy = passwordPolicy;
        this.trainingPhotoService = trainingPhotoService;
    }

    public UserDtos.UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateMe(UUID userId, UserDtos.UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, UserDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() == null) {
            throw new IllegalStateException(msg.get("user.password.oauth"));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(msg.get("user.password.invalid"));
        }
        passwordPolicy.validate(request.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Changing the password is how a user secures a compromised account, so it must also cut the
        // sessions the attacker holds: drop every refresh token (same as a password reset does). Without
        // this a stolen refresh token stayed usable for its full 7-day lifetime after the "fix".
        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
        jwtAuthenticationFilter.evictUser(userId);
    }

    @Transactional
    public void deleteMe(UUID userId, UserDtos.DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() != null) {
            if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new IllegalArgumentException(msg.get("user.password.invalid"));
            }
        }
        // We capture the data for the notification before deletion (after delete the entity is detached).
        String fullName = user.getFullName();
        String email = user.getEmail();
        eraseAndDeleteAccount(user);

        // Self-service account deletion — the coach wouldn't otherwise learn about it (when deleting from
        // the admin panel the admin already knows the result).
        adminUserMailService.sendAccountSelfDeletedNotification(fullName, email);
    }

    /**
     * Automatic cleanup of abandoned OAuth accounts: created at login (Google passes
     * name/e-mail) that never accepted the privacy policy and are older than the threshold. GDPR —
     * since the user did not finalize consent and never came back, we have no basis to keep storing their data.
     * Invoked by a scheduler. Returns the number of deleted accounts.
     */
    @Transactional
    public int purgeAbandonedUnconsentedAccounts(int olderThanDays) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(olderThanDays));
        List<User> abandoned = userRepository
                .findByOauthProviderIsNotNullAndPrivacyAcceptedAtIsNullAndCreatedAtBefore(cutoff);
        abandoned.forEach(this::eraseAndDeleteAccount);
        return abandoned.size();
    }

    // The shared "tail" of account deletion: deleting comment photos, the avatar, tokens, the account
    // itself, and eviction from the JWT filter cache. Call within a transaction, after permission checks.
    private void eraseAndDeleteAccount(User user) {
        UUID userId = user.getId();
        // Comment photos live on disk, and the rows pointing at them go through the DB cascade
        // without Hibernate loading them — so nothing else would ever unlink these files.
        trainingPhotoService.purgeForUser(userId);
        if (user.getAvatarFilename() != null) {
            fileStorageService.delete(AVATAR_FOLDER, user.getAvatarFilename());
        }
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
        // Without this, a deleted user would still authenticate from the JWT filter cache for ~60s.
        jwtAuthenticationFilter.evictUser(userId);
    }

    /**
     * "Log out everywhere": deletes ALL of the user's refresh tokens, so no device can refresh its
     * session — every device drops to logged-out within ≤15 min (when its access token expires).
     * Not instant and not device-specific by design (all-or-nothing). The caller's own session is
     * affected too, so the frontend logs the user out locally as well.
     */
    @Transactional
    public void logoutAllDevices(UUID userId) {
        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
    }

    @Transactional
    public UserDtos.UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        String oldFilename = user.getAvatarFilename();
        String filename = fileStorageService.store(AVATAR_FOLDER, file);
        user.setAvatarFilename(filename);
        userRepository.save(user);
        if (oldFilename != null) {
            fileStorageService.delete(AVATAR_FOLDER, oldFilename);
        }
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse deleteAvatar(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        String oldFilename = user.getAvatarFilename();
        if (oldFilename != null) {
            user.setAvatarFilename(null);
            userRepository.save(user);
            fileStorageService.delete(AVATAR_FOLDER, oldFilename);
            jwtAuthenticationFilter.evictUser(userId);
        }
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateMarketing(UUID userId, UserDtos.UpdateMarketingRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Enabling sets the consent moment (if not already present), disabling clears it — full consent withdrawal (GDPR).
        if (request.enabled()) {
            if (!user.hasMarketingConsent()) {
                user.setMarketingConsentAt(Instant.now());
            }
        } else {
            user.setMarketingConsentAt(null);
        }
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse submitConsents(UUID userId, UserDtos.ConsentsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Privacy policy mandatory until it has been accepted (completing Google accounts — GDPR).
        // Email/password accounts already have it set at registration, so we don't enforce it again here.
        if (!user.hasPrivacyAccepted()) {
            if (!request.acceptedPrivacy()) {
                throw new IllegalArgumentException(msg.get("validation.privacy.required"));
            }
            user.setPrivacyAcceptedAt(Instant.now());
        }
        // Marketing voluntary — we set it only when checked and not yet granted.
        if (request.acceptedMarketing() && !user.hasMarketingConsent()) {
            user.setMarketingConsentAt(Instant.now());
        }
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    /**
     * Records explicit consent to processing the 1-on-1 calendar's health data (GDPR art. 9(2)(a)).
     * Idempotent — re-accepting keeps the first timestamp, which is the proof of consent.
     * <p>
     * Only a coaching client can grant it: without the flag there is no calendar to consent to, and
     * storing a consent for one would leave a health-data declaration on an account that has none.
     */
    public UserDtos.UserResponse grantTrainingConsent(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (!user.isAthlete()) {
            throw new NotFoundException(msg.get("athlete.not.found"));
        }
        user.grantTrainingConsent();
        userRepository.save(user);
        return toResponse(user);
    }

    private UserDtos.UserResponse toResponse(User user) {
        String avatarUrl = user.getAvatarFilename() != null
            ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
            : null;
        return new UserDtos.UserResponse(
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.getPhone(), user.getRole().name(),
            user.getRole() == UserRole.ADMIN,
            adminEmailConfig.isAdminEmail(user.getEmail()),
            user.isEmailVerified(),
            user.hasPrivacyAccepted(), user.hasMarketingConsent(),
            user.isAthlete(), user.hasTrainingConsent(),
            user.getPreferredLanguage(), user.getPasswordHash() != null,
            user.getOauthProvider() != null, avatarUrl, user.getCreatedAt()
        );
    }
}
