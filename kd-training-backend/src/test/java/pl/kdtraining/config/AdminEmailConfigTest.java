package pl.kdtraining.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminEmailConfigTest {

    @Test
    void shouldParseMultipleAdminEmails() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("admin@test.com, super@test.com");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertEquals(2, config.getAdminEmails().size());
        assertTrue(config.isAdminEmail("admin@test.com"));
        assertTrue(config.isAdminEmail("super@test.com"));
    }

    @Test
    void shouldNormalizeTolowercase() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("Admin@Test.COM");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertTrue(config.isAdminEmail("admin@test.com"));
        assertTrue(config.isAdminEmail("ADMIN@TEST.COM"));
    }

    @Test
    void shouldTrimWhitespace() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("  admin@test.com  ,  other@test.com  ");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertTrue(config.isAdminEmail("admin@test.com"));
        assertTrue(config.isAdminEmail("other@test.com"));
    }

    @Test
    void shouldHandleEmptyConfig() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertTrue(config.getAdminEmails().isEmpty());
        assertFalse(config.isAdminEmail("admin@test.com"));
    }

    @Test
    void shouldHandleNullConfig() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail(null);

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertTrue(config.getAdminEmails().isEmpty());
    }

    @Test
    void shouldHandleBlankConfig() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("   ");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertTrue(config.getAdminEmails().isEmpty());
    }

    @Test
    void shouldReturnFalseForNullEmail() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("admin@test.com");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertFalse(config.isAdminEmail(null));
    }

    @Test
    void shouldReturnFalseForNonAdminEmail() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("admin@test.com");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertFalse(config.isAdminEmail("user@test.com"));
    }

    @Test
    void shouldHandleSingleEmail() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("admin@test.com");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertEquals(1, config.getAdminEmails().size());
        assertTrue(config.isAdminEmail("admin@test.com"));
    }

    @Test
    void shouldFilterEmptyEntriesInList() {
        AppConfig appConfig = new AppConfig();
        appConfig.getAdmin().setEmail("admin@test.com,,, ,other@test.com");

        AdminEmailConfig config = new AdminEmailConfig(appConfig);

        assertEquals(2, config.getAdminEmails().size());
    }
}
