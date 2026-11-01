package pl.kdtraining.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MailPlainText Tests")
class MailPlainTextTest {

    /** Same shape as the real templates: logo link, heading, paragraphs, a button, a footer. */
    private static final String TEMPLATE = """
        <html>
        <body style="font-family: Arial, sans-serif; padding: 20px;">
            <div style="max-width: 600px;">
                <div style="text-align: center;">
                    <a href="https://kdtraining.example" style="display: inline-block;"><img src="cid:logo" alt="KD Training" style="height: 80px;" /></a>
                </div>
                <div style="padding: 20px 30px 30px;">
                <h1 style="color: #0a0a0a;">Cześć Anna!</h1>
                <p style="font-size: 16px; line-height: 1.6;">
                    Twoje konto czeka
                    na potwierdzenie &amp; aktywację.
                </p>
                <div style="text-align: center; margin: 30px 0;">
                    <a href="https://kdtraining.example/verify-email?token=abc&amp;lang=pl"
                       style="color: white;
                              display: inline-block;">
                        Potwierdź adres
                    </a>
                </div>
                <hr style="border: none;">
                <p style="font-size: 12px;">
                    KD Training<br>
                    Trening personalny
                </p>
                </div>
            </div>
        </body>
        </html>
        """;

    @Test
    @DisplayName("should turn a template into readable text with the button URL kept")
    void shouldConvertTemplate() {
        assertEquals("""
            Cześć Anna!

            Twoje konto czeka na potwierdzenie & aktywację.

            Potwierdź adres (https://kdtraining.example/verify-email?token=abc&lang=pl)

            ----------

            KD Training
            Trening personalny
            """, MailPlainText.fromHtml(TEMPLATE));
    }

    @Test
    @DisplayName("should drop the image-only logo link instead of leading with a bare URL")
    void shouldDropImageOnlyLinks() {
        String text = MailPlainText.fromHtml(TEMPLATE);

        assertTrue(text.startsWith("Cześć Anna!"));
        assertFalse(text.contains("cid:logo"));
    }

    @Test
    @DisplayName("should not repeat a URL that is already the link label")
    void shouldNotRepeatUrlLabel() {
        assertEquals("Napisz: kontakt@kdtraining.example, strona https://kdtraining.example\n",
                MailPlainText.fromHtml("<p>Napisz: <a href=\"mailto:kontakt@kdtraining.example\">kontakt@kdtraining.example</a>,"
                        + " strona <a href=\"https://kdtraining.example\">https://kdtraining.example</a></p>"));
    }

    @Test
    @DisplayName("should treat a link whose only text is a non-breaking space as having no words")
    void shouldDropNbspOnlyLinks() {
        assertEquals("Treść\n", MailPlainText.fromHtml("<p>Treść<a href=\"https://kdtraining.example\">&nbsp;</a></p>"));
    }

    @Test
    @DisplayName("should keep list items on consecutive lines and inline formatting as plain words")
    void shouldRenderListsAndInlineFormatting() {
        String html = "<p>Zabierz:</p><ul style=\"margin:8px 0;\"><li><strong>hantle</strong></li>"
                + "<li><em>mata</em></li></ul><p>Do zobaczenia</p>";

        assertEquals("Zabierz:\n\n• hantle\n• mata\n\nDo zobaczenia\n", MailPlainText.fromHtml(html));
    }

    @Test
    @DisplayName("should write out the numbers and letters the client would draw for an ordered list")
    void shouldNumberOrderedLists() {
        String html = "<ol style=\"margin:8px 0;\"><li>Zbiórka 8:00</li><li>Wyjazd 8:15</li></ol>"
                + "<ol type=\"a\" style=\"margin:8px 0;\"><li>hantle</li><li>guma</li></ol>"
                + "<ul><li>woda</li></ul>";

        assertEquals("1. Zbiórka 8:00\n2. Wyjazd 8:15\n\na) hantle\nb) guma\n\n• woda\n", MailPlainText.fromHtml(html));
    }

    @Test
    @DisplayName("should decode entities, including escaped user text, without re-reading it as markup")
    void shouldDecodeEntities() {
        assertEquals("Komentarz: <b>nie tag</b> & \"cytat\" 50%\n",
                MailPlainText.fromHtml("<p>Komentarz: &lt;b&gt;nie tag&lt;/b&gt; &amp; &quot;cytat&quot;&nbsp;50%</p>"));
    }

    @Test
    @DisplayName("should ignore head, style and comments")
    void shouldIgnoreInvisibleBlocks() {
        assertEquals("Treść\n", MailPlainText.fromHtml(
                "<html><head><title>X</title><style>p{color:red}</style></head><!-- note --><body><p>Treść</p></body></html>"));
    }
}
