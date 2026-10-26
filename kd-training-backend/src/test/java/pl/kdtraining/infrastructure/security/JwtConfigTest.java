package pl.kdtraining.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {

    @Test
    void shouldAcceptValidSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("a-valid-secret-key-that-is-at-least-32-characters");

        assertDoesNotThrow(config::validateSecret);
    }

    @Test
    void shouldRejectNullSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret(null);

        var ex = assertThrows(IllegalStateException.class, config::validateSecret);
        assertTrue(ex.getMessage().contains("JWT secret is not configured"));
    }

    @Test
    void shouldRejectBlankSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("   ");

        var ex = assertThrows(IllegalStateException.class, config::validateSecret);
        assertTrue(ex.getMessage().contains("JWT secret is not configured"));
    }

    @Test
    void shouldRejectShortSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("short");

        var ex = assertThrows(IllegalStateException.class, config::validateSecret);
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void shouldAcceptExactly32CharSecret() {
        JwtConfig config = new JwtConfig();
        config.setSecret("a".repeat(32));

        assertDoesNotThrow(config::validateSecret);
    }

    @Test
    void shouldStoreExpirationValues() {
        JwtConfig config = new JwtConfig();
        config.setAccessTokenExpirationMs(900_000);
        config.setRefreshTokenExpirationMs(604_800_000);
        config.setIssuer("kd-training");

        assertEquals(900_000, config.getAccessTokenExpirationMs());
        assertEquals(604_800_000, config.getRefreshTokenExpirationMs());
        assertEquals("kd-training", config.getIssuer());
    }
}
