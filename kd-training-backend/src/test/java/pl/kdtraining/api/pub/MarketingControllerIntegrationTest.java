package pl.kdtraining.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRole;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketingControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldRevokeMarketingConsentWithoutAuthenticationWhenTokenIsValid() throws Exception {
        User user = createUserWithMarketingConsent("opted-in@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(user.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldReturnNoContentForUnknownTokenWithoutLeakingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(UUID.randomUUID())))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNoContentForMalformedTokenWithoutLeakingValidation() throws Exception {
        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"not-a-uuid\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldBeIdempotentWhenUserHasNoMarketingConsent() throws Exception {
        User user = createUserWithoutMarketingConsent("never-opted-in@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(user.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldNotAffectOtherUsersConsentWhenOneUnsubscribes() throws Exception {
        User target = createUserWithMarketingConsent("target@test.com");
        User bystander = createUserWithMarketingConsent("bystander@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(target.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(bystander.getId()).orElseThrow().hasMarketingConsent());
    }

    // --- One-click unsubscribe, the way a mailbox sends it (RFC 8058) ---

    /**
     * What Gmail actually sends when someone presses its own "Unsubscribe" button: a POST with the token in
     * the query string and the fixed form body. No JSON anywhere, because no JavaScript ran.
     */
    @Test
    void shouldUnsubscribeFromTheOneClickPostSentByAMailbox() throws Exception {
        User user = createUserWithMarketingConsent("one-click@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .param("token", user.getMarketingUnsubscribeToken().toString())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click"))
            .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    /**
     * Providers retry, and some POST twice on one press. The second time must be as quiet as the first — a
     * 500 would be recorded as a failed unsubscribe and counted against the sender.
     */
    @Test
    void shouldStayQuietWhenTheSameOneClickArrivesTwice() throws Exception {
        User user = createUserWithMarketingConsent("twice@test.com");
        String token = user.getMarketingUnsubscribeToken().toString();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/public/marketing/unsubscribe")
                    .param("token", token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("List-Unsubscribe=One-Click"))
                .andExpect(status().isNoContent());
        }

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldNotUnsubscribeAnybodyOnAnUnknownOneClickToken() throws Exception {
        User bystander = createUserWithMarketingConsent("bystander-one-click@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .param("token", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click"))
            .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(bystander.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldStayQuietOnAMalformedOneClickToken() throws Exception {
        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .param("token", "not-a-uuid")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("List-Unsubscribe=One-Click"))
            .andExpect(status().isNoContent());
    }

    /**
     * Older mail clients show the header address as an ordinary link, so a human can land here in a browser.
     * They get the page built for them — and crucially the GET revokes nothing, which is why unsubscribing is
     * a POST in the first place: scanners and prefetchers follow GETs on their own.
     */
    @Test
    void shouldRedirectAHumanToTheUnsubscribePageWithoutRevokingConsent() throws Exception {
        User user = createUserWithMarketingConsent("browser@test.com");
        String token = user.getMarketingUnsubscribeToken().toString();

        mockMvc.perform(get("/api/public/marketing/unsubscribe").param("token", token))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://localhost:5175/wypisz-sie?token=" + token));

        assertTrue(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent(),
            "a GET must never unsubscribe — prefetchers follow links");
    }

    private User createUserWithMarketingConsent(String email) {
        User user = new User(email, "Jan", "Kowalski", null);
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        user.setMarketingConsentAt(Instant.now());
        return userRepository.save(user);
    }

    private User createUserWithoutMarketingConsent(String email) {
        User user = new User(email, "Anna", "Nowak", null);
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        return userRepository.save(user);
    }
}
