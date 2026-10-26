package pl.kdtraining.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.kdtraining.api.trainingcalendar.TrainingPhotoRetentionService;

/**
 * Nightly trigger for the training photo passes. Holds the schedule and nothing else.
 * <p>
 * The work itself lives in {@link TrainingPhotoRetentionService} deliberately. Both passes are
 * transactional, and a transaction only exists when a call arrives through Spring's proxy — a pass
 * kept here and invoked from {@code sweep()} would be a plain call on {@code this}, running with the
 * annotation quietly doing nothing. Keeping the work in another bean removes the possibility rather
 * than leaving it as something to remember.
 */
@Component
public class TrainingPhotoRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingPhotoRetentionScheduler.class);

    private final TrainingPhotoRetentionService retention;

    public TrainingPhotoRetentionScheduler(TrainingPhotoRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "0 45 3 * * *")
    public void sweep() {
        int expired = retention.deleteExpired();
        int orphans = retention.deleteOrphans();
        if (expired > 0 || orphans > 0) {
            log.info("Training photo sweep: {} expired, {} orphaned file(s) removed", expired, orphans);
        }
    }
}
