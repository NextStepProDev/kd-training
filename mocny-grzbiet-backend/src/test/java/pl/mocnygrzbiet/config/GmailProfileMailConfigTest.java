package pl.mocnygrzbiet.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code gmail} profile is stacked on {@code dev} to send real mail from a developer's machine.
 * It must point at Gmail's submission port with login and STARTTLS switched on: Gmail refuses a plain
 * connection, and the MailHog values it overrides are the exact opposite, so a half-copied file would
 * fail only at the first send, with the developer waiting for a mail that never comes.
 */
class GmailProfileMailConfigTest {

    private static Properties gmailProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-gmail.yml"));
        return yaml.getObject();
    }

    @Test
    void shouldSendGmailProfileMailThroughAuthenticatedStartTls() {
        Properties gmail = gmailProfile();
        // Literals, not ${MAIL_HOST:...}: a .env made from .env.example carries MailHog's host and port.
        assertEquals("smtp.gmail.com", gmail.getProperty("spring.mail.host"));
        assertEquals("587", gmail.getProperty("spring.mail.port"));
        // Credentials come from the environment and nothing else: no defaults for them here.
        assertEquals("${MAIL_USERNAME}", gmail.getProperty("spring.mail.username"));
        assertEquals("${MAIL_PASSWORD}", gmail.getProperty("spring.mail.password"));
        assertEquals("true", gmail.getProperty("spring.mail.properties.mail.smtp.auth"));
        assertEquals("true", gmail.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
    }
}
