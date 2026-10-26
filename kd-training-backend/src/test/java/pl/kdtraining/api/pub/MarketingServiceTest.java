package pl.kdtraining.api.pub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.infrastructure.security.JwtAuthenticationFilter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;

    @InjectMocks private MarketingService service;

    @Test
    void shouldRevokeMarketingConsentForValidToken() {
        User user = new User("jan@test.com", "Jan", "Kowalski", null);
        user.setMarketingConsentAt(Instant.now());
        UUID token = user.getMarketingUnsubscribeToken();
        when(userRepository.findByMarketingUnsubscribeToken(token)).thenReturn(Optional.of(user));

        service.unsubscribe(token.toString());

        assertFalse(user.hasMarketingConsent());
        verify(userRepository).save(user);
    }

    @Test
    void shouldBeIdempotentWhenAlreadyUnsubscribed() {
        User user = new User("jan@test.com", "Jan", "Kowalski", null);
        UUID token = user.getMarketingUnsubscribeToken();
        when(userRepository.findByMarketingUnsubscribeToken(token)).thenReturn(Optional.of(user));

        service.unsubscribe(token.toString());

        // No consent → we save nothing, but also do not throw.
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreUnknownToken() {
        UUID token = UUID.randomUUID();
        when(userRepository.findByMarketingUnsubscribeToken(token)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.unsubscribe(token.toString()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreMalformedTokenWithoutQueryingRepository() {
        assertDoesNotThrow(() -> service.unsubscribe("not-a-uuid"));
        verifyNoInteractions(userRepository);
    }
}
