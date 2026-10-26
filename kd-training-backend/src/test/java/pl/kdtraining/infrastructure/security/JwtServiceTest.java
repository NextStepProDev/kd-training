package pl.kdtraining.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        config.setAccessTokenExpirationMs(900_000);
        config.setRefreshTokenExpirationMs(604_800_000);
        config.setIssuer("kd-training-test");

        jwtService = new JwtService(config);

        testUser = new User("test@example.com", "Jan", "Kowalski", null);
        setId(testUser, UUID.randomUUID());
        testUser.setRole(UserRole.USER);
    }

    @Test
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isAccessToken(token));
        assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        String token = jwtService.generateRefreshToken(testUser);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isRefreshToken(token));
        assertFalse(jwtService.isAccessToken(token));
    }

    @Test
    void shouldExtractUserIdFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        UUID userId = jwtService.extractUserId(token);

        assertEquals(testUser.getId(), userId);
    }

    @Test
    void shouldExtractEmailFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        String email = jwtService.extractEmail(token);

        assertEquals("test@example.com", email);
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtService.validateToken("invalid.token.here"));
    }

    @Test
    void shouldRejectTokenWithWrongSecret() {
        String token = jwtService.generateAccessToken(testUser);

        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-that-is-also-at-least-32-characters-long");
        otherConfig.setAccessTokenExpirationMs(900_000);
        otherConfig.setRefreshTokenExpirationMs(604_800_000);
        otherConfig.setIssuer("kd-training-test");
        JwtService otherService = new JwtService(otherConfig);

        assertFalse(otherService.validateToken(token));
    }

    /**
     * Same secret, different issuer. Only reachable if a second service is ever configured with this
     * key — which is exactly the case worth refusing, and the reason the parser demands the claim
     * rather than merely writing it.
     */
    @Test
    void shouldRejectTokenFromAnotherIssuerSharingTheSecret() {
        JwtConfig foreignConfig = new JwtConfig();
        foreignConfig.setSecret("test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        foreignConfig.setAccessTokenExpirationMs(900_000);
        foreignConfig.setRefreshTokenExpirationMs(604_800_000);
        foreignConfig.setIssuer("some-other-service");
        String foreignToken = new JwtService(foreignConfig).generateAccessToken(testUser);

        assertFalse(jwtService.validateToken(foreignToken));
        assertFalse(jwtService.isAccessToken(foreignToken));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        config.setAccessTokenExpirationMs(0);
        config.setRefreshTokenExpirationMs(0);
        config.setIssuer("kd-training-test");
        JwtService expiredService = new JwtService(config);

        String token = expiredService.generateAccessToken(testUser);

        assertFalse(expiredService.validateToken(token));
    }

    @Test
    void shouldReturnFalseForIsAccessTokenWhenInvalidToken() {
        assertFalse(jwtService.isAccessToken("invalid.token"));
    }

    @Test
    void shouldReturnFalseForIsRefreshTokenWhenInvalidToken() {
        assertFalse(jwtService.isRefreshToken("invalid.token"));
    }

    @Test
    void shouldGenerateUniqueSecureTokens() {
        String token1 = jwtService.generateSecureToken();
        String token2 = jwtService.generateSecureToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
    }

    @Test
    void shouldHashTokenConsistently() {
        String token = "test-token-value";
        String hash1 = jwtService.hashToken(token);
        String hash2 = jwtService.hashToken(token);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void shouldProduceDifferentHashesForDifferentTokens() {
        String hash1 = jwtService.hashToken("token-a");
        String hash2 = jwtService.hashToken("token-b");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void shouldReturnCorrectExpirationSeconds() {
        assertEquals(900, jwtService.getAccessTokenExpirationSeconds());
    }

    @Test
    void shouldReturnCorrectRefreshExpirationMs() {
        assertEquals(604_800_000, jwtService.getRefreshTokenExpirationMs());
    }

    @Test
    void shouldGenerateTokenForAdminUser() {
        testUser.setRole(UserRole.ADMIN);
        String token = jwtService.generateAccessToken(testUser);

        assertTrue(jwtService.validateToken(token));
        assertEquals(testUser.getId(), jwtService.extractUserId(token));
    }

    /**
     * The single-parse entry point the filter runs on. Behaviour has to match the three String-taking
     * methods exactly, because it replaced them on the hot path: a valid access token yields claims
     * that answer the same two questions, and anything unusable yields null rather than throwing.
     */
    @Test
    void shouldReadClaimsOnceAndAnswerTheSameQuestionsAsTheStringMethods() {
        String token = jwtService.generateAccessToken(testUser);

        var claims = jwtService.readClaims(token);

        assertNotNull(claims);
        assertTrue(JwtService.isAccessToken(claims));
        assertEquals(testUser.getId(), JwtService.userIdOf(claims));
        assertEquals(jwtService.isAccessToken(token), JwtService.isAccessToken(claims));
        assertEquals(jwtService.extractUserId(token), JwtService.userIdOf(claims));
    }

    @Test
    void shouldReadNoClaimsFromAnUnusableToken() {
        assertNull(jwtService.readClaims("not-a-token"));
        assertNull(jwtService.readClaims(""));
    }

    /** A refresh token verifies fine and is still not an access token — the filter must see that. */
    @Test
    void shouldNotMistakeARefreshTokenForAnAccessToken() {
        var claims = jwtService.readClaims(jwtService.generateRefreshToken(testUser));

        assertNotNull(claims);
        assertFalse(JwtService.isAccessToken(claims));
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
