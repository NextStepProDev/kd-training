package pl.kdtraining.infrastructure.mail;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.kdtraining.config.AppConfig;
import pl.kdtraining.infrastructure.i18n.MessageService;

/**
 * Mail about the 1-on-1 plan. Transactional, never bulk: it concerns one conversation the client is
 * already in, so it carries no unsubscribe headers (see MailDispatcher).
 */
@Service
public class TrainingMailService {

    private final BrandedMailSender mail;
    private final MessageService msg;
    private final String siteUrl;

    public TrainingMailService(BrandedMailSender mail, AppConfig appConfig, MessageService msg) {
        this.mail = mail;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    /**
     * "The coach wrote to you" — and nothing of what was written. Messages in the plan can be about
     * pain and injuries, and a mailbox is outside our control (forwarded, shared, read on a lock
     * screen), so the mail only says where to look.
     *
     * @param email     captured inside the caller's transaction: no entity crosses the async boundary
     * @param firstName likewise
     */
    @Async("mailExecutor")
    public void sendCoachReplyNotice(String email, String firstName) {
        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #ffffff; color: #141414; text-decoration: none; padding: 14px 32px; border-radius: 999px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #b0b0b0;">%s</p>
            """.formatted(
                msg.get("email.coach.reply.greeting", HtmlUtils.htmlEscape(firstName)),
                msg.get("email.coach.reply.body"),
                siteUrl + "/moje-konto/plan-treningowy",
                msg.get("email.coach.reply.button"),
                msg.get("email.coach.reply.privacy")
        );
        mail.send(email, msg.get("email.coach.reply.subject"), mail.template(content));
    }
}
