package pl.kdtraining.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pl.kdtraining.config.AppConfig;

/**
 * A shared point for sending HTML emails with retries (best-effort). Called from the {@code @Async}
 * methods of the mail services, so the retry loop runs on the {@code mailExecutor} pool thread and does not block
 * the user's request.
 * <p>
 * Transient SMTP errors (a brief Gmail unavailability, network) are the most common — a few spaced-out attempts
 * catch most of them. After the attempts are exhausted the email is dropped, but logged at ERROR level with a fixed
 * marker {@code MAIL_DELIVERY_FAILED}, so it can be caught in logs/monitoring. The method never
 * throws — sending an email must not break the business operation (whatever triggered it is already saved anyway).
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    // Delays (ms) BEFORE subsequent attempts: before the 2nd attempt, before the 3rd attempt.
    private static final long[] DEFAULT_BACKOFF_MS = {2_000L, 5_000L};

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final int maxAttempts;
    private final long[] backoffMs;

    @Autowired
    public MailDispatcher(JavaMailSender mailSender, AppConfig appConfig) {
        this(mailSender, appConfig, DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF_MS);
    }

    /** Constructor for tests — allows shortening/zeroing the retry delays. */
    MailDispatcher(JavaMailSender mailSender, AppConfig appConfig, int maxAttempts, long[] backoffMs) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** Sends an HTML email with retries on SMTP errors. Does not throw (best-effort). */
    public void sendHtml(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, null);
    }

    /**
     * A bulk send, which is the same delivery plus the two headers that make the mailbox itself offer to
     * unsubscribe (RFC 8058). Gmail draws its own "Unsubscribe" button next to the sender when it finds
     * them, and Gmail and Yahoo have required them of bulk senders since February 2024.
     * <p>
     * Their absence hurts twice over: filters score the message worse, and a reader who cannot find a way
     * out presses "report spam" instead — which is the expensive click, because it damages the reputation
     * of the whole domain and takes the verification and password-reset mail down with it.
     * <p>
     * <strong>Only for mail somebody can actually unsubscribe from.</strong> Declaring an unsubscribe
     * address on a verification or enrolment email would promise something we cannot honour, so the caller
     * decides, and {@link AdminUserMailService} decides it by the presence of an unsubscribe token.
     *
     * @param unsubscribeUrl an https address on our own backend that unsubscribes on POST — never a page
     *                       in the SPA, because the mailbox runs no JavaScript and would reach a blank
     *                       document instead of unsubscribing anybody
     */
    public void sendBulkHtml(String to, String subject, String htmlBody, String unsubscribeUrl) {
        send(to, subject, htmlBody, unsubscribeUrl);
    }

    private void send(String to, String subject, String htmlBody, @Nullable String unsubscribeUrl) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var message = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                helper.setFrom(appConfig.getMail().getFrom());
                if (unsubscribeUrl != null) {
                    // Inside the retry loop on purpose: every attempt builds a fresh MimeMessage, so headers
                    // set once outside it would ride on the first attempt only — and the retries are exactly
                    // the sends nobody watches.
                    message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
                    // Without this second header Gmail shows no button. It is what says "the address above
                    // takes a POST and needs no confirmation", which is the whole of one-click.
                    message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                }
                mailSender.send(message);
                if (attempt > 1) {
                    log.info("Email sent to {} on attempt {}/{}", to, attempt, maxAttempts);
                } else {
                    log.info("Email sent to: {}", to);
                }
                return;
            } catch (MailException | jakarta.mail.MessagingException e) {
                if (attempt < maxAttempts) {
                    long delay = backoffMs[Math.min(attempt - 1, backoffMs.length - 1)];
                    log.warn("Email to {} failed (attempt {}/{}), retrying in {} ms: {}",
                            to, attempt, maxAttempts, delay, e.getMessage());
                    if (!sleep(delay)) {
                        return; // interrupted while waiting
                    }
                } else {
                    log.error("MAIL_DELIVERY_FAILED to={} after {} attempts", to, maxAttempts, e);
                }
            }
        }
    }

    /** @return false when the thread was interrupted while waiting. */
    private boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Mail retry backoff interrupted; aborting send");
            return false;
        }
    }
}
