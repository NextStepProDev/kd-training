package pl.mocnygrzbiet.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Local development sends mail to MailHog (mocny-grzbiet-hub/docker-compose.dev.yml, SMTP on 1027),
 * never to a real server. The dev profile used to set no mail host at all, so application.yml's
 * default — smtp.gmail.com — applied: every verification and reset mail on a developer's machine
 * failed against Gmail, and with real credentials in the environment it would have gone out to
 * real addresses.
 */
class DevProfileMailConfigTest {

    private static Properties devProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        return yaml.getObject();
    }

    @Test
    void shouldSendDevMailToTheLocalMailHog() {
        Properties dev = devProfile();
        assertEquals("localhost", dev.getProperty("spring.mail.host"));
        assertEquals("1027", dev.getProperty("spring.mail.port"));
        // MailHog takes anything: no login, no TLS. With them on, the send fails before it starts.
        assertEquals("false", dev.getProperty("spring.mail.properties.mail.smtp.auth"));
        assertEquals("false", dev.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
    }
}
