package pl.kdtraining.domain.user;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    @Nullable
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "oauth_provider")
    @Nullable
    private String oauthProvider;

    @Column(name = "oauth_id")
    @Nullable
    private String oauthId;

    @Column(name = "password_hash")
    @Nullable
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    @Nullable
    private Instant emailVerifiedAt;

    @Column(name = "avatar_filename")
    @Nullable
    private String avatarFilename;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "pl";

    @Column(name = "privacy_accepted_at")
    @Nullable
    private Instant privacyAcceptedAt;

    @Column(name = "terms_accepted_at")
    @Nullable
    private Instant termsAcceptedAt;

    @Column(name = "marketing_consent_at")
    @Nullable
    private Instant marketingConsentAt;

    @Column(name = "marketing_unsubscribe_token", nullable = false, updatable = false)
    private UUID marketingUnsubscribeToken = UUID.randomUUID();

    // 1-on-1 coaching client. Set manually by an admin; clearing it hides the personal training
    // calendar but keeps every row behind it, so re-enabling restores the full history.
    @Column(name = "is_athlete", nullable = false)
    private boolean athlete = false;

    // Explicit consent (GDPR art. 9(2)(a)) to processing the calendar's health data: calorie targets,
    // effort ratings, post-session notes and photos. NULL = not
    // given; the timestamp is the proof of consent. Cleared together with the athlete flag.
    @Column(name = "training_consent_at")
    @Nullable
    private Instant trainingConsentAt;

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    @Nullable
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public User(String email, String firstName, String lastName, @Nullable String phone) {
        this.email = normalizeEmail(email);
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    public void setPhone(@Nullable String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Nullable
    public String getOauthProvider() {
        return oauthProvider;
    }

    public void setOauthProvider(@Nullable String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    @Nullable
    public String getOauthId() {
        return oauthId;
    }

    public void setOauthId(@Nullable String oauthId) {
        this.oauthId = oauthId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Nullable
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @Nullable
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(@Nullable Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    @Nullable
    public String getAvatarFilename() {
        return avatarFilename;
    }

    public void setAvatarFilename(@Nullable String avatarFilename) {
        this.avatarFilename = avatarFilename;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    @Nullable
    public Instant getPrivacyAcceptedAt() {
        return privacyAcceptedAt;
    }

    public void setPrivacyAcceptedAt(@Nullable Instant privacyAcceptedAt) {
        this.privacyAcceptedAt = privacyAcceptedAt;
    }

    public boolean hasPrivacyAccepted() {
        return privacyAcceptedAt != null;
    }

    @Nullable
    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(@Nullable Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public boolean hasTermsAccepted() {
        return termsAcceptedAt != null;
    }

    @Nullable
    public Instant getMarketingConsentAt() {
        return marketingConsentAt;
    }

    public void setMarketingConsentAt(@Nullable Instant marketingConsentAt) {
        this.marketingConsentAt = marketingConsentAt;
    }

    public boolean hasMarketingConsent() {
        return marketingConsentAt != null;
    }

    public UUID getMarketingUnsubscribeToken() {
        return marketingUnsubscribeToken;
    }

    public boolean isAthlete() {
        return athlete;
    }

    public void setAthlete(boolean athlete) {
        this.athlete = athlete;
        // Losing the flag drops the consent with it: returning to the calendar months later has to
        // be a fresh decision, not one carried over from an arrangement that already ended.
        if (!athlete) {
            this.trainingConsentAt = null;
        }
    }

    @Nullable
    public Instant getTrainingConsentAt() {
        return trainingConsentAt;
    }

    public boolean hasTrainingConsent() {
        return trainingConsentAt != null;
    }

    /** Idempotent: re-confirming keeps the original proof-of-consent timestamp. */
    public void grantTrainingConsent() {
        if (trainingConsentAt == null) {
            trainingConsentAt = Instant.now();
        }
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    @Nullable
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public void incrementFailedLoginAttempts() {
        // After the previous lockout expires we count from scratch — otherwise the first failure after
        // expiry (with the counter still at the threshold) would lock the account immediately.
        if (lockedUntil != null && Instant.now().isAfter(lockedUntil)) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60L);
        }
    }

    public long getRemainingLockoutMinutes() {
        if (lockedUntil == null) return 0;
        long seconds = Duration.between(Instant.now(), lockedUntil).getSeconds();
        if (seconds <= 0) return 0;
        return (seconds + 59) / 60;
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
