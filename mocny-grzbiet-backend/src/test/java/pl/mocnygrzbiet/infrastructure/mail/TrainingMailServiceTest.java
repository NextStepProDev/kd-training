package pl.mocnygrzbiet.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mocnygrzbiet.config.AppConfig;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingMailServiceTest {

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;

    @Test
    void shouldPointToThePlanAndEscapeTheName() {
        AppConfig appConfig = new AppConfig();
        appConfig.setSiteUrl("https://mocnygrzbiet.example");
        TrainingMailService service = new TrainingMailService(
                new BrandedMailSender(mailDispatcher, appConfig, msg), appConfig, msg);
        when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(msg.get(eq("email.coach.reply.greeting"), any())).thenAnswer(inv -> "Cześć " + inv.getArgument(1));

        service.sendCoachReplyNotice("ala@test.com", "<b>Ala</b>");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher).sendHtml(eq("ala@test.com"), eq("email.coach.reply.subject"), html.capture());
        assertTrue(html.getValue().contains("https://mocnygrzbiet.example/moje-konto/plan-treningowy"));
        // A first name is user input, and this is HTML.
        assertTrue(html.getValue().contains("&lt;b&gt;Ala&lt;/b&gt;"));
        assertFalse(html.getValue().contains("<b>Ala</b>"));
    }
}
