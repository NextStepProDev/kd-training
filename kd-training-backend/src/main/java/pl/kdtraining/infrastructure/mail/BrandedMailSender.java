package pl.kdtraining.infrastructure.mail;

import org.springframework.stereotype.Component;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.infrastructure.i18n.MessageService;

/**
 * Shared branding and email sending (black and white), and the only copy of the outer HTML.
 * <p>
 * Every mail service renders through here: account mail ({@link AuthMailService}) and the coach's own
 * messages ({@link AdminUserMailService}). One skeleton, so restyling is one edit and no class of
 * emails can quietly look different.
 * <p>
 * The header carries the brand name as TEXT, not an image: there is no logo yet, and mail clients
 * block remote images by default anyway, so text is what most recipients would see first.
 * <p>
 * Sending delegates to {@link MailDispatcher}, so every email gets the same retrying on transient SMTP
 * failures.
 */
@Component
public class BrandedMailSender {

    static final String BRAND_NAME = "KD Training";

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final MessageService msg;

    public BrandedMailSender(MailDispatcher mailDispatcher, AppConfig appConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    public String siteUrl() {
        return appConfig.getSiteUrl();
    }

    /**
     * By default skips the sign-off if the content already has a warm closing („Do zobaczenia")
     * or its own sign-off („Pozdrawiam"), to avoid duplicating courtesy phrases.
     */
    public String template(String content) {
        String lower = content.toLowerCase();
        boolean signOff = !lower.contains("do zobaczenia") && !lower.contains("pozdrawiam");
        return template(content, signOff);
    }

    /**
     * @param signOff whether to add the „Pozdrawiam, KD Training" sign-off. Skipped for emails that
     *                already end with a warm closing („Do zobaczenia!") or have their own sign-off.
     */
    public String template(String content, boolean signOff) {
        String siteUrl = appConfig.getSiteUrl();
        // Joined into the content rather than given its own slot: an empty slot would still emit its
        // line of indentation, and a mail without a sign-off should render byte-for-byte as it always did.
        String body = signOff
                ? content + "\n            "
                        + "<p style=\"font-size: 15px; line-height: 1.6; margin: 24px 0 0;\">%s<br/><strong>%s</strong></p>"
                                .formatted(msg.get("email.regards"), BRAND_NAME)
                : content;
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0a0a0a; color: #e5e5e5; padding: 20px; margin: 0;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #171717; border-radius: 12px; overflow: hidden;">
                    <div style="background-color: #000000; padding: 24px 30px; text-align: center; border-bottom: 1px solid #404040;">
                        <a href="%s" style="color: #ffffff; text-decoration: none; font-size: 22px; font-weight: bold; letter-spacing: 2px; text-transform: uppercase;">%s</a>
                    </div>
                    <div style="padding: 30px;">
                        %s
                        <hr style="border-color: #404040; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #a3a3a3; text-align: center; margin: 4px 0;">
                            <a href="%s" style="color: #ffffff; text-decoration: underline;">%s</a>
                        </p>
                        <p style="font-size: 12px; color: #a3a3a3; text-align: center; margin: 4px 0;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(siteUrl, BRAND_NAME, body,
                    siteUrl, msg.get("email.footer.visit"), msg.get("email.footer"));
    }

    public void send(String to, String subject, String htmlBody) {
        mailDispatcher.sendHtml(to, subject, htmlBody);
    }

    /**
     * A bulk send: the same message, plus the headers that let the mailbox offer its own unsubscribe button
     * (see {@link MailDispatcher#sendBulkHtml}). Reserved for mail the recipient can actually opt out of.
     */
    public void sendBulk(String to, String subject, String htmlBody, String unsubscribeUrl) {
        mailDispatcher.sendBulkHtml(to, subject, htmlBody, unsubscribeUrl);
    }
}
