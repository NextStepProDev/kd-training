package pl.mocnygrzbiet.api.trainingcalendar;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.infrastructure.mail.TrainingMailService;

import java.time.Duration;
import java.util.UUID;

/**
 * Tells the client by e-mail that the coach wrote to them — under a training or a material. The
 * client's answer: "dla mnie na panelu, a ludzie pewnie lepiej mail", so the coach keeps the panel
 * badge and gets no mail, and the client gets one.
 * <p>
 * One mail per client per {@link #QUIET_PERIOD} at most: the coach usually answers a few things in a
 * row, and three mails saying "you have a message" say nothing the first did not. The memory of who
 * was told lives in this process on purpose, like AuthService's mail quota: a restart forgetting it
 * costs one extra mail, and a table for it would cost a write per message.
 * <p>
 * Sent after the commit, so a reply that fails to save never announces itself.
 */
@Component
public class CoachReplyNotifier {

    private static final Logger log = LoggerFactory.getLogger(CoachReplyNotifier.class);
    static final Duration QUIET_PERIOD = Duration.ofMinutes(15);

    private final TrainingMailService mail;
    private final Cache<UUID, Boolean> recentlyTold = Caffeine.newBuilder()
            .expireAfterWrite(QUIET_PERIOD)
            .maximumSize(10_000)
            .build();

    public CoachReplyNotifier(TrainingMailService mail) {
        this.mail = mail;
    }

    /** Call when the coach has written to this client; the client's own messages never come here. */
    public void coachWroteTo(User athlete) {
        // Plain values, read now: the entity must not be touched after its transaction ends.
        UUID athleteId = athlete.getId();
        String email = athlete.getEmail();
        String firstName = athlete.getFirstName();
        // The quiet period is claimed at send time, not here: a reply rolled back after this call
        // must not use up the client's next notice.
        Runnable send = () -> {
            if (recentlyTold.asMap().putIfAbsent(athleteId, Boolean.TRUE) != null) {
                return;
            }
            try {
                mail.sendCoachReplyNotice(email, firstName);
            } catch (RuntimeException e) {
                // Never out of here: this runs after the commit, and an exception would turn a saved
                // reply into a 500 the coach answers by writing it again. A notice that never left
                // must not hold the quiet period either, so the next reply tries again.
                recentlyTold.invalidate(athleteId);
                log.warn("Could not queue the coach-reply notice for user {}", athleteId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
