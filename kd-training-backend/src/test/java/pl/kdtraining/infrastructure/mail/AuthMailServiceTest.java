package pl.kdtraining.infrastructure.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthMailServiceTest {

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;
    @Mock private AdminEmailConfig adminEmailConfig;

    private AuthMailService service;
    private User user;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.setBaseUrl("http://localhost:8083");
        appConfig.setSiteUrl("http://localhost:5175");

        service = new AuthMailService(new BrandedMailSender(mailDispatcher, appConfig, msg),
                appConfig, adminEmailConfig, msg);

        user = new User("jan@test.com", "Jan", "Kowalski", "+48123456789");
        user.setPreferredLanguage("pl");
    }

    @Test
    void shouldSendVerificationEmail() {
        when(msg.getForLang(eq("email.verification.subject"), eq("pl"))).thenReturn("Weryfikacja");
        when(msg.getForLang(eq("email.verification.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.verification.body"), eq("pl"))).thenReturn("Zweryfikuj email");
        when(msg.getForLang(eq("email.verification.button"), eq("pl"))).thenReturn("Zweryfikuj");
        when(msg.getForLang(eq("email.verification.expiry"), eq("pl"))).thenReturn("15 minut");
        when(msg.getForLang(eq("email.verification.ignore"), eq("pl"))).thenReturn("Ignoruj");

        service.sendVerificationEmail(user, "test-token-123");

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendWelcomeEmail() {
        when(msg.getForLang(eq("email.welcome.subject"), eq("pl"))).thenReturn("Witamy!");
        when(msg.getForLang(eq("email.welcome.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.welcome.body"), eq("pl"))).thenReturn("Witamy w KD Training");
        when(msg.getForLang(eq("email.welcome.see.you"), eq("pl"))).thenReturn("Do zobaczenia!");

        service.sendWelcomeEmail(user);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendPasswordResetEmail() {
        when(msg.getForLang(eq("email.reset.subject"), eq("pl"))).thenReturn("Reset hasła");
        when(msg.getForLang(eq("email.reset.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.reset.body"), eq("pl"))).thenReturn("Resetuj hasło");
        when(msg.getForLang(eq("email.reset.action"), eq("pl"))).thenReturn("Kliknij");
        when(msg.getForLang(eq("email.reset.button"), eq("pl"))).thenReturn("Resetuj");
        when(msg.getForLang(eq("email.reset.expiry"), eq("pl"))).thenReturn("1 godzina");
        when(msg.getForLang(eq("email.reset.ignore"), eq("pl"))).thenReturn("Ignoruj");

        service.sendPasswordResetEmail(user, "reset-token-456");

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendPasswordChangedNotification() {
        when(msg.getForLang(eq("email.password.changed.subject"), eq("pl"))).thenReturn("Hasło zmienione");
        when(msg.getForLang(eq("email.password.changed.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.password.changed.body"), eq("pl"))).thenReturn("Hasło zostało zmienione");
        when(msg.getForLang(eq("email.password.changed.warning"), eq("pl"))).thenReturn("Jeśli to nie Ty...");

        service.sendPasswordChangedNotification(user);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendNewUserAdminNotificationToAllAdmins() {
        when(adminEmailConfig.getAdminEmails())
            .thenReturn(Set.of("admin1@kdtraining.example", "admin2@kdtraining.example"));
        when(msg.get(eq("email.admin.new.user.subject"), eq("Jan Kowalski"))).thenReturn("Nowy użytkownik: Jan Kowalski");
        when(msg.get("email.admin.new.user.heading")).thenReturn("Nowy użytkownik dołączył do KD Training");
        when(msg.get("email.admin.new.user.intro")).thenReturn("Konto zostało właśnie aktywowane. Szczegóły:");
        when(msg.get(eq("email.admin.new.user.name"), anyString())).thenReturn("Imię i nazwisko: Jan Kowalski");
        when(msg.get(eq("email.admin.new.user.email"), anyString())).thenReturn("Email: jan@test.com");
        when(msg.get(eq("email.admin.new.user.phone"), anyString())).thenReturn("Telefon: +48123456789");
        when(msg.get("email.admin.new.user.source.email")).thenReturn("Email i hasło");
        when(msg.get(eq("email.admin.new.user.source"), anyString())).thenReturn("Sposób rejestracji: Email i hasło");
        when(msg.get("email.admin.new.user.marketing.no")).thenReturn("Nie");
        when(msg.get(eq("email.admin.new.user.marketing"), anyString())).thenReturn("Zgoda marketingowa: Nie");
        when(msg.get("email.admin.new.user.button")).thenReturn("Zobacz w panelu");
        when(msg.get("email.footer.visit")).thenReturn("Odwiedź naszą stronę");
        when(msg.get("email.footer")).thenReturn("KD Training");
        when(msg.get("email.regards")).thenReturn("Pozdrawiam,");

        service.sendNewUserAdminNotification(user);

        verify(mailDispatcher, times(2)).sendHtml(anyString(), anyString(), anyString());
    }
}
