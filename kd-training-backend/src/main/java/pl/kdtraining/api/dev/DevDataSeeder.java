package pl.kdtraining.api.dev;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.kdtraining.config.AdminEmailConfig;
import pl.kdtraining.domain.user.User;
import pl.kdtraining.domain.user.UserRepository;
import pl.kdtraining.domain.user.UserRole;

import java.time.Instant;

/**
 * Fills the dev database with sample data (`dev` profile).
 * <p>
 * <b>Idempotent</b> — each section checks whether the data already exists, so restarting the application
 * deletes NOTHING and creates no duplicates. You get a fresh set by resetting the database (on dev this is done
 * by FlywayConfig clean+migrate).
 * <p>
 * <b>Scope:</b> administrators and five client accounts; Jan and Anna are flagged as coaching clients, so
 * the 1-on-1 calendar can be opened for them straight away (each still has to give the art. 9 consent
 * on first visit, exactly like a real client).
 * <p>
 * All accounts have the password <b>{@value #DEV_PASSWORD}</b>. Administrators are the e-mails from {@code ADMIN_EMAIL}.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEV_PASSWORD = "dev";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminEmailConfig adminEmailConfig;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmailConfig = adminEmailConfig;
    }

    @Override
    public void run(String... args) {
        seedAdmins();
        seedUsers();
        log.info("DEV-SEEDER: done. Password for all accounts: '{}'.", DEV_PASSWORD);
    }

    // --- Administrators (from ADMIN_EMAIL) ---

    private void seedAdmins() {
        for (String email : adminEmailConfig.getAdminEmails()) {
            if (userRepository.existsByEmail(email)) continue;
            User admin = new User(email, "Admin", "Dev", null);
            admin.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            admin.setRole(UserRole.ADMIN);
            admin.markEmailVerified();
            userRepository.save(admin);
            log.info("DEV-SEEDER: admin {} (password: {})", email, DEV_PASSWORD);
        }
    }

    // --- Regular users (simple @dev.pl e-mails) ---

    private void seedUsers() {
        // Piotr without a phone (tests forcing profile completion); a mix of marketing consents for testing the
        // MARKETING audience; Jan and Anna are coaching clients.
        createUser("jan@dev.pl", "Jan", "Kowalski", "500100100", true, true);
        createUser("anna@dev.pl", "Anna", "Nowak", "500100200", true, true);
        createUser("piotr@dev.pl", "Piotr", "Wiśniewski", null, false, false);
        createUser("kasia@dev.pl", "Katarzyna", "Lewandowska", "500100400", false, false);
        createUser("marek@dev.pl", "Marek", "Zieliński", "500100500", true, false);
    }

    private void createUser(String email, String firstName, String lastName,
                            @Nullable String phone, boolean marketingConsent, boolean athlete) {
        if (userRepository.existsByEmail(email)) return;
        User u = new User(email, firstName, lastName, phone);
        u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        u.markEmailVerified();
        if (marketingConsent) {
            u.setMarketingConsentAt(Instant.now());
        }
        u.setAthlete(athlete);
        userRepository.save(u);
        log.info("DEV-SEEDER: user {} (password: {})", email, DEV_PASSWORD);
    }
}
