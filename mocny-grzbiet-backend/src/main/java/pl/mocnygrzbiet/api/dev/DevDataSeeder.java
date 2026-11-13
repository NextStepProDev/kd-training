package pl.mocnygrzbiet.api.dev;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.mocnygrzbiet.config.AdminEmailConfig;
import pl.mocnygrzbiet.domain.training.*;
import pl.mocnygrzbiet.domain.user.User;
import pl.mocnygrzbiet.domain.user.UserRepository;
import pl.mocnygrzbiet.domain.user.UserRole;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Fills the dev database with a realistic coaching situation (`dev` profile), so a manual test starts
 * from a working plan instead of an empty calendar.
 * <p>
 * <b>Addresses:</b> derived from the first {@code ADMIN_EMAIL} by plus-addressing — {@code you@gmail.com}
 * gives {@code you+ania@gmail.com} and so on — so with the {@code gmail} profile every mail the app sends
 * to any test account lands in one inbox, and no personal address is written into the repository.
 * Without {@code ADMIN_EMAIL} the accounts fall back to {@code name@dev.pl}.
 * <p>
 * <b>Written through repositories, never services:</b> a service write would queue "your plan changed"
 * mails, and every restart would then mail the test inbox.
 * <p>
 * <b>Idempotent</b>: accounts are skipped when they exist, the plan when the video library is not empty.
 * On dev the database is reset at every start anyway (FlywayConfig), so a restart gives a fresh set.
 * <p>
 * All accounts have the password <b>{@value #DEV_PASSWORD}</b>.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEV_PASSWORD = "dev";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminEmailConfig adminEmailConfig;
    private final PersonalTrainingRepository trainings;
    private final TrainingCommentRepository comments;
    private final AthleteGoalRepository goals;
    private final ExerciseVideoRepository videos;
    private final AthleteMaterialRepository materials;
    private final MaterialQuestionRepository questions;
    private final CoachDayOffRepository daysOff;
    private final TrainingTemplateRepository templates;
    private final JdbcTemplate jdbc;

    public DevDataSeeder(UserRepository users, PasswordEncoder passwordEncoder, AdminEmailConfig adminEmailConfig,
                         PersonalTrainingRepository trainings, TrainingCommentRepository comments,
                         AthleteGoalRepository goals, ExerciseVideoRepository videos,
                         AthleteMaterialRepository materials, MaterialQuestionRepository questions,
                         CoachDayOffRepository daysOff, TrainingTemplateRepository templates, JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmailConfig = adminEmailConfig;
        this.trainings = trainings;
        this.comments = comments;
        this.goals = goals;
        this.videos = videos;
        this.materials = materials;
        this.questions = questions;
        this.daysOff = daysOff;
        this.templates = templates;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        @Nullable User coach = seedAdmins();

        User ania = athlete("ania", "Ania", "Kowalska", "500100100", true);
        User kasia = athlete("kasia", "Kasia", "Nowak", "500100200", false);
        athlete("ola", "Ola", "Wiśniewska", "500100300", true);
        athlete("lena", "Lena", "Mazur", "500100400", true);
        athlete("beata", "Beata", "Zielińska", "500100500", true);
        // No phone: the profile-completion gate holds this account.
        account("piotr", "Piotr", "Wiśniewski", null);
        User marek = account("marek", "Marek", "Zieliński", "500100700");
        blockSinceDaysAgo(marek, 3);

        if (coach != null && videos.count() == 0) {
            seedPlan(coach, ania, kasia);
        }
        log.info("DEV-SEEDER: done. Password for all accounts: '{}'.", DEV_PASSWORD);
    }

    // --- Accounts ---

    private @Nullable User seedAdmins() {
        User first = null;
        for (String email : adminEmailConfig.getAdminEmails()) {
            User admin = users.findByEmail(email).orElse(null);
            if (admin == null) {
                admin = new User(email, "Kacper", "Testowy", "500100000");
                admin.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
                admin.setRole(UserRole.ADMIN);
                admin.markEmailVerified();
                acceptPolicies(admin);
                admin = users.save(admin);
                log.info("DEV-SEEDER: admin {} (password: {})", email, DEV_PASSWORD);
            }
            if (first == null) {
                first = admin;
            }
        }
        return first;
    }

    /** {@code you+name@domain} from the first admin address, or {@code name@dev.pl} without one. */
    private String address(String name) {
        return adminEmailConfig.getAdminEmails().stream().findFirst()
                .filter(e -> e.indexOf('@') > 0)
                .map(e -> e.substring(0, e.indexOf('@')) + "+" + name + e.substring(e.indexOf('@')))
                .orElse(name + "@dev.pl");
    }

    private User account(String name, String firstName, String lastName, @Nullable String phone) {
        String email = address(name);
        return users.findByEmail(email).orElseGet(() -> {
            User u = new User(email, firstName, lastName, phone);
            u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            u.markEmailVerified();
            acceptPolicies(u);
            log.info("DEV-SEEDER: user {} (password: {})", email, DEV_PASSWORD);
            return users.save(u);
        });
    }

    /** A coaching client; {@code consented} = the art. 9 consent screen is already behind them. */
    private User athlete(String name, String firstName, String lastName, String phone, boolean consented) {
        User u = account(name, firstName, lastName, phone);
        if (!u.isAthlete()) {
            u.setAthlete(true);
            if (consented) {
                u.grantTrainingConsent();
            }
            u = users.save(u);
        }
        return u;
    }

    private void blockSinceDaysAgo(User user, int days) {
        if (user.isBlocked()) {
            return;
        }
        user.block();
        users.save(user);
        // Backdated, so the panel shows a deletion date that is not simply "a year from now".
        jdbc.update("UPDATE users SET blocked_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(days))), user.getId());
    }

    // As registration does: without these the profile-completion gate would hold every seeded account.
    private static void acceptPolicies(User u) {
        u.setPrivacyAcceptedAt(Instant.now());
        u.acceptCurrentTerms(Instant.now());
    }

    // --- The plan ---

    private void seedPlan(User coach, User ania, User kasia) {
        LocalDate today = LocalDate.now();

        // Sample library entries: the ids are placeholders, so a thumbnail or the player may stay empty.
        ExerciseVideo deadBug = video("Dead bug — stabilizacja tułowia", "https://www.youtube.com/watch?v=g_BYB0R-4Ws");
        video("Koci grzbiet — mobilność kręgosłupa", "https://www.youtube.com/watch?v=kqnua4rHVVA");
        video("Oddychanie przeponowe w leżeniu", "https://www.youtube.com/watch?v=UB3tSaChwYs");

        // Ania: a weekly series on Tuesdays (three occurrences).
        UUID series = UUID.randomUUID();
        LocalDate tuesday = today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        for (int week = 0; week < 3; week++) {
            PersonalTraining t = training(ania, tuesday.plusWeeks(week), LocalTime.of(18, 0), LocalTime.of(19, 0),
                    "Siła — dół ciała", "Przysiad goblet 4×8, martwy ciąg rumuński 3×10, wykroki 3×8 na nogę.");
            t.joinSeries(series);
            trainings.save(t);
        }

        // Ania + Kasia: a group training, one row each, tied by a group id.
        UUID group = UUID.randomUUID();
        LocalDate thursday = today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY));
        for (User participant : new User[]{ania, kasia}) {
            PersonalTraining t = training(participant, thursday, LocalTime.of(17, 0), LocalTime.of(18, 0),
                    "Trening w parze — mobilność", "Rozgrzewka 10 min, praca na kręgosłup piersiowy, rozciąganie.");
            t.joinGroup(group);
            trainings.save(t);
        }

        // Ania: done five days ago, with an effort rating and a short exchange under it.
        PersonalTraining done = training(ania, today.minusDays(5), LocalTime.of(18, 0), LocalTime.of(19, 0),
                "Core i oddech", "Dead bug 3×10, plank boczny 3×30 s, oddychanie przeponowe 5 min.");
        done.complete(7, "Dobrze, tylko przy planku bolał lekko bark.");
        done = trainings.save(done);
        comments.save(new TrainingComment(done, coach, true, "Przy planku oprzyj się na przedramieniu, nie na dłoni."));
        comments.save(new TrainingComment(done, ania, false, "Dzięki, spróbuję w czwartek."));

        // Ania: missed two days ago (never ticked off).
        trainings.save(training(ania, today.minusDays(2), null, null,
                "Spacer 40 minut", "Spokojne tempo, bez zadyszki."));

        // Ania: a calorie task for tomorrow.
        PersonalTraining task = new PersonalTraining(ania, TrainingKind.TASK, today.plusDays(1), "Limit kalorii", true);
        task.edit(today.plusDays(1), null, null, "Limit kalorii", null, 2200, true);
        trainings.save(task);

        goals.save(new AthleteGoal(ania, GoalHorizon.SHORT, "Plank boczny 45 s na stronę bez bólu barku",
                today.plusWeeks(4)));
        AthleteMaterial material = materials.save(new AthleteMaterial(ania, "Ćwiczenie na co dzień",
                "Rób rano, 2 serie po 10 powtórzeń na stronę.", deadBug));
        questions.save(new MaterialQuestion(material, ania, false, "Czy mogę to robić też wieczorem?"));

        // Kasia: one entry of her own.
        trainings.save(new PersonalTraining(kasia, TrainingKind.TRAINING, today.plusDays(2), "Basen 30 minut", false));

        // Coach: a two-day break (seen in every calendar) on the Monday and Tuesday of the series' second
        // week. It covers that Tuesday's training on purpose: a planned session on a day off is a case the
        // calendar has to show. And a template.
        LocalDate secondTuesday = tuesday.plusWeeks(1);
        daysOff.save(new CoachDayOff(secondTuesday.minusDays(1), secondTuesday, "Szkolenie"));
        templates.save(new TrainingTemplate("Rozgrzewka standard",
                "Krążenia ramion, koci grzbiet, przysiad z pauzą.", 15));
        log.info("DEV-SEEDER: plan seeded (series, group training, completed, missed, task, goal, material)");
    }

    private ExerciseVideo video(String name, String url) {
        String key = YouTubeUrl.parse(url).orElseThrow().key();
        return videos.save(new ExerciseVideo(name, url, key, null));
    }

    private static PersonalTraining training(User athlete, LocalDate date, @Nullable LocalTime start,
                                             @Nullable LocalTime end, String title, @Nullable String description) {
        PersonalTraining t = new PersonalTraining(athlete, TrainingKind.TRAINING, date, title, true);
        t.edit(date, start, end, title, description, null, true);
        return t;
    }
}
