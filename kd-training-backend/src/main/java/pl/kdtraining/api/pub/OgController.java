package pl.kdtraining.api.pub;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.kdtraining.config.AppConfig;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/og")
public class OgController {

    private final AppConfig appConfig;

    public OgController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping("/")
    public ResponseEntity<String> homeOg() {
        String siteUrl = appConfig.getSiteUrl();
        String jsonLd = """
                [{"@context":"https://schema.org","@type":"Person","name":"Kacper Dąbrowski","jobTitle":"Trener personalny","url":"%s"},{"@context":"https://schema.org","@type":"WebSite","name":"KD Training","url":"%s","inLanguage":"pl-PL"}]"""
                .formatted(siteUrl, siteUrl);
        // Placeholder copy until the client sends the real description (city, services, phone) — the
        // same sentence lives in index.html and has to change together with this one.
        return ogResponse(
                "Kacper Dąbrowski — trener personalny",
                "Trening personalny z Kacprem Dąbrowskim — indywidualny plan, materiały i kontakt z trenerem w jednym miejscu.",
                siteUrl + "/og-default.png",
                siteUrl,
                jsonLd
        );
    }

    private ResponseEntity<String> ogResponse(String title, String description, String imageUrl, String pageUrl, String jsonLd) {
        String safeTitle = escapeHtml(title);
        String safeDesc = escapeHtml(description != null ? description : "KD Training");
        String jsonLdTag = jsonLd != null
                ? "<script type=\"application/ld+json\">" + jsonLd + "</script>\n"
                : "";
        String html = """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                <meta charset="utf-8"/>
                <meta name="robots" content="index, follow"/>
                <meta name="description" content="%s"/>
                <link rel="canonical" href="%s"/>
                <meta property="og:title" content="%s"/>
                <meta property="og:description" content="%s"/>
                <meta property="og:image" content="%s"/>
                <meta property="og:url" content="%s"/>
                <meta property="og:type" content="website"/>
                <meta property="og:site_name" content="KD Training"/>
                <meta property="og:locale" content="pl_PL"/>
                %s<meta http-equiv="refresh" content="0;url=%s"/>
                <title>%s | KD Training</title>
                </head>
                <body><p>Przekierowanie do <a href="%s">KD Training</a>...</p></body>
                </html>
                """.formatted(safeDesc, pageUrl, safeTitle, safeDesc, imageUrl, pageUrl, jsonLdTag, pageUrl, safeTitle, pageUrl);

        // Charset stated in the header, not only in the <meta> tag. Both work — HTML5 falls back to
        // the meta when the header is silent, and it sits in the first sixty bytes here, which is why
        // Polish titles have always rendered correctly in shared previews. But "correct because the
        // parser guesses right" is one fallback away from wrong, and a response that does not
        // describe its own bytes reads as ISO-8859-1 to everything that goes by the header alone:
        // curl, logs, and MockMvc — where a test asserting Polish output fails while production is
        // fine, which is a trap rather than a bug report.
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
