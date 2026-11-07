package pl.kdtraining.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.domain.auth.AuthTokenRepository;
import pl.kdtraining.domain.invitation.InvitationRepository;

import java.time.Instant;

@Component
public class TokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);
    private final AuthTokenRepository authTokenRepository;
    private final InvitationRepository invitationRepository;

    public TokenCleanupScheduler(AuthTokenRepository authTokenRepository, InvitationRepository invitationRepository) {
        this.authTokenRepository = authTokenRepository;
        this.invitationRepository = invitationRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = authTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired auth tokens", deleted);
        }
        // An expired invitation still holds an address of somebody who never signed up.
        int invitations = invitationRepository.deleteExpired(Instant.now());
        if (invitations > 0) {
            log.info("Cleaned up {} expired invitations", invitations);
        }
    }
}
