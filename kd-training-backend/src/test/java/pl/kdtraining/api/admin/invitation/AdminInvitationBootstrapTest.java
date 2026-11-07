package pl.kdtraining.api.admin.invitation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.user.UserRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInvitationBootstrapTest {

    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private UserRepository userRepository;
    @Mock private InvitationService invitationService;

    @InjectMocks private AdminInvitationBootstrap bootstrap;

    @Test
    void shouldInviteAnAdminWhoHasNoAccountYet() {
        // Without this nobody could get into a fresh server: only an admin can invite.
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("coach@test.com"));
        when(userRepository.existsByEmailIgnoreCase("coach@test.com")).thenReturn(false);

        bootstrap.inviteAdminsWithoutAccount();

        verify(invitationService).invite("coach@test.com");
    }

    @Test
    void shouldLeaveAnAdminWithAnAccountAlone() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("coach@test.com"));
        when(userRepository.existsByEmailIgnoreCase("coach@test.com")).thenReturn(true);

        bootstrap.inviteAdminsWithoutAccount();

        verify(invitationService, never()).invite(anyString());
    }

    @Test
    void shouldNeverStopTheApplicationFromStarting() {
        // A listener on ApplicationReadyEvent that throws fails SpringApplication.run: a hiccup in the
        // database or a full mail queue at boot would have kept the whole site down. And one admin's
        // failure must not cost the next admin their invitation.
        when(adminEmailConfig.getAdminEmails()).thenReturn(new LinkedHashSet<>(List.of("first@test.com", "second@test.com")));
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(invitationService.invite("first@test.com")).thenThrow(new IllegalStateException("mail queue full"));

        assertDoesNotThrow(() -> bootstrap.inviteAdminsWithoutAccount());

        verify(invitationService).invite("second@test.com");
    }
}
