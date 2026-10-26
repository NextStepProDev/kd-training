package pl.kdtraining.api.admin.note;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import pl.kdtraining.BaseIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the {@code @PreAuthorize} on the notebook's controller is a real lock, not decoration.
 *
 * <p>Every other test reaches the controller over HTTP, where {@code /api/admin/**} in
 * {@code SecurityConfig} already refuses a non-admin. That means those tests would stay green if the
 * annotation silently did nothing — and this controller is the one place in {@code main/} that
 * carries one, described as a second lock. A claim like that has to be checked on its own, so this
 * calls the BEAN directly, with the URL rule out of the picture.
 */
class AdminPrivateNoteMethodSecurityTest extends BaseIntegrationTest {

    @Autowired
    private AdminPrivateNoteController controller;

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("someone", "n/a",
                List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    @WithAnonymousUser
    void shouldRefuseANonAdminEvenWithTheUrlRuleOutOfThePicture() {
        authenticateAs("ROLE_USER");
        try {
            assertThrows(AccessDeniedException.class,
                () -> controller.get(UUID.randomUUID(), "training", UUID.randomUUID()),
                "the @PreAuthorize on AdminPrivateNoteController is not being enforced");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldLetAnAdminThrough() {
        // The other half: a gate that refuses everyone is indistinguishable from one that works.
        // A missing training answers 404 from the service — which means authorization already passed.
        authenticateAs("ROLE_ADMIN");
        try {
            assertDoesNotThrow(() -> {
                try {
                    controller.get(adminUserId(), "training", UUID.randomUUID());
                } catch (AccessDeniedException e) {
                    throw e;
                } catch (RuntimeException expected) {
                    // NotFoundException — past the gate, which is all this test asks.
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
