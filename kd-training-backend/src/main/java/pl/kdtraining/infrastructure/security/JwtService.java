package pl.kdtraining.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.kdtraining.domain.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final JwtConfig jwtConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TOKEN_TYPE_ACCESS, jwtConfig.getAccessTokenExpirationMs());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TOKEN_TYPE_REFRESH, jwtConfig.getRefreshTokenExpirationMs());
    }

    private String generateToken(User user, String tokenType, long expirationMs) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);

        return Jwts.builder()
            .issuer(jwtConfig.getIssuer())
            .subject(user.getId().toString())
            .claim(CLAIM_USER_ID, user.getId().toString())
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim("type", tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        return readClaims(token) != null;
    }

    /**
     * Verifies the signature once and hands back the claims, or null if the token is not usable.
     *
     * <p>Every request used to pay for this three times over: {@code validateToken},
     * {@code isAccessToken} and {@code extractUserId} each parsed the token from scratch, which means
     * three HMAC verifications and three claim decodings for one {@code Authorization} header. The
     * result was always the same — the token does not change between the three calls — and the cost
     * fell on the busiest code path in the application. The three methods stay, because they read
     * clearly at the call sites; they now share this one parse.
     */
    @Nullable
    public Claims readClaims(String token) {
        try {
            return parseToken(token);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired");
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token");
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // jjwt answers a null or empty token with IllegalArgumentException, not a JwtException,
            // and this catch was missing. The header "Authorization: Bearer " (nothing after the
            // space) therefore threw out of JwtAuthenticationFilter — which runs before the
            // DispatcherServlet, so @RestControllerAdvice never sees it and the caller gets the
            // container's own error page instead of being treated, correctly, as not logged in.
            log.debug("JWT missing or empty");
        }
        return null;
    }

    /** Whether these already-verified claims came from an access token. */
    public static boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    /** The account these already-verified claims belong to. */
    public static UUID userIdOf(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            // We stamp an issuer on every token, so we may as well insist on it coming back. It buys
            // nothing against a forged signature, but it does mean a token minted by another service
            // that happens to share this secret cannot be replayed here.
            .requireIssuer(jwtConfig.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtConfig.getAccessTokenExpirationMs() / 1000;
    }

    public long getRefreshTokenExpirationMs() {
        return jwtConfig.getRefreshTokenExpirationMs();
    }
}
