package pl.kdtraining.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks a password against the Have I Been Pwned "Pwned Passwords" corpus (~900M breached
 * passwords) via the k-anonymity range API: only the first 5 chars of the password's SHA-1 hash
 * leave the server, so the password itself is never transmitted. This is the authoritative source
 * of "known-bad" passwords — it replaces hand-curated blocklists.
 *
 * Fails OPEN: if HIBP is unreachable we allow the password (and log it) rather than block a
 * legitimate signup because of an external outage. Results are cached per hash-prefix.
 */
@Component
public class PwnedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(PwnedPasswordChecker.class);
    private static final String RANGE_API = "https://api.pwnedpasswords.com/range/";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Cache<String, Set<String>> cache = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build();

    public boolean isPwned(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        try {
            String sha1 = sha1Hex(password);
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);
            Set<String> breachedSuffixes = cache.get(prefix, this::fetchRange);
            return breachedSuffixes.contains(suffix);
        } catch (RuntimeException e) {
            // Fail open: never block a real signup because HIBP is down / slow / rate-limited.
            log.warn("HIBP pwned-password check failed, allowing password: {}", e.toString());
            return false;
        }
    }

    // Protected so tests can override it without hitting the network.
    protected Set<String> fetchRange(String prefix) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RANGE_API + prefix))
                // Add-Padding hides the real result count by padding with count-0 entries (privacy).
                .header("Add-Padding", "true")
                .header("User-Agent", "kd-training-password-policy")
                .timeout(TIMEOUT)
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HIBP returned status " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HIBP request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("HIBP request failed", e);
        }
    }

    // Body lines are "HASH_SUFFIX:COUNT"; entries with count 0 are padding and must be ignored.
    static Set<String> parse(String body) {
        Set<String> suffixes = new HashSet<>();
        for (String line : body.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String count = line.substring(colon + 1).trim();
            if (!"0".equals(count)) {
                suffixes.add(line.substring(0, colon).trim().toUpperCase());
            }
        }
        return suffixes;
    }

    static String sha1Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
