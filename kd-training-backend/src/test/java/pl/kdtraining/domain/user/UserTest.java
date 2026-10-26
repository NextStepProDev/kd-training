package pl.kdtraining.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("test@test.com", "Jan", "Kowalski", "123456789");
    }

    @Test
    void shouldReturnFullName() {
        assertEquals("Jan Kowalski", user.getFullName());
    }

    @Test
    void shouldNotBeLocked() {
        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldIncrementFailedAttempts() {
        user.incrementFailedLoginAttempts();
        assertEquals(1, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldLockAccountAfterFiveFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertTrue(user.isAccountLocked());
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
    }

    @Test
    void shouldNotLockAfterFourFailedAttempts() {
        for (int i = 0; i < 4; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldResetFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        user.resetFailedLoginAttempts();

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldStartCountingFreshAfterLockExpires() throws Exception {
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }
        // We simulate the 15-minute lockout expiring (the counter is still at the threshold).
        Field lockedField = User.class.getDeclaredField("lockedUntil");
        lockedField.setAccessible(true);
        lockedField.set(user, Instant.now().minusSeconds(1));

        user.incrementFailedLoginAttempts();

        // After expiry the first failure counts as 1 and does NOT lock immediately.
        assertEquals(1, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldNotBeLockedWhenLockExpired() throws Exception {
        Field lockedField = User.class.getDeclaredField("lockedUntil");
        lockedField.setAccessible(true);
        lockedField.set(user, Instant.now().minusSeconds(1));

        assertFalse(user.isAccountLocked());
    }

    @Test
    void shouldReturnRemainingLockoutMinutes() throws Exception {
        Field lockedField = User.class.getDeclaredField("lockedUntil");
        lockedField.setAccessible(true);
        lockedField.set(user, Instant.now().plusSeconds(600));

        long minutes = user.getRemainingLockoutMinutes();
        assertTrue(minutes > 0 && minutes <= 10);
    }

    @Test
    void shouldReturnZeroMinutesWhenNotLocked() {
        assertEquals(0, user.getRemainingLockoutMinutes());
    }

    @Test
    void shouldReturnZeroMinutesWhenLockExpired() throws Exception {
        Field lockedField = User.class.getDeclaredField("lockedUntil");
        lockedField.setAccessible(true);
        lockedField.set(user, Instant.now().minusSeconds(100));

        assertEquals(0, user.getRemainingLockoutMinutes());
    }

    @Test
    void shouldMarkEmailVerified() {
        assertFalse(user.isEmailVerified());

        user.markEmailVerified();

        assertTrue(user.isEmailVerified());
        assertNotNull(user.getEmailVerifiedAt());
    }

    @Test
    void shouldDetectPasswordPresence() {
        assertFalse(user.hasPassword());

        user.setPasswordHash("hash");
        assertTrue(user.hasPassword());
    }

    @Test
    void shouldDetectAdmin() {
        assertFalse(user.isAdmin());

        user.setRole(UserRole.ADMIN);
        assertTrue(user.isAdmin());
    }

    @Test
    void shouldDefaultToPolish() {
        assertEquals("pl", user.getPreferredLanguage());
    }
}
