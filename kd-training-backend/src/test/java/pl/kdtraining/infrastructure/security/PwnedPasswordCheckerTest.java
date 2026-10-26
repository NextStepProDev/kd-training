package pl.kdtraining.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PwnedPasswordCheckerTest {

    // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
    private static final String PASSWORD_SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    @Test
    void sha1HexMatchesKnownValue() {
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", PwnedPasswordChecker.sha1Hex("password"));
    }

    @Test
    void parseKeepsBreachedSuffixesAndSkipsPadding() {
        String body = PASSWORD_SUFFIX + ":42\r\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:0\r\nBBBB:7";
        Set<String> result = PwnedPasswordChecker.parse(body);
        assertTrue(result.contains(PASSWORD_SUFFIX));
        assertTrue(result.contains("BBBB"));
        assertFalse(result.contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")); // count 0 = padding
    }

    @Test
    void isPwnedReturnsTrueWhenSuffixPresent() {
        PwnedPasswordChecker checker = new PwnedPasswordChecker() {
            @Override
            protected Set<String> fetchRange(String prefix) {
                return Set.of(PASSWORD_SUFFIX);
            }
        };
        assertTrue(checker.isPwned("password"));
    }

    @Test
    void isPwnedReturnsFalseWhenSuffixAbsent() {
        PwnedPasswordChecker checker = new PwnedPasswordChecker() {
            @Override
            protected Set<String> fetchRange(String prefix) {
                return Set.of("SOMEOTHERSUFFIX");
            }
        };
        assertFalse(checker.isPwned("password"));
    }

    @Test
    void isPwnedFailsOpenWhenLookupThrows() {
        PwnedPasswordChecker checker = new PwnedPasswordChecker() {
            @Override
            protected Set<String> fetchRange(String prefix) {
                throw new RuntimeException("HIBP down");
            }
        };
        assertFalse(checker.isPwned("password")); // fail open, never blocks
    }
}
