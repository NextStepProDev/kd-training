package pl.kdtraining.infrastructure.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MessageService {

    private final MessageSource messageSource;

    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    public String get(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    public String getForLang(String key, String language, Object... args) {
        Locale locale = language != null ? Locale.of(language) : Locale.of("pl");
        return messageSource.getMessage(key, args, locale);
    }
}
