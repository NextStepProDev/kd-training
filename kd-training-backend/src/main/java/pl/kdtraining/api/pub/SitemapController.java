package pl.kdtraining.api.pub;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kdtraining.config.AppConfig;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@RestController
public class SitemapController {

    private final AppConfig appConfig;

    public SitemapController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String siteUrl = appConfig.getSiteUrl();
        String today = formatDate(Instant.now());

        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(sb, siteUrl + "/", today, "daily", "1.0");
        addUrl(sb, siteUrl + "/polityka-prywatnosci", today, "yearly", "0.3");

        sb.append("</urlset>");

        // Crawlers re-fetch this far more often than it changes, so let Cloudflare and the crawler's own
        // cache absorb the repeats.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(sb.toString());
    }

    private static void addUrl(StringBuilder sb, String loc, String lastmod, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    // ISO_LOCAL_DATE, not ISO_DATE: the latter appends the zone ("2026-10-26Z"), which is not a valid
    // W3C date for <lastmod>. The day is taken in UTC.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private static String formatDate(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
