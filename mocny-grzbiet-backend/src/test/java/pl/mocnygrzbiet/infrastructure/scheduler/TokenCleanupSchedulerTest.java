package pl.mocnygrzbiet.infrastructure.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mocnygrzbiet.domain.auth.AuthTokenRepository;
import pl.mocnygrzbiet.domain.invitation.InvitationRepository;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private InvitationRepository invitationRepository;

    @InjectMocks private TokenCleanupScheduler scheduler;

    @Test
    void shouldDeleteExpiredTokens() {
        when(authTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(5);

        scheduler.cleanupExpiredTokens();

        verify(authTokenRepository).deleteExpiredTokens(any(Instant.class));
    }

    @Test
    void shouldHandleNoExpiredTokens() {
        when(authTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(0);

        scheduler.cleanupExpiredTokens();

        verify(authTokenRepository).deleteExpiredTokens(any(Instant.class));
    }

    @Test
    void shouldDeleteExpiredInvitations() {
        scheduler.cleanupExpiredTokens();

        verify(invitationRepository).deleteExpired(any(Instant.class));
    }
}
