package pl.kdtraining.api.user;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;

import java.util.UUID;

/**
 * Counts a wrong current password against the login lockout, in a transaction of its own.
 *
 * <p>⚠️ Its own bean and {@code REQUIRES_NEW} on purpose. The caller refuses the request by throwing,
 * and that exception rolls the caller's transaction back — taking the count with it, so the limit
 * would never trip. {@code noRollbackFor} on the caller is not the answer either: on account
 * deletion it would also commit half a deletion if anything later in it failed. A separate bean,
 * because a {@code REQUIRES_NEW} method called on {@code this} skips the proxy and joins the outer
 * transaction anyway.
 */
@Component
public class PasswordAttempts {

    private final UserRepository userRepository;

    public PasswordAttempts(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** @return the user as stored after counting, so the caller can read the lock it may have set */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User recordFailure(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.incrementFailedLoginAttempts();
        return userRepository.save(user);
    }
}
