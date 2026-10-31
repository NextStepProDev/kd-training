package pl.kdtraining.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pl.kdtraining.BaseIntegrationTest;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SitemapControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "http://localhost:5175";

    @Test
    void shouldReturnWellFormedXmlWithStaticUrls() throws Exception {
        MvcResult result = mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/xml"))
            .andReturn();

        List<String> locs = parseLocs(result);
        assertEquals(List.of(BASE + "/", BASE + "/polityka-prywatnosci", BASE + "/regulamin"), locs);
    }

    /** A W3C date is YYYY-MM-DD; ISO_DATE used to append the zone and print "2026-10-26Z". */
    @Test
    void shouldWriteLastmodAsAPlainW3cDate() throws Exception {
        String xml = mockMvc.perform(get("/sitemap.xml")).andReturn().getResponse().getContentAsString();
        var dates = java.util.regex.Pattern.compile("<lastmod>([^<]*)</lastmod>").matcher(xml).results()
            .map(m -> m.group(1)).toList();
        assertFalse(dates.isEmpty());
        dates.forEach(d -> assertTrue(d.matches("\\d{4}-\\d{2}-\\d{2}"), "not a W3C date: " + d));
    }

    /** Parses the response as XML (a hard guarantee of correct structure) and extracts all {@code <loc>}. */
    private List<String> parseLocs(MvcResult result) throws Exception {
        byte[] body = result.getResponse().getContentAsByteArray();
        var factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        assertEquals("urlset", doc.getDocumentElement().getNodeName());

        NodeList locNodes = doc.getElementsByTagName("loc");
        List<String> locs = new ArrayList<>();
        for (int i = 0; i < locNodes.getLength(); i++) {
            locs.add(((Element) locNodes.item(i)).getTextContent());
        }
        assertFalse(locs.isEmpty(), "Sitemap nie może być pusta");
        // Sanity: each <url> entry has exactly one <loc>.
        assertEquals(doc.getElementsByTagName("url").getLength(), locs.size());
        return locs;
    }
}
