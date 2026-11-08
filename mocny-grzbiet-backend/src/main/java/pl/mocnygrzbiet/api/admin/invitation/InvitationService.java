package pl.mocnygrzbiet.api.admin.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mocnygrzbiet.api.NotFoundException;
import pl.mocnygrzbiet.api.admin.invitation.InvitationDtos.InvitationResponse;
import pl.mocnygrzbiet.domain.invitation.Invitation;
import pl.mocnygrzbiet.domain.invitation.InvitationRepository;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.infrastructure.i18n.MessageService;
import pl.mocnygrzbiet.infrastructure.mail.AuthMailService;
import pl.mocnygrzbiet.infrastructure.mail.MailDispatcher;
import pl.mocnygrzbiet.infrastructure.security.JwtService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Accounts are created by invitation only (the client's answer: the site is for his current clients
 * first). The coach invites an address, the mail carries a single-use link, and registration accepts
 * nothing else. Opening the link also proves the mailbox, so an invited account needs no separate
 * e-mail verification.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    // Long enough for somebody who reads mail once a week, short enough that a forgotten link in an
    // old inbox stops working.
    static final Duration INVITATION_EXPIRATION = Duration.ofDays(7);

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthMailService authMailService;
    private final MessageService msg;

    public InvitationService(InvitationRepository invitationRepository, UserRepository userRepository,
                             JwtService jwtService, AuthMailService authMailService, MessageService msg) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authMailService = authMailService;
        this.msg = msg;
    }

    /**
     * Invites an address, or invites it again: the previous invitation is deleted, so only the newest
     * link works. Refused for an address that already has an account.
     */
    @Transactional
    public InvitationResponse invite(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException(msg.get("invitation.user.exists"));
        }

        // A bulk DELETE runs at once, while the INSERT below waits for the flush, so the unique index
        // on the address never sees the old row and the new one together.
        invitationRepository.deleteByEmail(email);

        String token = jwtService.generateSecureToken();
        Invitation invitation = invitationRepository.save(
            new Invitation(email, jwtService.hashToken(token), Instant.now().plus(INVITATION_EXPIRATION)));

        authMailService.sendInvitationEmail(email, token);
        log.info("Invitation sent to {}", MailDispatcher.maskAddress(email));
        return toResponse(invitation);
    }

    /** Open invitations, newest first. Expired ones are left out: their link no longer works. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> listOpen() {
        return invitationRepository.findAllValid(Instant.now()).stream().map(InvitationService::toResponse).toList();
    }

    @Transactional
    public void cancel(UUID id) {
        if (invitationRepository.deleteByIdReturningCount(id) == 0) {
            throw new NotFoundException(msg.get("invitation.not.found"));
        }
    }

    /** The invitation behind a link, if the link is still good. */
    public Optional<Invitation> findValid(String token) {
        return invitationRepository.findValid(jwtService.hashToken(token), Instant.now());
    }

    /**
     * Uses the invitation up, once the account exists. A second registration racing on the same link
     * is stopped by the unique address on users, not here, so there is nothing to report back.
     */
    @Transactional
    public void consume(Invitation invitation) {
        invitationRepository.deleteByIdReturningCount(invitation.getId());
    }

    /** The open invitation for an address, for a sign-up that does not go through the link (Google). */
    public Optional<Invitation> findValidForEmail(String email) {
        return invitationRepository.findValidForEmail(email, Instant.now());
    }

    private static InvitationResponse toResponse(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getCreatedAt(), invitation.getExpiresAt());
    }
}
