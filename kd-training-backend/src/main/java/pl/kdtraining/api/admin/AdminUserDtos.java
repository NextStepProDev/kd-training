package pl.kdtraining.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record AdminUserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            @Nullable String phone,
            String role,
            boolean isAdmin,
            boolean superAdmin,
            boolean emailVerified,
            boolean marketingConsent,
            boolean isAthlete,
            Instant createdAt
    ) {}

    // audience: MARKETING (marketing consents only + unsubscribe link), ALL (service announcement to everyone),
    // SELECTED (selected people from userIds). Value validation in the service.
    public record SendEmailRequest(
            @NotBlank @Size(max = 200, message = "{validation.email.subject.size}") String subject,
            @NotBlank @Size(max = 10000, message = "{validation.email.message.size}") String message,
            @NotBlank String audience,
            @Nullable List<UUID> userIds
    ) {}

    public record SendEmailResponse(int recipientCount) {}

    public record PagedUsersResponse(
            List<AdminUserResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record AdminUserDetailResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            @Nullable String phone,
            String role,
            boolean isAdmin,
            boolean superAdmin,
            boolean emailVerified,
            boolean marketingConsent,
            boolean isAthlete,
            String preferredLanguage,
            boolean hasPassword,
            boolean oauthLinked,
            @Nullable String avatarUrl,
            Instant createdAt
    ) {}
}
