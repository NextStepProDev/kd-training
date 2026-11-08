package pl.mocnygrzbiet.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mocnygrzbiet.api.trainingcalendar.TrainingChangeMailer;

import java.time.Instant;
import java.util.UUID;

/**
 * Every minute: mails the clients whose calendar the coach has left alone for the quiet period.
 * Holds the schedule and nothing else — the transactional work is in {@link TrainingChangeMailer},
 * reached through its proxy (see TrainingPhotoRetentionScheduler for why it cannot live here).
 * <p>
 * Each client in a transaction of their own, so one failure costs that client's mail this round
 * and nobody else's; their queue is still there for the next one.
 */
@Component
public class TrainingChangeMailScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingChangeMailScheduler.class);

    private final TrainingChangeMailer mailer;

    public TrainingChangeMailScheduler(TrainingChangeMailer mailer) {
        this.mailer = mailer;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sweep() {
        Instant cutoff = Instant.now().minus(TrainingChangeMailer.QUIET_PERIOD);
        for (UUID athleteId : mailer.athletesDue(cutoff)) {
            try {
                mailer.sendDigest(athleteId, cutoff);
            } catch (RuntimeException e) {
                log.warn("Training-change mail for user {} failed, retrying next round", athleteId, e);
            }
        }
    }
}
