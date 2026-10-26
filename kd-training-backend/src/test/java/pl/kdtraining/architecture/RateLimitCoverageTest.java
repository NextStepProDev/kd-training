package pl.kdtraining.architecture;

import org.junit.jupiter.api.Test;
import pl.kdtraining.config.RateLimitFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every controller base path must land in a rate-limit bucket, and land in the same one whether it
 * is called on the bare base or on a sub-path.
 *
 * <p>Both halves come from the same failure mode. A rule written as {@code startsWith("/api/x/")}
 * covers a feature to the eye, but misses any endpoint mapped on the bare base — it carries no
 * trailing slash. In the sibling climbing app that is exactly how the heaviest query in the
 * application ran unthrottled for months: nothing about the rule looked wrong, you had to hold the
 * URI shape and the prefix in your head at the same time to see it. KD Training has no such
 * endpoint today, which is precisely why the trap is worth closing now rather than after one appears.
 *
 * <p>The filter denies by default, so a forgotten controller falls into the generic bucket instead
 * of through the filter untouched. This gate is what turns "it gets some limit" into "somebody chose
 * its limit": a new base path nobody thought about shows up here as the generic bucket at the moment
 * it is added, rather than in an audit two years later.
 */
class RateLimitCoverageTest {

    private static final Path API_ROOT = SourceFiles.mainJavaRoot().resolve("pl/kdtraining/api");

    /**
     * Class-level base path, e.g. {@code @RequestMapping("/api/admin/events")}. Deliberately not
     * limited to {@code /api}: the OG crawler stubs live at {@code /og} and are DB-backed reads like
     * any other, so they have to be covered too.
     */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
        "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"(/[^\"]*)\"");

    /**
     * Bases whose traffic is deliberately generic rather than given a bucket of its own. Keep this an
     * explicit allowlist: an entry here is a decision someone made, an entry missing from the rule
     * table is an oversight, and the two must not look alike.
     */
    private static final List<String> INTENTIONALLY_GENERIC = List.of(
        "/api/dev" // dev profile only; never mapped in production
    );

    /**
     * A write mapping and its own path, e.g. {@code @PostMapping("/{id}/photo")}. The parameter list
     * is not matched here — a single regex spanning annotations, generics and a multi-line signature
     * is the kind that quietly stops matching and leaves this gate iterating an empty list. The scan
     * below instead reads forward from the annotation to the start of the method body.
     */
    private static final Pattern WRITE_MAPPING = Pattern.compile(
        "@(?:Post|Put|Patch)Mapping(?:\\s*\\(\\s*(?:value\\s*=\\s*)?(?:\"([^\"]*)\")?[^)]*\\))?");

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*\\}");

    @Test
    void shouldGiveEveryControllerBasePathItsOwnRateLimitBucket() {
        for (String base : controllerBasePaths()) {
            String bucket = RateLimitFilter.bucketFor(base);
            assertNotNull(bucket, base + " is not rate limited at all.");

            if (INTENTIONALLY_GENERIC.contains(base)) {
                continue;
            }
            assertNotEquals("default", bucket, """
                %s falls into the generic "default" bucket, so nobody picked a limit for it.

                Add a rule for it in RateLimitFilter.RULES with a ceiling that fits what the endpoint
                actually costs (a write, a cached read, a file stream, a multipart upload), or — if
                generic really is the right answer — say so by listing the base in
                INTENTIONALLY_GENERIC here.
                """.formatted(base));
        }
    }

    @Test
    void shouldCountTheBareBaseAndItsSubPathsIntoTheSameBucket() {
        for (String base : controllerBasePaths()) {
            String bareBucket = RateLimitFilter.bucketFor(base);
            String subPathBucket = RateLimitFilter.bucketFor(base + "/anything");

            assertEquals(subPathBucket, bareBucket, """
                %s is counted into "%s" on the bare path but "%s" on a sub-path.

                An endpoint mapped on the bare base carries no trailing slash, so a rule written as
                startsWith(base + "/") misses it entirely and the request passes with whatever
                ceiling a broader rule happens to give it — or none. Match with under(path, base),
                which accepts both.
                """.formatted(base, bareBucket, subPathBucket));
        }
    }

    /** Google sign-in is a sign-in attempt, and it does not live under /api. */
    @Test
    void shouldThrottleTheOauth2LoginPathsAsAuthentication() {
        assertEquals("auth", RateLimitFilter.bucketFor("/oauth2/authorization/google"));
        assertEquals("auth", RateLimitFilter.bucketFor("/login/oauth2/code/google"));
    }

    /** Mapped with @GetMapping rather than a class-level base, so the scan above cannot see it. */
    @Test
    void shouldThrottleTheSitemapWithTheOtherAnonymousReads() {
        assertEquals("public", RateLimitFilter.bucketFor("/sitemap.xml"));
    }

    /**
     * The container healthcheck polls this every few seconds from one address. A 429 there would flap
     * the container to unhealthy, which is why the catch-all stops at /api instead of covering
     * everything.
     */
    @Test
    void shouldLeaveTheHealthEndpointUnthrottled() {
        assertNull(RateLimitFilter.bucketFor("/actuator/health"));
    }

    /**
     * Every endpoint that accepts a file must be rationed by the upload bucket, whatever prefix it
     * happens to sit under.
     *
     * <p>The other buckets ration cheap requests; this one rations bytes. A multipart body is read
     * into memory — up to 10 MB of it — before any handler can refuse it, on a container capped at
     * 384 MB. Missing an endpoint costs nothing visible: it simply gets measured by whatever roomier
     * ceiling its prefix happens to carry, so the ration protecting memory covers some doors into the
     * same expensive operation and not others.
     *
     * <p>That is not hypothetical. In the project this one was forked from, the avatar upload sat in
     * the ordinary user bucket for months, and three gallery uploads sat in the admin bucket, because
     * their id fell in the MIDDLE of the path and the rule was written as a prefix. Both were found by
     * reading the filter, which is the reading this test replaces.
     */
    @Test
    void shouldRationEveryFileUploadWithTheUploadBucket() {
        List<String> uploads = multipartEndpointPaths();
        for (String path : uploads) {
            assertEquals("upload", RateLimitFilter.bucketFor(path), """
                %s accepts a file but is counted into the "%s" bucket.

                Multipart bodies are parsed into memory before a handler can reject them, so they are
                rationed by bytes rather than by request count. Add the path to the "upload" rule in
                RateLimitFilter.RULES — and note that a prefix will not reach an endpoint whose id
                sits mid-path; match on the closing segment instead.
                """.formatted(path, RateLimitFilter.bucketFor(path)));
        }
    }

    /**
     * Second half of the guard below, for the upload scan specifically: the handler regex is the
     * fussiest thing in this file, and one that quietly stops matching would leave the assertion
     * above iterating an empty list — passing exactly like a gate that holds.
     */
    @Test
    void shouldFindTheFileUploadsItClaimsToCheck() {
        List<String> uploads = multipartEndpointPaths();
        assertTrue(uploads.size() >= 3,
            "Expected at least 3 multipart endpoints, found " + uploads.size() + " " + uploads
                + ". The handler regex is probably stale.");
        assertTrue(uploads.contains("/api/user/me/avatar"),
            "The avatar upload should be among the endpoints found: " + uploads);
    }

    /**
     * Guards the gate itself: if the regex or the source path goes stale, every assertion above
     * passes over an empty list and this file becomes indistinguishable from one that checks nothing.
     */
    @Test
    void shouldFindTheControllersItClaimsToCheck() {
        List<String> bases = controllerBasePaths();
        assertTrue(bases.size() >= 10,
            "Expected at least 10 controller base paths, found " + bases.size() + " " + bases
                + ". The mapping regex or the api/ source path is probably stale.");
    }

    private static List<String> controllerBasePaths() {
        List<String> bases = new ArrayList<>();
        for (Path file : controllerSources()) {
            Matcher matcher = CLASS_MAPPING.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                String base = matcher.group(1);
                if (!bases.contains(base)) {
                    bases.add(base);
                }
            }
        }
        return bases;
    }

    /**
     * Concrete request paths of every handler taking a {@code MultipartFile}, built from the class
     * mapping plus the method mapping with path variables filled in, so they can be put through the
     * real filter rather than a paraphrase of it.
     */
    private static List<String> multipartEndpointPaths() {
        List<String> paths = new ArrayList<>();
        for (Path file : controllerSources()) {
            String source = SourceFiles.readWithoutComments(file);
            Matcher classMapping = CLASS_MAPPING.matcher(source);
            if (!classMapping.find()) {
                continue;
            }
            String base = classMapping.group(1);
            Matcher mapping = WRITE_MAPPING.matcher(source);
            while (mapping.find()) {
                // From the end of the annotation to the opening brace of the method body: whatever
                // the signature looks like, its parameter list is inside that span.
                int bodyStart = source.indexOf('{', mapping.end());
                if (bodyStart < 0 || !source.substring(mapping.end(), bodyStart).contains("MultipartFile")) {
                    continue;
                }
                String suffix = mapping.group(1) == null ? "" : mapping.group(1);
                String path = PATH_VARIABLE.matcher(base + suffix)
                    .replaceAll("11111111-2222-3333-4444-555555555555");
                if (!paths.contains(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private static List<Path> controllerSources() {
        return SourceFiles.mainJavaFiles().stream()
            .filter(path -> path.startsWith(API_ROOT))
            .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
            .toList();
    }
}
