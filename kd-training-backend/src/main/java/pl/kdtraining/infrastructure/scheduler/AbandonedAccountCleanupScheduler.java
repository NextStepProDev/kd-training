package pl.kdtraining.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.kdtraining.api.user.UserService;

/**
 * Daily cleanup of abandoned OAuth accounts (Google login) that never accepted
 * the privacy policy. GDPR: without consent we have no basis to keep the data indefinitely, and the
 * application itself does not let such an account past the consent screen anyway. The day threshold is configurable
 * (default 7) — it gives the user time to return and complete the account before deletion.
 */
@Component
public class AbandonedAccountCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(AbandonedAccountCleanupScheduler.class);

    private final UserService userService;
    private final int retentionDays;

    public AbandonedAccountCleanupScheduler(UserService userService,
                                            @Value("${app.unconsented-account-retention-days:7}") int retentionDays) {
        this.userService = userService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeAbandonedAccounts() {
        int deleted = userService.purgeAbandonedUnconsentedAccounts(retentionDays);
        if (deleted > 0) {
            log.info("Purged {} abandoned OAuth accounts without privacy consent (older than {} days)",
                    deleted, retentionDays);
        }
    }
}
