package pl.kdtraining.infrastructure.security;

import pl.kdtraining.domain.user.User;

import java.util.UUID;

public class JwtAuthenticatedUser {

    private final UUID userId;
    private final String email;
    private final String role;

    public JwtAuthenticatedUser(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole().name();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
