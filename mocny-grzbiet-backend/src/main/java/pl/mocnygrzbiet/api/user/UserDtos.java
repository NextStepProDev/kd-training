package pl.mocnygrzbiet.api.user;

import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(UUID id, String email, String firstName, String lastName,
                                @Nullable String phone, String role, boolean isAdmin,
                                boolean superAdmin,
                                boolean emailVerified,
                                boolean privacyAccepted,
                                /** The terms in force are accepted; false also when an earlier version was. */
                                boolean termsAccepted,
                                /** Accepted once, but the terms changed since — the screen says "we updated them". */
                                boolean termsUpdated,
                                boolean marketingConsent,
                                boolean isAthlete, boolean trainingConsent,
                                String preferredLanguage, boolean hasPassword,
                                boolean oauthLinked, @Nullable String avatarUrl, Instant createdAt) {}

    public record UpdateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String firstName,
        @NotBlank @Size(min = 3, max = 100) String lastName,
        /** The same rule as RegisterRequest.phone: the coach has to be able to call this number. */
        @Nullable @Pattern(regexp = "^(\\d{9}|\\+\\d{1,4}\\d{9})$", message = "{validation.phone.format}") String phone
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 10, max = 100, message = "{validation.password.size}") String newPassword
    ) {}

    public record DeleteAccountRequest(@Nullable String password) {}

    public record UpdateMarketingRequest(boolean enabled) {}

    // Consents collected on the account side — mainly to complete Google accounts on the profile-completion screen
    // (privacy policy and terms mandatory, marketing optional). The mandatory checks are validated in the
    // service, because both may have been accepted earlier (email/password registration).
    public record ConsentsRequest(boolean acceptedPrivacy, boolean acceptedTerms, boolean acceptedMarketing) {}
}
