package pl.kdtraining.infrastructure.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageSource messageSource;
    @InjectMocks private MessageService messageService;

    @Test
    void shouldGetMessageWithDefaultLocale() {
        LocaleContextHolder.setLocale(Locale.of("pl"));
        when(messageSource.getMessage("test.key", new Object[]{}, Locale.of("pl")))
            .thenReturn("Testowa wiadomość");

        String result = messageService.get("test.key");

        assertEquals("Testowa wiadomość", result);
    }

    @Test
    void shouldGetMessageWithArgs() {
        LocaleContextHolder.setLocale(Locale.of("pl"));
        when(messageSource.getMessage("test.key", new Object[]{5}, Locale.of("pl")))
            .thenReturn("Wartość: 5");

        String result = messageService.get("test.key", 5);

        assertEquals("Wartość: 5", result);
    }

    @Test
    void shouldGetMessageWithSpecificLocale() {
        when(messageSource.getMessage("test.key", new Object[]{}, Locale.of("pl")))
            .thenReturn("Wiadomość po polsku");

        String result = messageService.get("test.key", Locale.of("pl"));

        assertEquals("Wiadomość po polsku", result);
    }

    @Test
    void shouldGetMessageForLanguage() {
        when(messageSource.getMessage("test.key", new Object[]{}, Locale.of("pl")))
            .thenReturn("Po polsku");

        String result = messageService.getForLang("test.key", "pl");

        assertEquals("Po polsku", result);
    }

    @Test
    void shouldDefaultToPolishWhenLanguageIsNull() {
        when(messageSource.getMessage("test.key", new Object[]{}, Locale.of("pl")))
            .thenReturn("Domyślnie po polsku");

        String result = messageService.getForLang("test.key", null);

        assertEquals("Domyślnie po polsku", result);
    }
}
