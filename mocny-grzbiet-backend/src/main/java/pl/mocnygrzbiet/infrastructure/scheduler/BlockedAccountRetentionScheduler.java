package pl.mocnygrzbiet.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mocnygrzbiet.api.user.UserService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nightly: deletes accounts blocked for over a year (see User#BLOCKED_ACCOUNT_RETENTION). Holds the
 * schedule and nothing else; the transactional work is in {@link UserService}.
 */
@Component
public class BlockedAccountRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlockedAccountRetentionScheduler.class);

    private final UserService userService;

    public BlockedAccountRetentionScheduler(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "0 15 4 * * *")
    public void purge() {
        List<UUID> deleted = userService.purgeLongBlockedAccounts(Instant.now());
        if (!deleted.isEmpty()) {
            log.info("Deleted {} account(s) blocked for over a year: {}", deleted.size(), deleted);
        }
    }
}
