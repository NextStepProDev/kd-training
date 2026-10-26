package pl.kdtraining.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kdtraining.BaseIntegrationTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A new table holding a person's data must not silently fall out of the GDPR export.
 *
 * <p>Reads every foreign key that points at {@code users(id)} from the live schema. Each table has
 * to be either read by {@link AdminUserDataExportService} or named below with the reason it is not
 * the person's data. A miss here is a decision nobody made.
 */
class UserDataExportCoverageTest extends BaseIntegrationTest {

    /** Tables pointing at users that are ours, not theirs. */
    private static final Map<String, String> INTENTIONALLY_EXCLUDED = Map.of(
        "auth_tokens", "login, refresh and reset tokens — security plumbing, worthless to the person",
        "training_calendar_reads", "\"seen up to\" timestamp behind the unread dots");

    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldCoverEveryTableThatPointsAtAUser() {
        List<String> referencing = jdbc.queryForList("""
            SELECT DISTINCT tc.table_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'users'
            """, String.class);
        // A query that quietly matches nothing would pass forever.
        assertFalse(referencing.isEmpty(), "no foreign keys to users found — the query is broken");

        Set<String> missing = new TreeSet<>(referencing);
        missing.remove("users");
        missing.removeAll(AdminUserDataExportService.EXPORTED_TABLES);
        missing.removeAll(INTENTIONALLY_EXCLUDED.keySet());
        assertTrue(missing.isEmpty(),
            "Tables pointing at users that the GDPR export neither reads nor deliberately skips: " + missing
                + ". Add them to AdminUserDataExportService (and EXPORTED_TABLES) or to INTENTIONALLY_EXCLUDED.");

        Set<String> stale = new HashSet<>(INTENTIONALLY_EXCLUDED.keySet());
        stale.addAll(AdminUserDataExportService.EXPORTED_TABLES);
        stale.remove("users");
        stale.removeAll(referencing);
        assertTrue(stale.isEmpty(), "Listed tables that no longer point at users: " + stale);
    }
}
