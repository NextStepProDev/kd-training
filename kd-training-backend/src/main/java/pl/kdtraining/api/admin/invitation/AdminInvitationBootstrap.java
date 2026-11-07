package pl.kdtraining.api.admin.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.infrastructure.mail.MailDispatcher;

/**
 * The first account on a fresh server. Registration takes an invitation and only an admin can send
 * one, so without this nobody could ever get in. At startup every configured admin address
 * (ADMIN_EMAIL) without an account gets an invitation.
 * <p>
 * It runs on every start until the admin has registered, and each run replaces the previous
 * invitation, so a lost or expired mail is fixed by restarting the backend. Once the account exists
 * this does nothing.
 * <p>
 * Production only: dev seeds the admin accounts itself (DevDataSeeder), and tests have no mail server.
 */
@Component
@Profile("prod")
public class AdminInvitationBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminInvitationBootstrap.class);

    private final AdminEmailConfig adminEmailConfig;
    private final UserRepository userRepository;
    private final InvitationService invitationService;

    public AdminInvitationBootstrap(AdminEmailConfig adminEmailConfig, UserRepository userRepository,
                                    InvitationService invitationService) {
        this.adminEmailConfig = adminEmailConfig;
        this.userRepository = userRepository;
        this.invitationService = invitationService;
    }

    // Never throws: an exception out of an ApplicationReadyEvent listener fails SpringApplication.run,
    // so a database hiccup or a full mail queue at boot would keep the whole site down. A failed
    // invitation is logged and retried by the next restart; the other admins still get theirs.
    @EventListener(ApplicationReadyEvent.class)
    public void inviteAdminsWithoutAccount() {
        for (String email : adminEmailConfig.getAdminEmails()) {
            try {
                if (!userRepository.existsByEmailIgnoreCase(email)) {
                    log.info("Admin {} has no account yet, sending an invitation", MailDispatcher.maskAddress(email));
                    invitationService.invite(email);
                }
            } catch (RuntimeException e) {
                log.error("Could not invite admin {} at startup; a restart will try again", MailDispatcher.maskAddress(email), e);
            }
        }
    }
}
