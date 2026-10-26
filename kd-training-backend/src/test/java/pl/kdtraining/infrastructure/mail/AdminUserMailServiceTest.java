package pl.kdtraining.infrastructure.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserMailServiceTest {

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Captor private ArgumentCaptor<String> subjectCaptor;
    @Captor private ArgumentCaptor<String> bodyCaptor;

    private AdminUserMailService service;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();

        when(msg.get(anyString())).thenReturn("text");
        when(msg.get(anyString(), any())).thenReturn("text");
        when(msg.get("email.bulk.signature")).thenReturn("Pozdrawiam,");
        when(msg.get("email.footer")).thenReturn("KD Training");

        service = new AdminUserMailService(new BrandedMailSender(mailDispatcher, appConfig, msg),
                adminEmailConfig, msg);
    }

    @Test
    void shouldSendCustomMessageWithBrandingAndSignature() {
        service.sendCustomMessage("jan@test.com", "Jan", "Ważna informacja", "Treść wiadomości", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("Pozdrawiam,"));
        assertTrue(body.contains("KD Training"));
        assertFalse(body.contains("/wypisz-sie"), "mail serwisowy (token null) nie ma linku rezygnacji");
    }

    @Test
    void shouldAppendUnsubscribeLinkForMarketingMessage() {
        service.sendCustomMessage("jan@test.com", "Jan", "Nowy obóz", "Zapraszamy", "tok-123");

        // Marketing goes out as a bulk send — that is what carries the one-click headers.
        verify(mailDispatcher).sendBulkHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture(), anyString());
        assertTrue(bodyCaptor.getValue().contains("/wypisz-sie?token=tok-123"),
                "mail marketingowy zawiera link rezygnacji z tokenem usera");
    }

    /**
     * The address handed to the mailbox has to be the API, not the SPA page from the footer: the mailbox
     * POSTs to it itself and runs no JavaScript, so the page would leave it with an empty document and
     * nobody unsubscribed.
     */
    @Test
    void shouldGiveTheMailboxTheApiUnsubscribeAddressNotTheSpaPage() {
        var urlCaptor = ArgumentCaptor.forClass(String.class);

        service.sendCustomMessage("jan@test.com", "Jan", "Nowy obóz", "Zapraszamy", "tok-123");

        verify(mailDispatcher).sendBulkHtml(eq("jan@test.com"), anyString(), anyString(), urlCaptor.capture());
        assertTrue(urlCaptor.getValue().endsWith("/api/public/marketing/unsubscribe?token=tok-123"),
                "nagłówek prowadzi do backendu z tokenem w URL-u, było: " + urlCaptor.getValue());
    }

    /**
     * The invariant worth a test of its own: a service message must not be sent as bulk, because that is
     * what would declare an unsubscribe address on mail nobody can unsubscribe from.
     */
    @Test
    void shouldNeverSendServiceMessageAsBulk() {
        service.sendCustomMessage("jan@test.com", "Jan", "Ważna informacja", "Treść", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
        verify(mailDispatcher, never()).sendBulkHtml(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendCampaignSequentiallyToAllRecipientsWithPerRecipientToken() {
        service.sendCampaign(java.util.List.of(
                new AdminUserMailService.CampaignRecipient("jan@test.com", "Jan", "tok-jan"),
                new AdminUserMailService.CampaignRecipient("anna@test.com", "Anna", null)
        ), "Temat", "Treść");

        var order = inOrder(mailDispatcher);
        // Per recipient the token also decides the send path: bulk (with the unsubscribe headers) for the one
        // who has it, an ordinary send for the one who does not.
        order.verify(mailDispatcher).sendBulkHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture(), anyString());
        order.verify(mailDispatcher).sendHtml(eq("anna@test.com"), anyString(), bodyCaptor.capture());
        var bodies = bodyCaptor.getAllValues();
        assertTrue(bodies.get(0).contains("/wypisz-sie?token=tok-jan"),
                "odbiorca z tokenem dostaje link rezygnacji");
        assertFalse(bodies.get(1).contains("/wypisz-sie"),
                "odbiorca bez tokenu (mail serwisowy) nie ma linku rezygnacji");
    }

    @Test
    void shouldKeepSubjectRawWithoutHtmlEscaping() {
        // A subject with Polish and special characters must stay raw (no HTML entities).
        String subject = "Zniżka 50% <wyjątkowo> ąćę";
        service.sendCustomMessage("jan@test.com", "Jan", subject, "Treść", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), subjectCaptor.capture(), anyString());
        assertEquals(subject, subjectCaptor.getValue());
    }

    @Test
    void shouldHtmlEscapeMessageBodyAndConvertNewlines() {
        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Linia 1\n<script>alert(1)</script>", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("&lt;script&gt;"), "treść powinna być escapowana");
        assertFalse(body.contains("<script>alert(1)</script>"), "surowy skrypt nie może trafić do treści");
        assertTrue(body.contains("Linia 1<br/>"), "nowe linie zamieniane na <br/>");
    }

    @Test
    void shouldTellEveryCoachThatAClientDeletedTheirAccountWithTheNameEscaped() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(java.util.Set.of("trener@test.com"));
        when(msg.get(eq("email.account.deleted.admin.body"), any(Object[].class)))
                .thenAnswer(i -> "Usunął: " + i.getArgument(1));

        service.sendAccountSelfDeletedNotification("<b>Jan</b> Kowalski", "jan@test.com");

        verify(mailDispatcher).sendHtml(eq("trener@test.com"), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("&lt;b&gt;Jan&lt;/b&gt; Kowalski"), "the client's name must be escaped");
        assertFalse(body.contains("<b>Jan</b>"), "a raw name must never reach the HTML");
    }
}
