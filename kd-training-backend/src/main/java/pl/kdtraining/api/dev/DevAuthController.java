package pl.kdtraining.api.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.security.JwtService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevAuthController {

    private static final String DEV_PASSWORD = "dev";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;

    public DevAuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             JwtService jwtService, AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam(defaultValue = "false") boolean asAdmin) {
        String email = asAdmin ? "admin@dev.local" : "user@dev.local";
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, asAdmin ? "Admin" : "User", "Dev", null);
            u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            u.setRole(asAdmin ? UserRole.ADMIN : UserRole.USER);
            u.markEmailVerified();
            return userRepository.save(u);
        });

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());
        authTokenRepository.save(new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration));

        return ResponseEntity.ok(Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "expiresIn", jwtService.getAccessTokenExpirationSeconds(),
            "userId", user.getId(),
            "email", user.getEmail(),
            "role", user.getRole().name()
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        List<Map<String, Object>> list = userRepository.findAll().stream()
            .map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "email", u.getEmail(),
                "role", u.getRole().name(),
                "verified", u.isEmailVerified()))
            .toList();
        return ResponseEntity.ok(list);
    }
}
