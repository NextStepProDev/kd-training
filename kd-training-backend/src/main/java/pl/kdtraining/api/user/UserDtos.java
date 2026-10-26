package pl.kdtraining.api.user;

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
                                boolean privacyAccepted, boolean marketingConsent,
                                boolean isAthlete, boolean trainingConsent,
                                String preferredLanguage, boolean hasPassword,
                                boolean oauthLinked, @Nullable String avatarUrl, Instant createdAt) {}

    public record UpdateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String firstName,
        @NotBlank @Size(min = 3, max = 100) String lastName,
        @Nullable String phone
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 10, max = 100, message = "{validation.password.size}") String newPassword
    ) {}

    public record DeleteAccountRequest(@Nullable String password) {}

    public record UpdateMarketingRequest(boolean enabled) {}

    // Consents collected on the account side — mainly to complete Google accounts on the profile-completion screen
    // (privacy policy mandatory, marketing optional). The mandatory check is validated in the service,
    // because the policy may have been accepted earlier (email/password registration).
    public record ConsentsRequest(boolean acceptedPrivacy, boolean acceptedMarketing) {}
}
