package pl.kdtraining.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.domain.auth.AuthTokenRepository;

import java.time.Instant;

@Component
public class TokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);
    private final AuthTokenRepository authTokenRepository;

    public TokenCleanupScheduler(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = authTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired auth tokens", deleted);
        }
    }
}
