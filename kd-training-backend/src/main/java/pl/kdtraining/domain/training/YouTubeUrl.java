package pl.kdtraining.domain.training;

import org.jspecify.annotations.Nullable;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YouTube video id extracted from any of the shapes people paste.
 * <p>
 * Pure and dependency-free, so it can be tested exhaustively — the same parsing exists in TypeScript
 * for the live preview in the add-video form, and both sides are covered by the same case list.
 * <p>
 * Deduplication keys on the id rather than the URL: {@code watch?v=X}, {@code youtu.be/X} and
 * {@code youtu.be/X?t=30} are one video, and a coach who pastes the same clip twice in two different
 * shapes should be told so.
 */
public record YouTubeUrl(String key) {

    /** YouTube ids are 11 characters of the URL-safe base64 alphabet. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static final Pattern[] SHAPES = {
            // https://www.youtube.com/watch?v=ID  (with any other query params)
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})"),
            // https://youtu.be/ID  ·  /embed/ID  ·  /shorts/ID  ·  /live/ID  ·  /v/ID
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})"),
    };

    public static Optional<YouTubeUrl> parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        String url = rawUrl.trim();
        String host = hostOf(url);
        if (host == null || !isYouTubeHost(host)) {
            return Optional.empty();
        }
        for (Pattern shape : SHAPES) {
            Matcher m = shape.matcher(url);
            if (m.find()) {
                return Optional.of(new YouTubeUrl(m.group(1)));
            }
        }
        return Optional.empty();
    }

    /** Canonical player URL. Built from the id, so a messy pasted link never reaches an iframe. */
    public String embedUrl() {
        // nocookie: the client is watching their own training plan, not opting into ad tracking.
        return "https://www.youtube-nocookie.com/embed/" + key;
    }

    public String thumbnailUrl() {
        return "https://img.youtube.com/vi/" + key + "/hqdefault.jpg";
    }

    public static boolean isValidKey(String key) {
        return key != null && ID.matcher(key).matches();
    }

    /** Null for anything the URI parser refuses — the caller treats "no host" as "not YouTube". */
    private static @Nullable String hostOf(String url) {
        try {
            String withScheme = url.startsWith("http") ? url : "https://" + url;
            return java.net.URI.create(withScheme).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isYouTubeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        if (h.startsWith("m.")) {
            h = h.substring(2);
        }
        return h.equals("youtube.com") || h.equals("youtu.be") || h.equals("youtube-nocookie.com");
    }
}
