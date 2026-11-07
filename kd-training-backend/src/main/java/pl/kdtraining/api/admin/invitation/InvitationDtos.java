package pl.kdtraining.api.admin.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class InvitationDtos {
    private InvitationDtos() {}

    public record InviteRequest(
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        @Size(max = 255, message = "{validation.email.invalid}")
        String email
    ) {}

    public record InvitationResponse(UUID id, String email, Instant createdAt, Instant expiresAt) {}
}
