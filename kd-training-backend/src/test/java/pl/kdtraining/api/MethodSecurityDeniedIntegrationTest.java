package pl.kdtraining.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kdtraining.BaseIntegrationTest;
import pl.kdtraining.domain.user.UserRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a caller is told when {@code @PreAuthorize} refuses them.
 *
 * <h2>Why the endpoint under test is invented here</h2>
 * The only {@code @PreAuthorize} in {@code main/} sits under {@code /api/admin/**}, and the filter
 * chain closes that prefix a level up — so a stranger is turned away before any controller runs and
 * the annotation never actually decides anything over HTTP. Testing it there would prove the URL
 * rule works and say nothing about method security. The controller below lives outside that prefix,
 * which puts the annotation on its own, exactly as it would be the first time somebody adds one to
 * a user-facing endpoint.
 *
 * <p>{@code AdminPrivateNoteMethodSecurityTest} is the neighbour to this one and covers the other
 * half: it invokes the bean directly and asserts the annotation throws at all. That path never
 * reaches the DispatcherServlet, which is where the status is decided — so neither test replaces
 * the other.
 */
@Import(MethodSecurityDeniedIntegrationTest.GuardedEndpoint.class)
class MethodSecurityDeniedIntegrationTest extends BaseIntegrationTest {

    @TestConfiguration
    static class GuardedEndpoint {
        @RestController
        static class Controller {
            @GetMapping("/api/probe/admin-only")
            @PreAuthorize("hasRole('ADMIN')")
            public String adminOnly() {
                return "\"ok\"";
            }
        }
    }

    @Test
    void shouldAnswerForbiddenWhenMethodSecurityRefuses() throws Exception {
        String user = createUserAndGetToken("denied@kdtraining.test", "Odmowa", "Testowa", UserRole.USER);

        mockMvc.perform(get("/api/probe/admin-only").header("Authorization", "Bearer " + user))
            // Was 500: @PreAuthorize throws inside the controller, so the exception surfaces in the
            // DispatcherServlet and GlobalExceptionHandler's catch-all claimed it before Spring
            // Security's translator could turn it into a 403.
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            // The localized message, never the exception's own "Access Denied".
            .andExpect(jsonPath("$.message").value("Nie masz uprawnień do tego zasobu"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Access Denied"))));
    }

    /** A gate that refuses everyone is indistinguishable from one that is simply broken. */
    @Test
    void shouldStillLetAnAdminThrough() throws Exception {
        mockMvc.perform(get("/api/probe/admin-only").header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk());
    }

    /**
     * One answer whichever lock refused.
     * <p>
     * The filter chain writes its own body in {@code SecurityConfig}; this advice writes another.
     * Two spellings of one answer drift, and the frontend reads {@code code} and {@code message} off
     * both without knowing which produced it.
     * <p>
     * Compared field by field rather than as raw text: {@code SecurityConfig} builds its body with
     * {@code Map.of}, whose iteration order is unspecified and varies between JVM runs, so a string
     * comparison would pin something nobody promised and fail at random.
     */
    @Test
    void shouldGiveTheSameAnswerAsTheFilterChainForTheSameRefusal() throws Exception {
        String user = createUserAndGetToken("denied@kdtraining.test", "Odmowa", "Testowa", UserRole.USER);

        String fromMethodSecurity = mockMvc.perform(
                get("/api/probe/admin-only").header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String fromFilterChain = mockMvc.perform(
                get("/api/admin/athletes").header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        for (String field : new String[] {"code", "message"}) {
            org.junit.jupiter.api.Assertions.assertEquals(
                (String) com.jayway.jsonpath.JsonPath.read(fromFilterChain, "$." + field),
                (String) com.jayway.jsonpath.JsonPath.read(fromMethodSecurity, "$." + field),
                "the two refusals must be indistinguishable to the caller: " + field);
        }
    }
}
