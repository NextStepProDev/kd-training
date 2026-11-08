package pl.mocnygrzbiet.infrastructure.mail;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.mocnygrzbiet.config.AppConfig;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Mail about the 1-on-1 plan. Transactional, never bulk: it concerns one conversation the client is
 * already in, so it carries no unsubscribe headers (see MailDispatcher).
 */
@Service
public class TrainingMailService {

    /** "wtorek, 12 listopada": the month in the genitive, which is how Polish writes a date. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.of("pl"));
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

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

    /**
     * "The coach changed your plan": one mail for everything since the last one (see
     * TrainingChangeMailer for the quiet period). Lists only when — dates and hours, see
     * {@link TrainingChangeDigest} for why nothing of the content goes by mail.
     *
     * @param email     captured inside the caller's transaction: no entity crosses the async boundary
     * @param firstName likewise
     */
    @Async("mailExecutor")
    public void sendTrainingChanges(String email, String firstName, TrainingChangeDigest digest) {
        StringBuilder lists = new StringBuilder();
        appendList(lists, "email.training.changes.added", digest.added().stream().map(this::slot).toList());
        appendList(lists, "email.training.changes.changed", digest.changed().stream().map(c -> c.before() == null
                ? slot(c.now())
                : slot(c.now()) + " " + msg.get("email.training.changes.before", slot(c.before()))).toList());
        appendList(lists, "email.training.changes.cancelled", digest.cancelled().stream().map(this::slot).toList());

        String content = """
                        <h1 style="color: #ffffff; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #ffffff; color: #141414; text-decoration: none; padding: 14px 32px; border-radius: 999px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #b0b0b0;">%s</p>
            """.formatted(
                msg.get("email.training.changes.greeting", HtmlUtils.htmlEscape(firstName)),
                msg.get("email.training.changes.body"),
                lists,
                siteUrl + "/moje-konto/plan-treningowy",
                msg.get("email.training.changes.button"),
                msg.get("email.training.changes.privacy")
        );
        mail.send(email, msg.get("email.training.changes.subject"), mail.template(content));
    }

    private void appendList(StringBuilder out, String headingKey, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        out.append("<p style=\"font-size: 16px; font-weight: bold; margin: 20px 0 6px;\">")
                .append(msg.get(headingKey)).append("</p>\n<ul style=\"font-size: 16px; line-height: 1.6; margin: 0; padding-left: 20px;\">\n");
        for (String item : items) {
            out.append("<li>").append(item).append("</li>\n");
        }
        out.append("</ul>\n");
    }

    /** "wtorek, 12 listopada, 18:00", or without the hour for an untimed training. */
    private String slot(TrainingChangeDigest.Slot slot) {
        String day = DAY.format(slot.date());
        return slot.start() == null ? day : day + ", " + HOUR.format(slot.start());
    }
}
