package pl.kdtraining.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Per-IP request throttling, keyed on the real client address and bucketed by path.
 *
 * <p><strong>Deny by default.</strong> Anything under {@code /api} that no rule claims falls into
 * {@link #DEFAULT_LIMIT} rather than through the filter untouched. The rule table used to end in
 * "no prefix matched, so no ceiling", which meant every new controller started life completely
 * unthrottled and nothing at the call site looked wrong — the omission was invisible until someone
 * audited the filter. The generic bucket makes the gap survivable; {@code RateLimitCoverageTest}
 * makes it visible, by failing the build when a controller base path lands in it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
        Locale.of("pl")
    );

    private static final int AUTH_LIMIT = 15;
    // Reads and writes share this one counter, and they cannot be split by path: a rule here is a
    // Predicate over the URI alone. Every page load spends one on /user/me without anyone asking for
    // it, so the ceiling has to leave room for browsing on top of the actual account operations.
    private static final int USER_LIMIT = 40;
    private static final int ADMIN_LIMIT = 60;
    // The personal training calendar needs its own budget. Browsing it generates far more requests than
    // the rest of /api/user/** put together (range fetch per week/month navigation, unread summary on every
    // window focus, mark-seen, comment threads), and sharing the user bucket above would lock a coaching
    // client out of their own plan after a couple of minutes of normal use.
    private static final int MY_TRAINING_LIMIT = 120;
    // Anonymous traffic (marketing unsubscribe, OG stubs, sitemap). Set well above what a person browsing
    // the site generates, so it never bites a real visitor — it exists to cap a flood. The unsubscribe
    // endpoint hits the DB, so without a ceiling an unauthenticated client could drain the small Hikari
    // pool on its own.
    private static final int PUBLIC_LIMIT = 120;
    // Every file upload in the app, whoever sends it. Every other ceiling here rations cheap requests;
    // this one rations bytes arriving on a 1 GB box, and the request is parsed into memory (up to
    // 10 MB of it) before any handler can reject it. Twelve a minute is far more than a person
    // attaching screenshots to a session or changing their profile picture will ever need — the
    // per-training cap of three is what shapes normal use — and it puts a hard number on the question
    // an audit asks: what stops a client uploading thousands of files?
    //
    // The rule below must list EVERY multipart endpoint. Missing one does not fail, warn, or show up
    // anywhere: the request simply gets measured by whatever roomier ceiling its prefix happens to
    // have, so the ration meant to protect memory covers some of the doors into the same expensive
    // operation and not others. That is exactly how the avatar upload sat outside it for months.
    private static final int UPLOAD_LIMIT = 12;
    // Public image streaming (/api/files/**). Deliberately generous: a page can pull a dozen files at
    // once and Cloudflare only shields the hits — a request for a name that is not on disk misses the edge
    // cache every time and reaches the JVM, where each one costs a thread and a stat() on a 1 GB box. This
    // is a ceiling on a flood, not a brake on browsing.
    private static final int FILES_LIMIT = 240;
    // The catch-all. Deliberately generous: it exists to stop a script, not to surprise a household or an
    // office behind one NAT address. A path that reaches it has no ceiling anyone chose for it, so the
    // number has to be safe for traffic nobody has thought about yet.
    private static final int DEFAULT_LIMIT = 120;

    /**
     * One bucket, one limit, one predicate — so a request can no longer be counted into one bucket
     * while being measured against another's ceiling. That was the standing hazard of the two
     * parallel {@code if} ladders this replaced: they had to test the same prefixes in the same
     * order, and nothing but a comment held them together.
     */
    record Rule(String bucket, int limit, Predicate<String> matches) {}

    /**
     * First match wins, so the narrowest rules come first: the upload paths sit inside the calendar
     * and admin prefixes, and would otherwise inherit their far roomier ceilings.
     *
     * <p>Reading a photo is deliberately NOT in the upload bucket — it lives at
     * {@code .../comments/{id}/photo} and stays on the ordinary calendar ceiling, because opening a
     * handful of trainings is already a dozen GETs. Only the multipart writes are rationed.
     */
    private static final List<Rule> RULES = List.of(
        // The avatar is the third way to put a file on this server, and it is the same cost as the
        // other two: a multipart read into memory before anything can refuse it. It counted against
        // the ordinary user ceiling for no reason anybody chose — the ration was written when the
        // training photos were added and named the two paths that existed then. Its DELETE lands here
        // too, because the rules match on path alone; nobody removes a profile picture twelve times a
        // minute, so that costs nothing.
        new Rule("upload", UPLOAD_LIMIT, path -> under(path, "/api/user/my-training/photos")
            || under(path, "/api/admin/training-photos")
            || under(path, "/api/user/me/avatar")),
        // The Google sign-in handshake shares the credential bucket: both legs end in a session, so
        // they belong with the other ways of getting one rather than outside every ceiling.
        new Rule("auth", AUTH_LIMIT, path -> under(path, "/api/auth")
            || under(path, "/oauth2")
            || under(path, "/login/oauth2")),
        new Rule("mytraining", MY_TRAINING_LIMIT, path -> under(path, "/api/user/my-training")),
        new Rule("user", USER_LIMIT, path -> under(path, "/api/user")),
        new Rule("admin", ADMIN_LIMIT, path -> under(path, "/api/admin")),
        new Rule("files", FILES_LIMIT, path -> under(path, "/api/files")),
        // Unauthenticated: the public API (marketing unsubscribe), the OG crawler stubs and the sitemap.
        new Rule("public", PUBLIC_LIMIT, path -> under(path, "/api/public")
            || under(path, "/og")
            || path.equals("/sitemap.xml"))
    );

    /**
     * Catch-all for {@code /api}. Scoped to the API on purpose: {@code /actuator/health} is polled by
     * the container healthcheck from a single address every few seconds, and a 429 there would flap
     * the container to unhealthy — a rate limiter that restarts the service is worse than none.
     */
    private static final Rule DEFAULT_RULE =
        new Rule("default", DEFAULT_LIMIT, path -> under(path, "/api"));

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final boolean enabled;

    /**
     * @param enabled escape hatch for load testing, where every request arrives from one address and
     *                the limiter would measure itself instead of the application. Left on everywhere
     *                else — it is not a switch for turning throttling off "for a moment" in production.
     */
    @Autowired
    public RateLimitFilter(MessageSource messageSource,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.messageSource = messageSource;
        this.enabled = enabled;
    }

    /** Default wiring: throttling on, as in production. */
    public RateLimitFilter(MessageSource messageSource) {
        this(messageSource, true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // The path as Spring matches it — decoded, `;params` dropped — not the raw URI. (It also
        // collapses `//`, which Spring would not route at all: such a request only spends a limit.)
        // On the raw one, `/%61pi/auth/login` reached the login controller while counting in the
        // default bucket (120/min instead of 15): eight times the password guesses (audit,
        // 2026-10-26; shouldCountAPathInTheBucketSpringRoutesItTo). Cloudflare normalises such
        // URLs before the firewalled origin sees them, so this is the second line, not the first.
        Rule rule = enabled ? resolveRule(UrlPathHelper.defaultInstance.getPathWithinApplication(request)) : null;

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = getClientIp(request) + ":" + rule.bucket();
        AtomicInteger counter = requestCounts.get(cacheKey, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > rule.limit()) {
            Locale locale = resolveLocale(request);
            String message = messageSource.getMessage("rate.limit.exceeded", null, locale);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            // The counter expires a minute after its first request, so a full window is the honest upper
            // bound on the wait. Without this header a client — ours or a crawler — just keeps hammering.
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            OBJECT_MAPPER.writeValue(response.getWriter(),
                Map.of("code", "TOO_MANY_REQUESTS", "message", message, "timestamp", Instant.now().toString()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * The rule a path is subject to, or {@code null} when it lives outside the API and is left alone.
     */
    static @Nullable Rule resolveRule(String path) {
        for (Rule rule : RULES) {
            if (rule.matches().test(path)) {
                return rule;
            }
        }
        return DEFAULT_RULE.matches().test(path) ? DEFAULT_RULE : null;
    }

    /**
     * Name of the bucket a path counts into, or {@code null} when it is not throttled at all. Exposed
     * for the architecture gate, which asserts that every controller base path lands in some bucket,
     * and in the same one as its sub-paths.
     */
    public static @Nullable String bucketFor(String path) {
        Rule rule = resolveRule(path);
        return rule == null ? null : rule.bucket();
    }

    /**
     * Base-path match that includes the base itself. An endpoint mapped on the bare base carries no
     * trailing slash, so a plain {@code startsWith(base + "/")} misses it — in the sibling climbing
     * app that is exactly how the heaviest query in the app ran unthrottled for months, under a rule
     * that looked like it covered the feature. Requiring the separator on the sub-path side is what
     * still keeps {@code /api/users-export} from being read as {@code /api/user}.
     */
    private static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    private Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isEmpty()) {
            String lang = header.split("[,;_-]")[0].trim().toLowerCase();
            for (Locale supported : SUPPORTED_LOCALES) {
                if (supported.getLanguage().equals(lang)) {
                    return supported;
                }
            }
        }
        return Locale.of("pl");
    }

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare sets CF-Connecting-IP to the true client IP on every proxied request, and a
        // client cannot forge it as long as the origin only accepts traffic from Cloudflare. Trust
        // it first: unlike the first X-Forwarded-For token (which a client can set to rotate past
        // the per-IP limiter — confirmed bypassable in prod), this header is controlled by our edge.
        // Fall back to X-Forwarded-For, then the socket address, for non-Cloudflare envs (local/dev).
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
