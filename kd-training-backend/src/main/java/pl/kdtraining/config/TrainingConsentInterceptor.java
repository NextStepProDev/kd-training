package pl.kdtraining.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.security.JwtAuthenticatedUser;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Blocks the client side of the 1-on-1 calendar until the client has given explicit consent to
 * processing its health data (GDPR art. 9(2)(a) — see V1, users.training_consent_at).
 * <p>
 * It sits in front of {@code /api/user/my-training/**} rather than inside
 * {@link pl.kdtraining.api.trainingcalendar.TrainingAccessService} for one reason: that service's
 * {@code requireAthlete} is shared with the coach's endpoints, and the coach must keep planning for
 * a client who has not consented yet — the plan itself runs on the contract, not on consent. Only
 * the client's own access, and the health data only they can write, wait for the tick.
 * <p>
 * Guarding the path instead of each handler also means an endpoint added to
 * {@code MyTrainingController} later is covered by default, with no line to remember.
 * <p>
 * {@code /summary} is deliberately exempt: it returns bare counters for the account tile badge, no
 * calendar content, and it is polled from the account page — 409-ing it would fill the client's
 * console with errors while the consent screen is still in front of them.
 */
@Component
public class TrainingConsentInterceptor implements HandlerInterceptor {

    static final String PATH_PATTERN = "/api/user/my-training/**";
    static final String[] EXCLUDED_PATHS = {"/api/user/my-training/summary"};

    private final UserRepository userRepository;
    private final MessageService msg;

    public TrainingConsentInterceptor(UserRepository userRepository, MessageService msg) {
        this.userRepository = userRepository;
        this.msg = msg;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        UUID userId = currentUserId();
        if (userId == null) {
            // Not authenticated: the security filter chain already rejected it, or will
            return true;
        }
        User user = userRepository.findById(userId).orElse(null);
        // Not a coaching client: fall through so TrainingAccessService answers its 404. A 409 here
        // would tell an ordinary account that the feature exists and that consent is what is
        // missing — exactly the leak that 404 was chosen to avoid.
        if (user == null || !user.isAthlete()) {
            return true;
        }
        if (user.hasTrainingConsent()) {
            return true;
        }
        writeConflict(response);
        return false;
    }

    private @Nullable UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof JwtAuthenticatedUser jwtUser) {
            return jwtUser.getUserId();
        }
        if (auth.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }
        return null;
    }

    /** Same body shape as GlobalExceptionHandler's CONFLICT, so the frontend reads it identically. */
    private void writeConflict(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"CONFLICT","message":"%s","timestamp":"%s"}"""
                .formatted(msg.get("mytraining.consent.required"), Instant.now()));
    }
}
