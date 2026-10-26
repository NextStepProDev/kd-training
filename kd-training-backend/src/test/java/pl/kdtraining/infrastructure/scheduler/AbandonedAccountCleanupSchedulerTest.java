package pl.kdtraining.infrastructure.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kdtraining.api.user.UserService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbandonedAccountCleanupSchedulerTest {

    @Mock private UserService userService;

    @Test
    void shouldPurgeUsingConfiguredRetentionThreshold() {
        AbandonedAccountCleanupScheduler scheduler = new AbandonedAccountCleanupScheduler(userService, 30);
        when(userService.purgeAbandonedUnconsentedAccounts(30)).thenReturn(3);

        scheduler.purgeAbandonedAccounts();

        // The threshold must come from configuration, not be hardcoded.
        verify(userService).purgeAbandonedUnconsentedAccounts(30);
    }

    @Test
    void shouldHandleNothingToPurge() {
        AbandonedAccountCleanupScheduler scheduler = new AbandonedAccountCleanupScheduler(userService, 7);
        when(userService.purgeAbandonedUnconsentedAccounts(7)).thenReturn(0);

        scheduler.purgeAbandonedAccounts();

        verify(userService).purgeAbandonedUnconsentedAccounts(7);
    }
}
