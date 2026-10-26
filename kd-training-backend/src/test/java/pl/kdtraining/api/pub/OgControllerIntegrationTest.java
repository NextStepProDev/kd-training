package pl.kdtraining.api.pub;

import org.junit.jupiter.api.Test;
import pl.kdtraining.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OgControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldReturnOgHtmlForHome() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("og:title")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("KD Training")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("og:image")));
    }

    /**
     * ogResponse appends " | KD Training" to every title it renders, so a page title that already
     * carries the brand would print it twice. "KD Training" legitimately appears several times
     * elsewhere (og:site_name, the body link).
     */
    @Test
    void shouldNotRepeatTheBrandInTheTitleTag() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("KD Training | KD Training"))));
    }

    /**
     * Polish survives the round trip, and the response says so in its own header.
     * <p>
     * The document declares utf-8 in its first sixty bytes and HTML5 falls back to that when the
     * header is silent, so shared previews would render either way. It is a trap all the same —
     * anything reading the header alone (curl, a log, MockMvc) decodes the body as ISO-8859-1, so
     * the first test to assert Polish output would fail against a perfectly healthy endpoint.
     */
    @Test
    void shouldDeclareUtf8SoPolishSurvivesTheHeaderAndNotJustTheMetaTag() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/html;charset=UTF-8"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dąbrowski")));
    }

    @Test
    void shouldIncludeMetaRefreshRedirect() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("http-equiv=\"refresh\"")));
    }
}
