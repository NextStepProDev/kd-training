package pl.kdtraining.infrastructure.mail;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.infrastructure.i18n.MessageService;

@Service
public class AuthMailService {

    private final BrandedMailSender mail;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public AuthMailService(BrandedMailSender mail, AppConfig appConfig,
                           AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mail = mail;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async("mailExecutor")
    public void sendVerificationEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String verificationUrl = siteUrl + "/verify-email?token=" + token;
        String subject = msg.getForLang("email.verification.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #ffffff; color: #0a0a0a; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #a3a3a3;">%s</p>
                        <p style="font-size: 14px; color: #a3a3a3;">%s</p>
            """.formatted(
                msg.getForLang("email.verification.greeting", lang, safeFirstName),
                msg.getForLang("email.verification.body", lang),
                verificationUrl,
                msg.getForLang("email.verification.button", lang),
                msg.getForLang("email.verification.expiry", lang),
                msg.getForLang("email.verification.ignore", lang)
        );

        mail.send(user.getEmail(), subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendWelcomeEmail(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.welcome.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.getForLang("email.welcome.greeting", lang, safeFirstName),
                msg.getForLang("email.welcome.body", lang),
                msg.getForLang("email.welcome.see.you", lang)
        );

        mail.send(user.getEmail(), subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String resetUrl = siteUrl + "/reset-password?token=" + token;
        String subject = msg.getForLang("email.reset.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #ffffff; color: #0a0a0a; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #a3a3a3;">%s</p>
                        <p style="font-size: 14px; color: #a3a3a3;">%s</p>
            """.formatted(
                msg.getForLang("email.reset.greeting", lang, safeFirstName),
                msg.getForLang("email.reset.body", lang),
                msg.getForLang("email.reset.action", lang),
                resetUrl,
                msg.getForLang("email.reset.button", lang),
                msg.getForLang("email.reset.expiry", lang),
                msg.getForLang("email.reset.ignore", lang)
        );

        mail.send(user.getEmail(), subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendPasswordChangedNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.password.changed.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.getForLang("email.password.changed.greeting", lang, safeFirstName),
                msg.getForLang("email.password.changed.body", lang),
                msg.getForLang("email.password.changed.warning", lang)
        );

        mail.send(user.getEmail(), subject, brandedTemplate(content));
    }

    /**
     * Notifies the organizers about a new active account. Sent after email verification
     * (email/password flow) or directly after account creation via Google OAuth — in both
     * cases the account is already "real" (a verified mailbox owner).
     */
    @Async("mailExecutor")
    public void sendNewUserAdminNotification(User user) {
        if (adminEmailConfig.getAdminEmails().isEmpty()) {
            return;
        }
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        String subject = msg.get("email.admin.new.user.subject", fullName);

        String safeFullName = HtmlUtils.htmlEscape(fullName);
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());
        String phone = user.getPhone();
        String safePhone = (phone == null || phone.isBlank())
                ? msg.get("email.admin.new.user.phone.none")
                : HtmlUtils.htmlEscape(phone);
        String source = user.getOauthProvider() != null
                ? msg.get("email.admin.new.user.source.google")
                : msg.get("email.admin.new.user.source.email");
        String marketing = user.getMarketingConsentAt() != null
                ? msg.get("email.admin.new.user.marketing.yes")
                : msg.get("email.admin.new.user.marketing.no");

        String adminUrl = siteUrl + "/admin/uzytkownicy";

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #262626; border-radius: 8px; padding: 16px; margin: 16px 0;">
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                        </div>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #ffffff; color: #0a0a0a; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
            """.formatted(
                msg.get("email.admin.new.user.heading"),
                msg.get("email.admin.new.user.intro"),
                msg.get("email.admin.new.user.name", safeFullName),
                msg.get("email.admin.new.user.email", safeEmail),
                msg.get("email.admin.new.user.phone", safePhone),
                msg.get("email.admin.new.user.source", source),
                msg.get("email.admin.new.user.marketing", marketing),
                adminUrl,
                msg.get("email.admin.new.user.button")
        );

        String body = brandedTemplate(content);
        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            mail.send(adminEmail, subject, body);
        }
    }


    /**
     * Account mail carries no section, so it always renders in KD Training branding. The sign-off is left
     * to the shared rule: skipped when the content already closes warmly („Do zobaczenia" in the welcome
     * mail) or signs off itself.
     * <p>
     * The footer strings now resolve through {@code msg.get} rather than the user's language. With a single
     * {@code messages.properties} and Polish as the only supported language those are the same lookup; if a
     * second language is ever added, this is where account mail would need its language back.
     */
    private String brandedTemplate(String content) {
        return mail.template(content);
    }
}
