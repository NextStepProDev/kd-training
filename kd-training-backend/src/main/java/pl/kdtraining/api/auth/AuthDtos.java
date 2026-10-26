package pl.kdtraining.api.auth;

import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.password.required}") @Size(min = 10, max = 100, message = "{validation.password.size}") String password,
        @NotBlank(message = "{validation.firstname.required}") @Size(min = 3, max = 36, message = "{validation.firstname.size}") String firstName,
        @NotBlank(message = "{validation.lastname.required}") @Size(min = 3, max = 36, message = "{validation.lastname.size}") String lastName,
        @Nullable @Pattern(regexp = "^(\\d{9}|\\+\\d{1,4}\\d{9})$", message = "{validation.phone.format}") String phone,
        @Nullable String preferredLanguage,
        @AssertTrue(message = "{validation.privacy.required}") boolean acceptedPrivacy,
        @Nullable Boolean acceptedMarketing
    ) {}

    public record LoginRequest(
        @NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.password.required}") String password
    ) {}

    public record AuthTokensResponse(String accessToken, String refreshToken, long expiresIn) {}
    public record RefreshTokenRequest(@NotBlank(message = "{validation.refresh.token.required}") String refreshToken) {}
    public record ForgotPasswordRequest(@NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email) {}
    public record ResetPasswordRequest(@NotBlank(message = "{validation.token.required}") String token, @NotBlank(message = "{validation.password.required}") @Size(min = 10, max = 100, message = "{validation.password.size}") String newPassword) {}
    public record ResendVerificationRequest(@NotBlank(message = "{validation.email.required}") @Email(message = "{validation.email.invalid}") String email) {}
    public record MessageResponse(String message) {}
}
