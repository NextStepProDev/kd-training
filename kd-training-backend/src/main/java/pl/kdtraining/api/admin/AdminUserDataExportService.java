package pl.kdtraining.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kdtraining.api.NotFoundException;
import pl.kdtraining.api.admin.note.AdminPrivateNoteService;
import pl.kdtraining.api.trainingcalendar.TrainingPhotoService;
import pl.kdtraining.infrastructure.i18n.MessageService;
import pl.kdtraining.infrastructure.storage.FileStorageService;
import pl.kdtraining.infrastructure.storage.StoragePaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Everything the club holds about one person, packed for a GDPR access request (art. 15) and for
 * data portability (art. 20): a ZIP with {@code dane.json}, the photos and a short Polish readme.
 *
 * <p>Plain SQL with an explicit column list per section, on purpose. The list is the decision about
 * what leaves the building: a password hash, an OAuth id or the unsubscribe token must not ride along
 * just because they sit on the same row, and {@code SELECT *} would ship every column added later.
 * It is also one query per section instead of a lazy load per row.
 *
 * <p>The notebook's private notes come through {@link AdminPrivateNoteService#exportForAthlete} and
 * nowhere else — reading {@code admin_private_notes} here would step round the isolation fence.
 *
 * <p>Collecting and writing are split. The data is read inside one read-only transaction; the ZIP is
 * streamed afterwards, one photo at a time, so a person with a long photo history does not have to
 * fit in the container's 384 MB.
 */
@Service
public class AdminUserDataExportService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserDataExportService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AVATAR_FOLDER = "avatars";

    /**
     * Tables pointing at {@code users(id)} that this export reads. {@code UserDataExportCoverageTest}
     * fails when a new one appears in neither this set nor its own list of deliberate exclusions.
     */
    static final Set<String> EXPORTED_TABLES = Set.of(
        "users", "personal_trainings", "athlete_goals", "training_comments", "training_deletions",
        "admin_private_notes");

    private static final String PROFILE_SQL = """
        SELECT first_name AS imie, last_name AS nazwisko, email, phone AS telefon,
               role AS rola, oauth_provider AS logowanie_przez, email_verified AS email_potwierdzony,
               email_verified_at AS email_potwierdzony_kiedy, preferred_language AS jezyk,
               is_athlete AS podopieczny_1na1, created_at AS konto_utworzone, updated_at AS konto_zmienione
        FROM users WHERE id = ?
        """;

    private static final String CONSENTS_SQL = """
        SELECT privacy_accepted_at AS polityka_prywatnosci_zaakceptowana,
               marketing_consent_at AS zgoda_marketingowa,
               training_consent_at AS zgoda_na_dane_zdrowotne
        FROM users WHERE id = ?
        """;

    private static final String TRAININGS_SQL = """
        SELECT id, kind AS rodzaj, training_date AS data, start_time AS godzina_od, end_time AS godzina_do,
               title AS tytul, description AS opis, target_calories AS docelowe_kalorie,
               created_by_admin AS wpis_trenera, completed_at AS wykonano, feedback AS informacja_zwrotna,
               rpe, created_at AS utworzono, updated_at AS zmieniono
        FROM personal_trainings
        WHERE athlete_id = ?
        ORDER BY training_date, start_time
        """;

    private static final String ATTACHMENTS_SQL = """
        SELECT a.training_id, a.kind AS rodzaj, COALESCE(a.label, v.name) AS nazwa,
               COALESCE(a.url, v.url) AS link
        FROM training_attachments a
        JOIN personal_trainings t ON t.id = a.training_id
        LEFT JOIN exercise_videos v ON v.id = a.video_id
        WHERE t.athlete_id = ?
        ORDER BY a.training_id, a.position
        """;

    private static final String COMMENTS_SQL = """
        SELECT c.training_id, CASE WHEN c.author_is_admin THEN 'trener' ELSE 'podopieczny' END AS autor,
               c.body AS tresc, c.photo_filename AS zdjecie, c.created_at AS dodano
        FROM training_comments c
        JOIN personal_trainings t ON t.id = c.training_id
        WHERE t.athlete_id = ?
        ORDER BY c.created_at
        """;

    private static final String GOALS_SQL = """
        SELECT horizon AS horyzont, content AS tresc, target_date AS termin, achieved_at AS osiagnieto,
               created_at AS utworzono
        FROM athlete_goals
        WHERE athlete_id = ?
        ORDER BY created_at
        """;

    /** The banner snapshot of a deleted future training: what was planned, when, and who removed it. */
    private static final String DELETIONS_SQL = """
        SELECT training_date AS data, start_time AS godzina, title AS tytul,
               deleted_by_admin AS usuniety_przez_trenera, deleted_at AS usunieto
        FROM training_deletions
        WHERE athlete_id = ?
        ORDER BY deleted_at
        """;

    private final JdbcTemplate jdbc;
    private final AdminPrivateNoteService notes;
    private final FileStorageService storage;
    private final MessageService msg;

    public AdminUserDataExportService(JdbcTemplate jdbc, AdminPrivateNoteService notes,
                                      FileStorageService storage, MessageService msg) {
        this.jdbc = jdbc;
        this.notes = notes;
        this.storage = storage;
        this.msg = msg;
    }

    /** What gets written into the ZIP; the photos are only named here and read while streaming. */
    public record ExportPackage(String lastName, byte[] json, List<Photo> photos) {}

    /** One file to copy from storage into the ZIP under {@code zipPath}. */
    public record Photo(String folder, String filename, String zipPath) {}

    @Transactional(readOnly = true)
    public ExportPackage collect(UUID userId, UUID adminId) {
        List<Map<String, @Nullable Object>> profile = rows(PROFILE_SQL, userId);
        if (profile.isEmpty()) {
            throw new NotFoundException(msg.get("error.user.not.found"));
        }
        String lastName = String.valueOf(profile.getFirst().get("nazwisko"));

        List<Map<String, @Nullable Object>> trainings = rows(TRAININGS_SQL, userId);
        List<Map<String, @Nullable Object>> attachments = rows(ATTACHMENTS_SQL, userId);
        List<Map<String, @Nullable Object>> comments = rows(COMMENTS_SQL, userId);
        // Keyed by ZIP path: a ZIP refuses a duplicate entry, and the refusal would come mid-stream,
        // after the headers, leaving a half-written download. Two rows naming one file need it once.
        Map<String, Photo> photos = new LinkedHashMap<>();
        for (var comment : comments) {
            Object photo = comment.get("zdjecie");
            if (photo != null) {
                String path = "zdjecia/treningi/" + photo;
                photos.putIfAbsent(path, new Photo(TrainingPhotoService.FOLDER, photo.toString(), path));
                comment.put("zdjecie", path);
            }
        }
        for (var training : trainings) {
            Object id = training.remove("id");
            training.put("zalaczniki", childrenOf(attachments, "training_id", id));
            training.put("komentarze", childrenOf(comments, "training_id", id));
        }

        @Nullable String avatar = jdbc.queryForObject(
            "SELECT avatar_filename FROM users WHERE id = ?", String.class, userId);
        if (avatar != null) {
            photos.putIfAbsent("zdjecia/" + avatar, new Photo(AVATAR_FOLDER, avatar, "zdjecia/" + avatar));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wygenerowano", LocalDate.now().toString());
        data.put("profil", profile.getFirst());
        data.put("zgody", rows(CONSENTS_SQL, userId).getFirst());
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("treningi_i_zadania", trainings);
        plan.put("cele", rows(GOALS_SQL, userId));
        plan.put("usuniete_treningi", rows(DELETIONS_SQL, userId));
        data.put("plan_1na1", plan);
        data.put("notatki_trenera", notes.exportForAthlete(userId).stream()
            .map(n -> {
                Map<String, @Nullable Object> row = new LinkedHashMap<>();
                row.put("dotyczy", switch (n.target()) {
                    case TRAINING -> "trening 1:1";
                });
                row.put("data", plain(n.date()));
                row.put("tytul_treningu", n.trainingTitle());
                row.put("tresc", n.body());
                row.put("utworzono", plain(n.createdAt()));
                row.put("zmieniono", plain(n.updatedAt()));
                return row;
            })
            .toList());

        byte[] json;
        try {
            json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize the data export", e);
        }

        log.info("Exported personal data of user {} by admin {}", userId, adminId);
        return new ExportPackage(lastName, json, List.copyOf(photos.values()));
    }

    /** Writes the package as a ZIP. Runs outside the transaction — it only touches storage. */
    public void writeZip(ExportPackage pkg, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "CZYTAJ_MNIE.txt", readme().getBytes(StandardCharsets.UTF_8));
            put(zip, "dane.json", pkg.json());
            for (Photo photo : pkg.photos()) {
                // Expired training photos lose their file to the sweep; the comment row stays. The name
                // check comes first because storage throws on a malformed one, and by now the headers
                // are out — a throw would leave a half-written ZIP instead of an error.
                if (!StoragePaths.FILENAME.matcher(photo.filename()).matches()
                        || !storage.exists(photo.folder(), photo.filename())) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(photo.zipPath()));
                try (InputStream in = storage.getInputStream(photo.folder(), photo.filename())) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static String readme() {
        return """
            Kopia danych osobowych — KD Training (kdtraining.example)

            Ta paczka zawiera wszystkie dane osobowe, które przechowujemy o Tobie w serwisie,
            przygotowane na Twoje żądanie (art. 15 i 20 RODO).

            dane.json  — dane konta, zgody, plan treningowy 1:1 (treningi, komentarze, cele,
                         usunięte treningi) oraz notatki trenera na Twój temat
            zdjecia/   — zdjęcie profilowe i zdjęcia dodane do komentarzy treningowych

            Data przygotowania: %s
            Administrator danych: [DANE FIRMY — DO UZUPEŁNIENIA PRZED STARTEM]
            Kontakt: kontakt@kdtraining.example
            """.formatted(LocalDate.now());
    }

    private List<Map<String, @Nullable Object>> rows(String sql, UUID userId) {
        List<Map<String, @Nullable Object>> out = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, userId)) {
            Map<String, @Nullable Object> clean = new LinkedHashMap<>();
            row.forEach((k, v) -> clean.put(k, plain(v)));
            out.add(clean);
        }
        return out;
    }

    /** Pulls the rows belonging to one parent and drops the join key, which means nothing to a reader. */
    private static List<Map<String, @Nullable Object>> childrenOf(List<Map<String, @Nullable Object>> rows,
                                                                  String key, @Nullable Object parentId) {
        List<Map<String, @Nullable Object>> out = new ArrayList<>();
        for (var row : rows) {
            if (parentId != null && parentId.equals(row.get(key))) {
                Map<String, @Nullable Object> copy = new LinkedHashMap<>(row);
                copy.remove(key);
                out.add(copy);
            }
        }
        return out;
    }

    /**
     * JDBC hands back {@code java.sql} types the plain mapper cannot write; turn them into readable
     * text. Timestamps go out in the JVM's zone (Europe/Warsaw), dates and times as ISO.
     */
    private static @Nullable Object plain(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case java.sql.Timestamp ts -> ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            case java.time.Instant i -> i.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            case java.sql.Date d -> d.toLocalDate().toString();
            case java.sql.Time t -> t.toLocalTime().toString();
            case TemporalAccessor ta -> ta.toString();
            case UUID u -> u.toString();
            default -> value;
        };
    }
}
