package pl.kdtraining.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.kdtraining.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldAllowPublicEndpointsWithoutAuth() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAuthEndpointsWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@x.com\",\"password\":\"12345678\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldReject401ForUserEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/user/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReject401ForAdminEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReject403ForAdminEndpointWithUserToken() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowAdminEndpointWithAdminToken() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowUserEndpointWithUserToken() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * "Bearer " with nothing after it is a 401 like any other bad credential — not a server error.
     *
     * <p>jjwt answers an empty token with IllegalArgumentException rather than a JwtException, and
     * that catch was missing, so the exception left JwtAuthenticationFilter. A filter runs before the
     * DispatcherServlet, which means @RestControllerAdvice never sees what it throws: the caller got
     * the container's own error page instead of the JSON 401 every other rejection here produces.
     */
    @Test
    void shouldRejectAnEmptyBearerTokenAsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminToAccessUserEndpoints() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturnJsonErrorForUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
