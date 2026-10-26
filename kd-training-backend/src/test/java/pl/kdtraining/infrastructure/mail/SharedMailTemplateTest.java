package pl.kdtraining.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every mail service renders through {@link BrandedMailSender} — there is one copy of the outer HTML.
 * <p>
 * In the project this one was forked from, three of four services once carried their own copy of the
 * same skeleton. Nothing broke, which is the problem: restyling meant finding every copy, and the one
 * that got missed would only surface as a client noticing that one kind of mail looks different. This
 * test fails the moment a service starts emitting a shell the shared sender did not produce.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedMailTemplateTest {

    /** The 600px shell — the part every branded email must have in common. */
    private static final String SHELL =
            "<div style=\"max-width: 600px; margin: 0 auto; background-color: #171717;"
                    + " border-radius: 12px; overflow: hidden;\">";

    /** The brand travels as text in the header, not as an image (see BrandedMailSender). */
    private static final String BRAND = BrandedMailSender.BRAND_NAME;

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;
    @Mock private AdminEmailConfig adminEmailConfig;

    private AppConfig appConfig() {
        AppConfig c = new AppConfig();
        c.setBaseUrl("http://localhost:8083");
        c.setSiteUrl("http://localhost:5175");
        return c;
    }

    private BrandedMailSender sender() {
        when(msg.get(anyString())).thenAnswer(i -> i.getArgument(0));
        when(msg.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        when(msg.getForLang(anyString(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(msg.getForLang(anyString(), anyString(), any(Object[].class)))
                .thenAnswer(i -> i.getArgument(0));
        return new BrandedMailSender(mailDispatcher, appConfig(), msg);
    }

    private String sent() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher).sendHtml(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    private static void assertBranded(String html) {
        assertTrue(html.contains(SHELL), "email does not use the shared 600px shell");
        assertTrue(html.contains(BRAND), "email does not carry the brand name");
    }

    @Test
    void accountMailUsesTheSharedShell() {
        var service = new AuthMailService(sender(), appConfig(), adminEmailConfig, msg);
        User user = new User("jan@test.com", "Jan", "Kowalski", "+48123456789");
        user.setPreferredLanguage("pl");

        service.sendVerificationEmail(user, "tok");

        assertBranded(sent());
    }

    @Test
    void coachMessageUsesTheSharedShell() {
        var service = new AdminUserMailService(sender(), adminEmailConfig, msg);

        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);

        assertBranded(sent());
    }

    /**
     * Emails that sign off inside their own content must not get a second one from the shell — the
     * distinction the {@code signOff} flag exists for.
     */
    @Test
    void mailThatSignsItselfOffGetsNoSecondSignOff() {
        var service = new AdminUserMailService(sender(), adminEmailConfig, msg);

        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);

        String html = sent();
        assertFalse(html.contains("email.regards"),
                "the shell added a sign-off to a message that already carries one");
    }
}
