package pl.kdtraining.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.admin.AdminUserDtos.*;
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
import pl.kdtraining.infrastructure.storage.FileStorageService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final String AVATAR_FOLDER = "avatars";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final AdminEmailConfig adminEmailConfig;
    private final AdminUserMailService adminUserMailService;
    private final FileStorageService fileStorageService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MessageService msg;
    private final TrainingPhotoService trainingPhotoService;

    public AdminUserService(UserRepository userRepository,
                            AuthTokenRepository authTokenRepository,
                            AdminEmailConfig adminEmailConfig,
                            AdminUserMailService adminUserMailService,
                            FileStorageService fileStorageService,
                            JwtAuthenticationFilter jwtAuthenticationFilter,
                            MessageService msg,
                            TrainingPhotoService trainingPhotoService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.adminEmailConfig = adminEmailConfig;
        this.adminUserMailService = adminUserMailService;
        this.fileStorageService = fileStorageService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.msg = msg;
        this.trainingPhotoService = trainingPhotoService;
    }

    @Transactional(readOnly = true)
    public PagedUsersResponse list(String search, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort, direction));

        // Technical/developer accounts (e.g. the developer's account) are hidden from the list — they have admin for testing,
        // but should not clutter the view or confuse the other administrators. Filter in SQL so that counters/pagination
        // stay consistent; when there are no hidden e-mails, use the plain queries (NOT IN () would be invalid).
        Set<String> hidden = adminEmailConfig.getHiddenEmails();
        boolean blank = search == null || search.isBlank();
        Page<User> result = hidden.isEmpty()
                ? (blank ? userRepository.findAll(pageable)
                         : userRepository.searchByPhrase(search.trim(), pageable))
                : (blank ? userRepository.findAllExcludingEmails(hidden, pageable)
                         : userRepository.searchByPhraseExcludingEmails(search.trim(), hidden, pageable));

        List<AdminUserResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PagedUsersResponse(
                content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // Whitelist of sortable fields (protects against injecting an arbitrary property into Sort).
    // Phone is intentionally not sortable.
    //
    // Every key ends on `id`, and that is not decoration. The list is paged 50 at a time and three of
    // the four keys are heavily tied — `role` holds two values, `marketingConsentAt` is null on most
    // accounts. Postgres promises nothing about the order of rows inside a tie and need not answer
    // OFFSET 0 and OFFSET 50 the same way, so without a unique last key one person lands on two pages
    // while another lands on none. Adding a sortable column? Give it the same tiebreaker.
    //
    // Note on "role": the column stores the enum as text, so this sorts alphabetically rather than by
    // declaration order. With ADMIN and USER the two happen to coincide. A third role would not, and
    // the fix then belongs in the service, the way AthleteGoalService sorts horizons.
    private Sort resolveSort(String sort, String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return switch (sort == null ? "" : sort) {
            case "name" -> Sort.by(dir, "lastName", "firstName").and(Sort.by(dir, "id"));
            case "email" -> Sort.by(dir, "email").and(Sort.by(dir, "id"));
            case "role" -> Sort.by(dir, "role").and(Sort.by(dir, "id"));
            case "marketing" -> Sort.by(dir, "marketingConsentAt").and(Sort.by(dir, "id"));
            default -> Sort.by(dir, "createdAt").and(Sort.by(dir, "id"));
        };
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getDetail(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        String avatarUrl = user.getAvatarFilename() != null
                ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
                : null;

        return new AdminUserDetailResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getRole().name(), user.isAdmin(),
                adminEmailConfig.isAdminEmail(user.getEmail()),
                user.isEmailVerified(),
                user.hasMarketingConsent(),
                user.isAthlete(),
                user.getPreferredLanguage(), user.getPasswordHash() != null,
                user.getOauthProvider() != null, avatarUrl, user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public SendEmailResponse sendEmail(SendEmailRequest request) {
        // MARKETING mode reaches only people with an active consent and appends an unsubscribe link.
        // ALL/SELECTED are service announcements (no link) — responsibility for the content lies with the admin.
        boolean marketing = "MARKETING".equals(request.audience());
        List<User> base = switch (request.audience()) {
            case "MARKETING" -> userRepository.findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc();
            case "ALL" -> userRepository.findAllByOrderByCreatedAtDesc();
            case "SELECTED" -> request.userIds() == null || request.userIds().isEmpty()
                    ? List.<User>of()
                    : userRepository.findAllById(request.userIds());
            default -> throw new IllegalArgumentException(msg.get("email.admin.invalid.audience"));
        };

        List<User> recipients = base.stream()
                // Hidden accounts (technical/developer) are never recipients of a bulk send.
                .filter(u -> !adminEmailConfig.isHiddenEmail(u.getEmail()))
                .toList();

        if (recipients.isEmpty()) {
            throw new IllegalStateException(msg.get("email.admin.no.recipients"));
        }

        // One background campaign task (sequential loop on mailCampaignExecutor) — not one @Async task per
        // recipient, which would overflow the mailExecutor queue and starve transactional mail. Recipients are
        // detached to plain values inside this transaction before crossing the @Async boundary.
        List<AdminUserMailService.CampaignRecipient> campaign = recipients.stream()
                .map(u -> new AdminUserMailService.CampaignRecipient(
                        u.getEmail(), u.getFirstName(),
                        marketing ? u.getMarketingUnsubscribeToken().toString() : null))
                .toList();
        adminUserMailService.sendCampaign(campaign, request.subject(), request.message());

        return new SendEmailResponse(recipients.size());
    }

    @Transactional
    public void delete(UUID adminId, UUID targetId, boolean notify) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        if (target.getId().equals(adminId)) {
            throw new IllegalStateException(msg.get("error.user.cannot.delete.self"));
        }
        if (adminEmailConfig.isAdminEmail(target.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.cannot.delete.superadmin"));
        }

        // Notify the user about account deletion (optional — the admin decides; e.g. yes for GDPR/a real person,
        // no for test/spam accounts). Data captured before delete.
        if (notify) {
            adminUserMailService.sendAccountDeletedNotification(target.getEmail(), target.getFirstName());
        }

        // Comment photos live on disk, and the rows pointing at them go through the DB cascade
        // without Hibernate loading them — so nothing else would ever unlink these files.
        trainingPhotoService.purgeForUser(targetId);

        if (target.getAvatarFilename() != null) {
            fileStorageService.delete(AVATAR_FOLDER, target.getAvatarFilename());
        }
        authTokenRepository.deleteAllByUserId(targetId);
        userRepository.delete(target);
        // Without evict, a deleted/refreshed user would still authenticate from the JWT filter cache for ~60s.
        jwtAuthenticationFilter.evictUser(targetId);
    }

    /**
     * Force-logout of a chosen user (e.g. a compromised account): deletes ALL their refresh tokens so
     * no device can refresh, and evicts the JWT filter cache. Sessions drop to logged-out within ≤15 min
     * (when access tokens expire) — not instant, not device-specific. Allowed against any user, admins
     * included (a deliberate scenario, unlike delete which protects the super-admin).
     */
    @Transactional
    public void forceLogout(UUID targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        authTokenRepository.deleteByUserIdAndTokenType(target.getId(), TokenType.REFRESH_TOKEN);
        jwtAuthenticationFilter.evictUser(target.getId());
    }

    @Transactional
    public AdminUserResponse promote(UUID adminId, UUID targetId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Granting the admin role can only be done by the super-admin defined in .env (ADMIN_EMAIL).
        if (!adminEmailConfig.isAdminEmail(admin.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.promote.forbidden"));
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (target.isAdmin()) {
            throw new IllegalStateException(msg.get("error.user.already.admin"));
        }
        target.setRole(UserRole.ADMIN);
        userRepository.save(target);
        jwtAuthenticationFilter.evictUser(targetId);
        return toResponse(target);
    }

    @Transactional
    public AdminUserResponse demote(UUID adminId, UUID targetId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Revoking the admin role can only be done by the super-admin defined in .env (ADMIN_EMAIL).
        if (!adminEmailConfig.isAdminEmail(admin.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.demote.forbidden"));
        }
        if (targetId.equals(adminId)) {
            throw new IllegalStateException(msg.get("error.user.cannot.demote.self"));
        }

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // The super-admin from .env would be promoted again on login anyway → block this pointless operation.
        if (adminEmailConfig.isAdminEmail(target.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.cannot.demote.superadmin"));
        }
        if (!target.isAdmin()) {
            throw new IllegalStateException(msg.get("error.user.not.admin"));
        }
        target.setRole(UserRole.USER);
        userRepository.save(target);
        jwtAuthenticationFilter.evictUser(targetId);
        return toResponse(target);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getRole().name(), user.isAdmin(),
                adminEmailConfig.isAdminEmail(user.getEmail()),
                user.isEmailVerified(),
                user.hasMarketingConsent(),
                user.isAthlete(),
                user.getCreatedAt()
        );
    }

    /**
     * Flags (or unflags) a user as a 1-on-1 coaching client. Unlike promote/demote this is plain
     * admin work, not a super-admin privilege — it grants no rights over anyone else's data.
     * <p>
     * Clearing the flag is deliberately non-destructive: the personal trainings, comments and goals
     * stay in the database, they just stop being reachable. Re-flagging restores the full history.
     */
    @Transactional
    public AdminUserResponse setAthlete(UUID targetId, boolean athlete) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        target.setAthlete(athlete);
        userRepository.save(target);
        return toResponse(target);
    }
}
