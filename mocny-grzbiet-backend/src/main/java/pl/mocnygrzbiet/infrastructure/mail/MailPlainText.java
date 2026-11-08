package pl.mocnygrzbiet.infrastructure.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the {@code text/plain} alternative of an outgoing mail from its HTML body.
 *
 * <p>Every template is written as HTML only; a message without a plain part is scored worse by spam
 * filters ({@code MIME_HTML_ONLY}) and is unreadable in text-only clients. Deriving it here, in
 * one place, keeps the two versions from drifting apart — a hand-written plain twin per template
 * would be a third copy of every text to forget.
 *
 * <p>The templates are our own, simple and inline-styled (no {@code <style>}, no {@code <pre>}),
 * so a tag-level conversion is enough; this is not a general HTML renderer. What it must get
 * right is the one thing a reader acts on: every button is a link, so its URL stays in the text.
 */
final class MailPlainText {

    private static final Pattern INVISIBLE_BLOCKS =
            Pattern.compile("(?is)<(head|style|script)\\b.*?</\\1\\s*>|<!--.*?-->");
    private static final Pattern LINK =
            Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a\\s*>");
    private static final Pattern IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern RULE = Pattern.compile("(?i)<hr\\b[^>]*>");
    private static final Pattern LIST_ITEM = Pattern.compile("(?i)<li\\b[^>]*>");
    private static final Pattern ORDERED_LIST = Pattern.compile("(?is)<ol\\b([^>]*)>(.*?)</ol\\s*>");
    private static final Pattern LETTERED = Pattern.compile("(?i)\\btype\\s*=\\s*[\"']?a\\b");
    private static final Pattern BLOCK_BOUNDARY =
            Pattern.compile("(?i)</?(p|div|h[1-6]|tr|table|ul|ol|blockquote)\\b[^>]*>");
    private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]+>");

    /** Private-use markers survive whitespace collapsing, so real breaks are told apart from source indentation. */
    private static final String BREAK = "\uE000";
    private static final String PARAGRAPH = "\uE001";

    private MailPlainText() {
    }

    static String fromHtml(String html) {
        String text = INVISIBLE_BLOCKS.matcher(html).replaceAll("");
        // Images go first: a logo is an <a> wrapping only an <img>, and with the image gone that
        // link has no words and is dropped instead of becoming a bare URL at the top. (The header
        // brand is text today; this keeps a future image logo from changing the plain part.)
        text = IMAGE.matcher(text).replaceAll("");
        text = replaceLinks(text);
        text = LINE_BREAK.matcher(text).replaceAll(BREAK);
        text = RULE.matcher(text).replaceAll(PARAGRAPH + "----------" + PARAGRAPH);
        text = numberOrderedLists(text);
        text = LIST_ITEM.matcher(text).replaceAll(BREAK + "• ");
        text = BLOCK_BOUNDARY.matcher(text).replaceAll(PARAGRAPH);
        text = ANY_TAG.matcher(text).replaceAll("");
        // Newlines in the source are template indentation, not line breaks — HTML collapses them too.
        text = text.replaceAll("\\s+", " ");
        text = HtmlUtils.htmlUnescape(text).replace('\u00A0', ' ');
        text = text.replace(PARAGRAPH, "\n\n").replace(BREAK, "\n");
        text = text.replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.strip() + "\n";
    }

    /**
     * In HTML the numbers of an {@code <ol>} are drawn by the client, so they are not in the text;
     * without this the plain part turns "1. Meet at 8:00, 2. Leave at 8:15" into bullets.
     * Lettered lists ({@code type="a"}) get letters; lists are not expected to nest.
     */
    private static String numberOrderedLists(String html) {
        Matcher list = ORDERED_LIST.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (list.find()) {
            boolean lettered = LETTERED.matcher(list.group(1)).find();
            Matcher item = LIST_ITEM.matcher(list.group(2));
            StringBuilder items = new StringBuilder();
            int n = 0;
            while (item.find()) {
                String marker = lettered ? (char) ('a' + n % 26) + ") " : (n + 1) + ". ";
                item.appendReplacement(items, Matcher.quoteReplacement(BREAK + marker));
                n++;
            }
            item.appendTail(items);
            list.appendReplacement(sb, Matcher.quoteReplacement(PARAGRAPH + items + PARAGRAPH));
        }
        list.appendTail(sb);
        return sb.toString();
    }

    private static String replaceLinks(String html) {
        Matcher m = LINK.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = HtmlUtils.htmlUnescape(m.group(2).strip());
            String label = ANY_TAG.matcher(m.group(3)).replaceAll("").replaceAll("\\s+", " ").strip();
            String replacement;
            // U+00A0 is not whitespace to String.strip(), so an &nbsp;-only label must be checked by hand.
            if (HtmlUtils.htmlUnescape(label).replace('\u00A0', ' ').isBlank()) {
                replacement = "";
            } else if (url.isEmpty() || url.startsWith("#")
                    || HtmlUtils.htmlUnescape(label).equals(url)
                    || ("mailto:" + HtmlUtils.htmlUnescape(label)).equals(url)) {
                replacement = label;
            } else {
                // The URL is escaped back so the later unescape pass returns it unchanged.
                replacement = label + " (" + HtmlUtils.htmlEscape(url) + ")";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
