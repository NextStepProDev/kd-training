package pl.kdtraining.infrastructure.security;

import org.springframework.stereotype.Component;
import pl.kdtraining.infrastructure.i18n.MessageService;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces a strong-password policy at every point a password is set (registration, reset, change).
 * Three checks: minimum length, no personal data (email/name) in the password, and not a known
 * breached password (Have I Been Pwned). No composition rules and no hand-curated blocklist — the
 * authoritative "known-bad" source is HIBP. Throws IllegalArgumentException (mapped to HTTP 400 with
 * a localized message) on a policy violation.
 */
@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 10;
    // Personal-data fragments shorter than this are ignored, so a short name (e.g. "Ola") does not
    // over-block unrelated passwords that merely contain those letters.
    private static final int MIN_PERSONAL_TOKEN = 4;

    private final MessageService msg;
    private final PwnedPasswordChecker pwnedPasswordChecker;

    public PasswordPolicyValidator(MessageService msg, PwnedPasswordChecker pwnedPasswordChecker) {
        this.msg = msg;
        this.pwnedPasswordChecker = pwnedPasswordChecker;
    }

    public void validate(String password, String email, String firstName, String lastName) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(msg.get("validation.password.size"));
        }
        if (containsPersonalData(password.toLowerCase(), email, firstName, lastName)) {
            throw new IllegalArgumentException(msg.get("validation.password.personal"));
        }
        if (pwnedPasswordChecker.isPwned(password)) {
            throw new IllegalArgumentException(msg.get("validation.password.pwned"));
        }
    }

    private boolean containsPersonalData(String lower, String email, String firstName, String lastName) {
        for (String token : personalTokens(email, firstName, lastName)) {
            if (token.length() >= MIN_PERSONAL_TOKEN && lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private List<String> personalTokens(String email, String firstName, String lastName) {
        List<String> tokens = new ArrayList<>();
        if (email != null && !email.isBlank()) {
            String local = email.toLowerCase().split("@", 2)[0];
            tokens.add(local);
            for (String part : local.split("[._\\-+]")) {
                tokens.add(part);
            }
        }
        if (firstName != null && !firstName.isBlank()) {
            tokens.add(firstName.toLowerCase());
        }
        if (lastName != null && !lastName.isBlank()) {
            tokens.add(lastName.toLowerCase());
        }
        return tokens;
    }
}
