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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

    @Test
    void shouldListTheChangesWithPolishDatesAndNothingElse() {
        AppConfig appConfig = new AppConfig();
        appConfig.setSiteUrl("https://mocnygrzbiet.example");
        TrainingMailService service = new TrainingMailService(
                new BrandedMailSender(mailDispatcher, appConfig, msg), appConfig, msg);
        when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(msg.get(anyString(), any())).thenAnswer(inv -> inv.getArgument(0) + "[" + inv.getArgument(1) + "]");
        // 2027-03-09 is a Tuesday, 2027-03-10 a Wednesday.
        TrainingChangeDigest digest = new TrainingChangeDigest(
                List.of(new TrainingChangeDigest.Slot(LocalDate.of(2027, 3, 9), LocalTime.of(18, 0))),
                List.of(new TrainingChangeDigest.Changed(
                        new TrainingChangeDigest.Slot(LocalDate.of(2027, 3, 10), null),
                        new TrainingChangeDigest.Slot(LocalDate.of(2027, 3, 9), LocalTime.of(7, 5)))),
                List.of());

        service.sendTrainingChanges("ala@test.com", "<b>Ala</b>", digest);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher).sendHtml(eq("ala@test.com"), eq("email.training.changes.subject"), html.capture());
        String body = html.getValue();
        // The month in the genitive, the weekday spelled out: how a Polish reader expects a date.
        assertTrue(body.contains("<li>wtorek, 9 marca, 18:00</li>"), body);
        assertTrue(body.contains("<li>środa, 10 marca email.training.changes.before[wtorek, 9 marca, 07:05]</li>"), body);
        assertTrue(body.contains("email.training.changes.added"));
        assertTrue(body.contains("email.training.changes.changed"));
        // An empty section gets no heading.
        assertFalse(body.contains("email.training.changes.cancelled"));
        assertTrue(body.contains("&lt;b&gt;Ala&lt;/b&gt;"));
        assertTrue(body.contains("https://mocnygrzbiet.example/moje-konto/plan-treningowy"));
        // The text part is derived from this HTML: each slot must stay a line of its own there too.
        assertTrue(MailPlainText.fromHtml(body).contains("\n• wtorek, 9 marca, 18:00\n"), MailPlainText.fromHtml(body));
    }
}
