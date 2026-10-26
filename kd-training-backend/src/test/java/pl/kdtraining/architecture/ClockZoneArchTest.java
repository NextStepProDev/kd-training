package pl.kdtraining.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clock the money is counted in.
 *
 * <p>Monthly billing, payment windows, proration, refunds, "today" and every session date are derived
 * from the JVM's default zone. Not one of those call sites passes a {@link ZoneId} — there is no
 * {@code ZoneId.of(...)} anywhere in the production sources — so the whole of it rests on the default
 * being Europe/Warsaw. That makes the zone a load-bearing setting with no code to defend it, which is
 * exactly the shape of thing that disappears in a refactor and is noticed a month later, around
 * midnight on the first of the month.
 *
 * <p>Two ends, because there are two ways to lose it and they fail differently:
 * <ul>
 *   <li><b>tests</b> — before this was pinned in build.gradle the suite ran in the host's zone:
 *       Europe/Warsaw locally, UTC on CI. Green meant "agreed with whatever clock happened to be
 *       running", not "agreed with production";
 *   <li><b>production</b> — the Dockerfile's {@code -Duser.timezone} is the only thing setting it
 *       there. Drop that flag and the container silently falls back to UTC, and every bill computed
 *       within two hours of midnight lands in the wrong month.
 * </ul>
 *
 * <p>docker-compose.prod.yml also sets a TZ variable, but that is documented as redundant belt and
 * braces — it only covers OS-level timestamps and does nothing for a container started without
 * compose, which is precisely the case the Dockerfile default exists to cover.
 */
class ClockZoneArchTest {

    private static final ZoneId CLUB_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String DOCKERFILE_FLAG = "-Duser.timezone=Europe/Warsaw";

    @Test
    void shouldRunTheSuiteInTheClubsTimeZone() {
        assertEquals(CLUB_ZONE, ZoneId.systemDefault(),
            "Tests must run in the zone production runs in, or a passing billing test says nothing "
                + "about a real bill. Pinned by `systemProperty 'user.timezone'` in build.gradle.");
    }

    @Test
    void shouldKeepProductionPinnedToTheClubsTimeZone() {
        String dockerfile = read(moduleRoot().resolve("Dockerfile"));
        assertTrue(dockerfile.contains(DOCKERFILE_FLAG),
            "The Dockerfile no longer sets " + DOCKERFILE_FLAG + ". Nothing else pins the clock in "
                + "production: without it the container runs on UTC and every month boundary moves.");
    }

    /**
     * Backend module root. Which directory the suite starts from depends on how it was launched, so
     * probe both and fail loudly rather than read nothing and call it a pass — a gate that quietly
     * scans an empty tree looks identical to one that holds.
     */
    private static Path moduleRoot() {
        Path root = Path.of(".");
        if (!Files.isRegularFile(root.resolve("Dockerfile"))) {
            root = Path.of("kd-training-backend");
        }
        assertTrue(Files.isRegularFile(root.resolve("Dockerfile")),
            "cannot locate the backend Dockerfile to check");
        return root;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path.toAbsolutePath(), e);
        }
    }
}
