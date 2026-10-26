package pl.kdtraining.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminEmailConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminEmailConfig.class);

    private final Set<String> adminEmails;
    private final Set<String> hiddenEmails;

    public AdminEmailConfig(AppConfig appConfig) {
        this.adminEmails = parse(appConfig.getAdmin().getEmail());
        this.hiddenEmails = parse(appConfig.getAdmin().getHiddenEmails());
    }

    private static Set<String> parse(String raw) {
        return (raw == null || raw.isBlank())
                ? Set.of()
                : Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void logConfiguration() {
        if (adminEmails.isEmpty()) {
            log.warn("No admin emails configured (app.admin.email). First admin must be set manually.");
        } else {
            log.info("Admin auto-promotion configured for: {}", adminEmails);
        }
    }

    public Set<String> getAdminEmails() {
        return adminEmails;
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.toLowerCase().trim());
    }

    // Technical/developer accounts — hidden from the admin users list despite holding admin privileges.
    public Set<String> getHiddenEmails() {
        return hiddenEmails;
    }

    public boolean isHiddenEmail(String email) {
        return email != null && hiddenEmails.contains(email.toLowerCase().trim());
    }
}
