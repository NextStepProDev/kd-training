package pl.kdtraining.config;

import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public Flyway flyway(DataSource dataSource, Environment environment) {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        // Dev only: the database is rebuilt from scratch on every start (clean + migrate), so switching
        // between branches with diverging migrations never collides. Data is restored by DevDataSeeder.
        // Opt-out: app.dev.reset-on-start=false (then a plain migrate, data persists between restarts).
        // Prod/test: clean is DISABLED (cleanDisabled=true) — a safeguard against wiping data.
        boolean resetOnStart = devProfile
                && environment.getProperty("app.dev.reset-on-start", Boolean.class, true);

        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(!resetOnStart);

        Flyway flyway = config.load();

        if (resetOnStart) {
            log.warn("DEV reset-on-start: cleaning the database (clean) and migrating from scratch. The seeder will rebuild data.");
            flyway.clean();
        }

        log.info("Running Flyway migrations...");
        flyway.migrate();
        log.info("Flyway migrations completed.");
        return flyway;
    }
}
