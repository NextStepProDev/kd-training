package pl.mocnygrzbiet.api.admin.invitation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import pl.mocnygrzbiet.BaseIntegrationTest;
import pl.mocnygrzbiet.domain.invitation.Invitation;
import pl.mocnygrzbiet.domain.invitation.InvitationRepository;
import pl.mocnygrzbiet.domain.user.UserRole;
import pl.mocnygrzbiet.infrastructure.mail.AuthMailService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminInvitationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private InvitationRepository invitationRepository;

    // The mail is the only place the raw token ever goes; capturing it is how a test gets the link.
    @MockitoBean private AuthMailService authMailService;

    private ResultActions invite(String token, String email) throws Exception {
        return mockMvc.perform(post("/api/admin/invitations")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s"}
                """.formatted(email)));
    }

    @Test
    void shouldInviteAnAddressAndMailTheLink() throws Exception {
        invite(adminToken(), "Nowy@Test.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("nowy@test.com"))
            .andExpect(jsonPath("$.expiresAt").exists());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(authMailService).sendInvitationEmail(eq("nowy@test.com"), link.capture());
        Invitation stored = invitationRepository.findAll().getFirst();
        assertNotEquals(link.getValue(), stored.getTokenHash(), "the raw token never reaches the database");
        assertEquals(jwtService.hashToken(link.getValue()), stored.getTokenHash());
    }

    @Test
    void shouldReplaceThePreviousInvitationWhenInvitingAgain() throws Exception {
        String admin = adminToken();
        AtomicReference<String> firstLink = new AtomicReference<>();
        doAnswer(inv -> { firstLink.compareAndSet(null, inv.getArgument(1)); return null; })
            .when(authMailService).sendInvitationEmail(anyString(), anyString());

        invite(admin, "again@test.com").andExpect(status().isOk());
        invite(admin, "Again@test.com").andExpect(status().isOk());

        assertEquals(1, invitationRepository.count(), "one open invitation per address");
        // The first link is dead: only the newest mail works.
        mockMvc.perform(post("/api/auth/invitation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"%s"}
                    """.formatted(firstLink.get())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefuseToInviteAnAddressThatHasAnAccount() throws Exception {
        String admin = adminToken();
        createUserAndGetToken("member@test.com", "Jan", "Kowalski", UserRole.USER);

        invite(admin, "member@test.com")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertEquals(0, invitationRepository.count());
        verify(authMailService, never()).sendInvitationEmail(anyString(), anyString());
    }

    @Test
    void shouldListOnlyOpenInvitations() throws Exception {
        String admin = adminToken();
        invitationRepository.saveAll(List.of(
            new Invitation("open@test.com", "a".repeat(64), Instant.now().plusSeconds(3600)),
            new Invitation("expired@test.com", "b".repeat(64), Instant.now().minusSeconds(1))));

        mockMvc.perform(get("/api/admin/invitations").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("open@test.com"));
    }

    @Test
    void shouldCancelAnInvitation() throws Exception {
        String admin = adminToken();
        Invitation invitation = invitationRepository.save(
            new Invitation("cancel@test.com", "c".repeat(64), Instant.now().plusSeconds(3600)));

        mockMvc.perform(delete("/api/admin/invitations/" + invitation.getId()).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        assertEquals(0, invitationRepository.count());

        mockMvc.perform(delete("/api/admin/invitations/" + invitation.getId()).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepInvitationsForAdminsOnly() throws Exception {
        String user = createUserAndGetToken("plain@test.com", "Jan", "Kowalski", UserRole.USER);

        invite(user, "someone@test.com").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/invitations").header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden());
    }
}
