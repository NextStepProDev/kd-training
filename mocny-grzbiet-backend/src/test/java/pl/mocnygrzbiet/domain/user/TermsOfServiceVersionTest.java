package pl.mocnygrzbiet.domain.user;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The version that makes everyone accept the terms again and the date the terms page prints are typed
 * in two languages, in two modules. Changing the text and bumping only the page would show a new date
 * while nobody is asked to accept it — the failure is silent, so it is caught here.
 * <p>
 * The page is read from the repository (CI checks out the whole of it). A missing file fails rather
 * than skips: a skipped guard looks exactly like a passing one.
 */
class TermsOfServiceVersionTest {

    private static final Path TERMS_PAGE = Path.of("../mocny-grzbiet-frontend/src/pages/TermsPage.tsx");
    private static final Pattern LAST_UPDATED = Pattern.compile("const LAST_UPDATED = '([^']+)'");

    @Test
    void shouldPrintTheVersionInForceAsTheTermsPageDate() throws Exception {
        assertTrue(Files.exists(TERMS_PAGE), "terms page not found at " + TERMS_PAGE.toAbsolutePath());
        Matcher m = LAST_UPDATED.matcher(Files.readString(TERMS_PAGE));
        assertTrue(m.find(), "LAST_UPDATED not found in TermsPage.tsx");

        String expected = LocalDate.parse(TermsOfService.CURRENT_VERSION)
                .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("pl")));
        assertEquals(expected, m.group(1),
                "TermsPage.tsx LAST_UPDATED and TermsOfService.CURRENT_VERSION must move together");
    }
}
