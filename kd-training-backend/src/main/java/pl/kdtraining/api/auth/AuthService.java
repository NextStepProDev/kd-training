package pl.kdtraining.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.InvalidRefreshTokenException;
import pl.kdtraining.api.auth.AuthDtos.*;
import pl.kdtraining.domain.auth.AuthToken;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.auth.TokenType;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.mail.AuthMailService;
import pl.kdtraining.infrastructure.security.JwtService;
import pl.kdtraining.infrastructure.security.PasswordPolicyValidator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("pl");
    // A format-valid BCrypt hash (cost 10) used solely for a dummy comparison
    // when the account does not exist — it evens out the response time and blocks user enumeration.
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final Duration EMAIL_VERIFICATION_EXPIRATION = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_EXPIRATION = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    // Ceiling on unsolicited mail to a single address, on top of the one-minute cooldown above and the
    // per-IP limit on /api/auth/. Neither of those caps what matters here: both "forgot password" and
    // "resend verification" mail a stranger on request, so anyone can point them at a victim's inbox and,
    // rotating IPs, keep it up for an hour a message. That also burns the Gmail relay's daily allowance
    // (500), and once it is gone nobody gets a verification or reset mail until the counter rolls over.
    // Three an hour is more than a person who lost a message to a spam folder will ever need.
    private static final Duration MAIL_QUOTA_WINDOW = Duration.ofHours(1);
    private static final int MAIL_QUOTA_PER_ADDRESS = 3;
    // Grace window after refresh-token rotation: a just-used token stays acceptable this long,
    // so concurrent tabs racing to refresh don't get logged out (they share one refresh token).
    private static final Duration REFRESH_ROTATION_GRACE = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMailService authMailService;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final PasswordPolicyValidator passwordPolicy;

    // One counter per recipient, both flows sharing it: the thing being rationed is somebody else's
    // inbox, not either endpoint. In memory on purpose — a restart resetting it costs nothing, and a
    // DB row per attempt would hand the flooder a write of ours for every request of theirs.
    private final Cache<String, AtomicInteger> mailQuota = Caffeine.newBuilder()
        .expireAfterWrite(MAIL_QUOTA_WINDOW)
        .maximumSize(10_000)
        .build();

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMailService authMailService,
            AdminEmailConfig adminEmailConfig,
            MessageService msg,
            PasswordPolicyValidator passwordPolicy) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMailService = authMailService;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.passwordPolicy = passwordPolicy;
    }

    // Intentionally NOT @Transactional, for the same reason as login below: the password policy calls
    // Have I Been Pwned over the network (up to a 3s timeout), and a transaction would pin a Hikari
    // connection for that entire wait. A slow or unreachable HIBP would then drain the small pool from
    // an endpoint anyone can hit, taking down every DB-backed request with it. The policy check runs
    // first, before any DB work, and the remaining writes are independent enough for per-call
    // auto-commit: a failure between saving the user and issuing the verification token leaves an
    // unverified account, which the "resend verification" flow already recovers.
    public MessageResponse register(RegisterRequest request) {
        // Before any DB access — this is the slow, network-bound check.
        passwordPolicy.validate(request.password(), request.email(), request.firstName(), request.lastName());

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException(msg.get("auth.email.exists"));
        }

        User user = new User(
            request.email(),
            request.firstName(),
            request.lastName(),
            request.phone()
        );
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredLanguage(resolveLanguage(request.preferredLanguage()));
        // Privacy policy consent is required (@AssertTrue validation) — we record the acceptance moment (GDPR).
        user.setPrivacyAcceptedAt(Instant.now());
        // Marketing consent is voluntary (opt-in) — the field is optional (may be absent) → null is treated as no consent.
        if (Boolean.TRUE.equals(request.acceptedMarketing())) {
            user.setMarketingConsentAt(Instant.now());
        }

        if (adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during registration", user.getEmail());
        }

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two submissions of the same address racing past the existence check above (a double click,
            // or the client retrying a request that already landed) hit the users.email UNIQUE index.
            // Report it as the ordinary "this e-mail is taken" 400, not a 500.
            log.debug("Registration lost the race on a duplicate e-mail: {}", user.getEmail());
            throw new IllegalArgumentException(msg.get("auth.email.exists"));
        }
        log.info("User registered: {}", user.getEmail());

        sendVerificationEmail(user);

        return new MessageResponse(msg.get("auth.register.success"));
    }

    // Intentionally NOT @Transactional: a transaction would pin a Hikari connection for the whole
    // method, including the ~100ms+ BCrypt comparison. Under a login flood that starves the small
    // pool (idle-in-transaction connections), cascading 500s to every DB-backed endpoint. Each
    // repository write here is independent, so a per-call auto-commit transaction is sufficient and
    // releases the connection before BCrypt runs.
    public AuthTokensResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null) {
            // Dummy comparison so the response time does not reveal the account's existence.
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new IllegalArgumentException(msg.get("auth.login.invalid"));
        }

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException(msg.get("auth.login.oauth"));
        }

        // The password is checked BEFORE the lockout is reported, and that order is the whole point.
        //
        // Reporting the lockout first made this endpoint the one place in the application that
        // answers "does this address have an account". Every other anti-enumeration path is
        // deliberate and documented — forgot-password, resend-verification and the marketing
        // unsubscribe all reply identically whether or not the account exists, and claimMailQuota
        // stays silent for the same reason. Here five wrong guesses were enough: an existing account
        // then answered "locked for N minutes" while an unknown address kept answering "invalid".
        //
        // Checking the secret first means the lockout notice only ever reaches somebody who already
        // proved they know the password, so it tells them nothing they did not have. To everyone
        // else a locked account, a live account with the wrong password and an address with no
        // account are one answer. The timing matches too: this branch runs BCrypt exactly like the
        // unlocked one, and an unknown address already pays for the dummy comparison above.
        //
        // The lockout itself is unchanged — it still refuses, and the counter still does not move
        // while the account is locked, so attempts cannot stack the window past 15 minutes.
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (user.isAccountLocked()) {
            log.warn("Login attempt for locked account: {}", request.email());
            if (passwordMatches) {
                throw new IllegalStateException(msg.get("auth.login.locked", user.getRemainingLockoutMinutes()));
            }
            throw new IllegalArgumentException(msg.get("auth.login.invalid"));
        }

        if (!passwordMatches) {
            log.debug("Invalid password attempt for: {}", request.email());
            // Increment failed attempts and potentially lock the account
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            if (user.isAccountLocked()) {
                log.warn("Account locked due to failed attempts: {}", request.email());
                throw new IllegalStateException(msg.get("auth.login.locked", user.getRemainingLockoutMinutes()));
            }
            throw new IllegalArgumentException(msg.get("auth.login.invalid"));
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException(msg.get("auth.email.not.verified"));
        }

        // Successful login - reset failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.resetFailedLoginAttempts();
            userRepository.save(user);
        }

        if (!user.isAdmin() && adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during login", user.getEmail());
        }

        log.info("User logged in: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        String tokenHash = jwtService.hashToken(token);

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.EMAIL_VERIFICATION, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.verify.invalid")));

        User user = authToken.getUser();
        user.markEmailVerified();
        authToken.markAsUsed();

        userRepository.save(user);
        authTokenRepository.save(authToken);

        authMailService.sendWelcomeEmail(user);
        authMailService.sendNewUserAdminNotification(user);

        log.info("Email verified for: {}", user.getEmail());
        return new MessageResponse(msg.get("auth.verify.success"));
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // Always the same message — we do not reveal whether the account exists, is already verified,
        // or whether a cooldown is in progress (anti-enumeration). We send the email only when actually needed.
        boolean shouldSend = user != null
            && !user.isEmailVerified()
            && !authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.EMAIL_VERIFICATION, Instant.now().minus(RESEND_COOLDOWN));

        if (shouldSend && claimMailQuota(request.email())) {
            sendVerificationEmail(user);
        }

        return new MessageResponse(msg.get("auth.resend.success"));
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // A uniform message regardless of account existence, OAuth login (no password),
        // or cooldown (anti-enumeration). We send the email only when actually needed.
        boolean shouldSend = user != null
            && user.getPasswordHash() != null
            && !authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.PASSWORD_RESET, Instant.now().minus(RESEND_COOLDOWN));

        if (shouldSend && claimMailQuota(request.email())) {
            sendPasswordResetEmail(user);
        }

        return new MessageResponse(msg.get("auth.forgot.success"));
    }

    /**
     * Takes one slot off the hourly per-address allowance; false means the allowance is spent and the
     * caller must not send. Counted only where a message would really go out, so probing addresses that
     * have no account costs nothing — and the answer to the caller never changes either way, because a
     * "you have had enough mail" response would be the enumeration oracle these endpoints avoid.
     */
    private boolean claimMailQuota(String email) {
        String key = email.trim().toLowerCase(Locale.ROOT);
        int used = mailQuota.get(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (used > MAIL_QUOTA_PER_ADDRESS) {
            log.warn("Suppressed an auth e-mail: hourly per-address quota spent for {}", key);
            return false;
        }
        return true;
    }

    // Stays @Transactional even though passwordPolicy.validate makes the same network call as in register:
    // the personal-data check needs the account behind the token, so validation cannot run before the
    // lookup, and splitting this into read/validate/write transactions buys little here. Unlike register,
    // this path is not floodable — it requires a valid single-use token from an e-mail we sent, on top of
    // the per-IP limit on /api/auth/. Revisit if password resets ever become a hot path.
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String tokenHash = jwtService.hashToken(request.token());

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.PASSWORD_RESET, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.reset.invalid")));

        User user = authToken.getUser();
        passwordPolicy.validate(request.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A password reset confirms mailbox access → we lift any account lockout
        // (after 5 failed attempts) so the user can log in right away.
        user.resetFailedLoginAttempts();
        authToken.markAsUsed();

        // Invalidate all refresh tokens for this user (force re-login on all devices)
        authTokenRepository.deleteByUserIdAndTokenType(user.getId(), TokenType.REFRESH_TOKEN);

        userRepository.save(user);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordChangedNotification(user);

        log.info("Password reset for: {}", user.getEmail());
        return new MessageResponse(msg.get("auth.reset.success"));
    }

    @Transactional
    public AuthTokensResponse refreshTokens(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtService.validateToken(refreshToken)) {
            throw new InvalidRefreshTokenException(msg.get("auth.refresh.invalid"));
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidRefreshTokenException(msg.get("auth.refresh.invalid.type"));
        }

        // Verify refresh token exists in database (not revoked). A token rotated within the
        // grace window is still accepted — see findRefreshableToken.
        String tokenHash = jwtService.hashToken(refreshToken);
        Instant now = Instant.now();
        AuthToken storedToken = authTokenRepository
            .findRefreshableToken(tokenHash, TokenType.REFRESH_TOKEN, now, now.minus(REFRESH_ROTATION_GRACE))
            .orElseThrow(() -> new InvalidRefreshTokenException(msg.get("auth.refresh.revoked")));

        User user = storedToken.getUser();

        // Invalidate old refresh token (rotation). Only on first use — the grace window counts
        // from the first rotation, so the token truly dies 30s after it, not later.
        if (storedToken.getUsedAt() == null) {
            storedToken.markAsUsed();
            authTokenRepository.save(storedToken);
        }

        log.debug("Tokens refreshed for: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = jwtService.hashToken(refreshToken);
        authTokenRepository.findByTokenHashAndTokenType(tokenHash, TokenType.REFRESH_TOKEN)
            .ifPresent(token -> {
                token.markAsUsed();
                authTokenRepository.save(token);
            });
    }

    private AuthTokensResponse generateTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token hash in database for revocation capability
        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        AuthToken storedRefreshToken = new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration);
        authTokenRepository.save(storedRefreshToken);

        return new AuthTokensResponse(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpirationSeconds()
        );
    }

    private void sendVerificationEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(EMAIL_VERIFICATION_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.EMAIL_VERIFICATION, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendVerificationEmail(user, token);
    }

    private void sendPasswordResetEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(PASSWORD_RESET_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.PASSWORD_RESET, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordResetEmail(user, token);
    }

    private String resolveLanguage(String requested) {
        if (requested != null && SUPPORTED_LANGUAGES.contains(requested)) {
            return requested;
        }
        return "pl";
    }
}
