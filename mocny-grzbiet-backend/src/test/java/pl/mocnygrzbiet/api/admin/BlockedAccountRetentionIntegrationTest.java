package pl.mocnygrzbiet.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.mocnygrzbiet.BaseIntegrationTest;
import pl.mocnygrzbiet.api.user.UserService;
import pl.mocnygrzbiet.domain.training.PersonalTraining;
import pl.mocnygrzbiet.domain.training.PersonalTrainingRepository;
import pl.mocnygrzbiet.domain.training.TrainingKind;
import pl.mocnygrzbiet.domain.user.User;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client's answer (08.11.2026): a blocked account waits a year for its owner, then goes with all
 * its data. GDPR leaves no basis to keep health data longer on the chance of a return.
 */
class BlockedAccountRetentionIntegrationTest extends BaseIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private PersonalTrainingRepository trainings;
    @Autowired private JdbcTemplate jdbc;

    private static final Instant NOW = Instant.now();

    private UUID client(String email, @org.jspecify.annotations.Nullable Instant blockedAt) {
        User user = new User(email, "Ala", "Testowa", null);
        user.markEmailVerified();
        user.setAthlete(true);
        UUID id = userRepository.save(user).getId();
        trainings.save(new PersonalTraining(userRepository.findById(id).orElseThrow(),
                TrainingKind.TRAINING, LocalDate.now(), "Siła", true));
        if (blockedAt != null) {
            jdbc.update("UPDATE users SET blocked_at = ? WHERE id = ?", Timestamp.from(blockedAt), id);
        }
        return id;
    }

    @Test
    void shouldDeleteOnlyAccountsBlockedForOverAYearWithTheirData() {
        UUID expired = client("old@mocnygrzbiet.test", NOW.minus(Duration.ofDays(366)));
        UUID recent = client("recent@mocnygrzbiet.test", NOW.minus(Duration.ofDays(364)));
        UUID active = client("active@mocnygrzbiet.test", null);

        List<UUID> deleted = userService.purgeLongBlockedAccounts(NOW);

        assertEquals(List.of(expired), deleted);
        assertTrue(userRepository.findById(expired).isEmpty());
        assertTrue(userRepository.findById(recent).isPresent(), "still within the year: they may come back");
        assertTrue(userRepository.findById(active).isPresent());
        Integer left = jdbc.queryForObject("SELECT count(*) FROM personal_trainings WHERE athlete_id = ?",
                Integer.class, expired);
        assertEquals(0, left, "the plan goes with the account");
    }

    @Test
    void shouldNeverDeleteAnAdministrator() {
        adminToken();
        UUID admin = adminUserId();
        jdbc.update("UPDATE users SET blocked_at = ? WHERE id = ?",
                Timestamp.from(NOW.minus(Duration.ofDays(400))), admin);

        assertEquals(List.of(), userService.purgeLongBlockedAccounts(NOW));
        assertTrue(userRepository.findById(admin).isPresent());
    }

    @Test
    void shouldTellTheCoachWhenABlockedAccountWillBeDeleted() throws Exception {
        String admin = adminToken();
        Instant blockedAt = Instant.parse("2026-11-08T10:00:00Z");
        UUID id = client("blocked@mocnygrzbiet.test", blockedAt);
        UUID active = client("active@mocnygrzbiet.test", null);

        mockMvc.perform(get("/api/admin/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDeletionAt").value("2027-11-08T10:00:00Z"));
        mockMvc.perform(get("/api/admin/users/" + active).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDeletionAt").doesNotExist());
    }
}
