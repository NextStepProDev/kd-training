package pl.kdtraining.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pl.kdtraining.config.AppConfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    @Mock private JavaMailSender mailSender;

    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMail().setFrom("noreply@test.com");
        // Zero backoff — test without real waiting.
        dispatcher = new MailDispatcher(mailSender, appConfig, 3, new long[]{0, 0});
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void shouldSendOnFirstAttempt() {
        dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldRetryThenSucceed() {
        doThrow(new MailSendException("transient")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotThrowAfterExhaustingRetries() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>"));

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    // --- One-click unsubscribe headers (RFC 8058) ---

    /**
     * Both headers, and in exactly this shape: the address in angle brackets, and the second header naming
     * the field verbatim. Gmail draws no button when either is missing or malformed, and nothing in our own
     * logs would ever say so.
     */
    @Test
    void shouldPutOneClickUnsubscribeHeadersOnABulkSend() throws Exception {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);

        dispatcher.sendBulkHtml("to@test.com", "Nowy obóz", "<p>Zapraszamy</p>",
                "https://kdtraining.example/api/public/marketing/unsubscribe?token=abc-123");

        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertArrayEquals(
                new String[]{"<https://kdtraining.example/api/public/marketing/unsubscribe?token=abc-123>"},
                sent.getHeader("List-Unsubscribe"));
        assertArrayEquals(new String[]{"List-Unsubscribe=One-Click"},
                sent.getHeader("List-Unsubscribe-Post"));
    }

    /**
     * Transactional mail carries neither. There is no opting out of a verification link or an enrolment
     * confirmation, and telling a mailbox otherwise promises something we cannot honour.
     */
    @Test
    void shouldNotPutUnsubscribeHeadersOnTransactionalMail() throws Exception {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);

        dispatcher.sendHtml("to@test.com", "Potwierdź adres e-mail", "<p>Kliknij</p>");

        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertNull(sent.getHeader("List-Unsubscribe"));
        assertNull(sent.getHeader("List-Unsubscribe-Post"));
    }

    /**
     * The retry is the send nobody watches. Every attempt builds a fresh MimeMessage, so headers set once
     * outside the loop would ride on the first attempt only — and the first attempt is the one that failed.
     */
    @Test
    void shouldKeepTheHeadersOnARetriedBulkSend() throws Exception {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        doThrow(new MailSendException("transient")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        dispatcher.sendBulkHtml("to@test.com", "Nowy obóz", "<p>Zapraszamy</p>",
                "https://kdtraining.example/api/public/marketing/unsubscribe?token=abc-123");

        verify(mailSender, times(2)).send(captor.capture());
        MimeMessage retried = captor.getAllValues().get(1);
        assertArrayEquals(new String[]{"List-Unsubscribe=One-Click"},
                retried.getHeader("List-Unsubscribe-Post"));
        assertNotNull(retried.getHeader("List-Unsubscribe"));
    }
}
