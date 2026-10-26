package pl.kdtraining.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.kdtraining.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that standard Spring MVC exceptions are mapped to the correct HTTP codes by
 * GlobalExceptionHandler (rather than swallowed by the catch-all as 500). Regression: registration
 * with a missing primitive field (`acceptedPrivacy`) returned 500 instead of 400.
 */
class GlobalExceptionHandlerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldReturn400WhenRegisterBodyOmitsPrimitiveField() throws Exception {
        // Missing `acceptedPrivacy` (primitive boolean) → HttpMessageNotReadableException, not 500.
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"omit-primitive@test.com","password":"StrongPass123","firstName":"Jan","lastName":"Kowalski","phone":"123456789"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn404ForUnknownApiPath() throws Exception {
        mockMvc.perform(get("/api/public/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn405ForUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(post("/sitemap.xml"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
