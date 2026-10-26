package pl.kdtraining.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner's private notes must be unreachable from anywhere that is not the notebook itself.
 *
 * <p>The danger this guards against is not a missing role check. It is an obliging field. The shapes
 * that describe a session are shared: {@code CalendarRangeResponse} is ONE record served both to the
 * coach and to the client, and the public listings are cached at the edge. A note field added to any
 * of them compiles, reads like a convenience, and publishes the notebook to the exact people it is
 * written about. A service that cannot read a note cannot leak one, so the fence is structural and
 * privacy stops depending on anyone remembering.
 *
 * <h2>Why the matching is shaped like this</h2>
 * The sibling app shipped this gate checking for the fully-qualified name and for the type name
 * followed by a space. That missed {@code import ...domain.adminnote.*;}, it missed
 * {@code AdminPrivateNote.SOME_CONSTANT}, and it missed {@code List<AdminPrivateNote>} -- after the
 * name comes a dot or an angle bracket, not a space. Worse, a wildcard import of one's own domain
 * package turned out to be that repo's house style, so the bypass was the local convention rather
 * than an exotic move. It is the house style HERE too: 66 files use wildcard imports and nine
 * services wildcard-import their own domain package ({@code PersonalTrainingService},
 * {@code AttachmentService}, {@code TrainingUnreadService}, {@code TrainingStatsService},
 * {@code AdminEventService}, {@code AdminEventTypeService}, {@code PublicService} and two more).
 *
 * <p>So this gate measures "does this code reach a note", not "does this file contain that string":
 * word boundaries around the type names, plus the package name, which catches the fully-qualified
 * use and the star import alike.
 */
class AdminNoteIsolationArchTest {

    /** Packages allowed to touch the notebook: the domain it lives in, and the API that serves it. */
    private static final List<String> OWNERS = List.of(
        "pl/kdtraining/domain/adminnote/",
        "pl/kdtraining/api/admin/note/"
    );

    /**
     * Files outside those packages that may call the notebook's SERVICE, and why.
     * <p>
     * Keep this an explicit allowlist. An entry is a decision somebody made and can be argued with;
     * a missing entry is an oversight, and the two must not look alike.
     */
    private static final List<String> SERVICE_CALLERS = List.of(
        // Erasing one athlete's plan asks the notebook to empty itself rather than reaching in.
        "AdminTrainingPlanErasureService.java",
        // A GDPR access request (art. 15) must hand over everything held about the person.
        "AdminUserDataExportService.java"
    );

    /**
     * A note type named as code, not as prose. {@code \b} after the name is the whole point: it
     * matches the dot of a constant access and the angle bracket of a generic, which a trailing
     * space never would.
     */
    private static final Pattern NOTE_TYPE = Pattern.compile(
        "\\b(AdminPrivateNote|AdminPrivateNoteRepository|SessionMarker)\\b");

    /** Catches both {@code import ...adminnote.Foo;} and {@code import ...adminnote.*;}. */
    private static final Pattern NOTE_PACKAGE = Pattern.compile("pl\\.kdtraining\\.domain\\.adminnote");

    /**
     * The service is a narrower door than the entity, but it is still a door.
     * <p>
     * It was demonstrated open: adding a note copy to {@code duplicate}/{@code paste} — the change
     * somebody makes "for consistency" with attachments, which DO travel with a copy — went straight
     * past the rule above, because {@code AdminPrivateNoteService} is not the entity and not the
     * repository. Three behavioural tests caught it and this gate did not, so the gate now covers the
     * seam as well: a new caller has to be argued for in {@code SERVICE_CALLERS} rather than merely
     * compiling.
     */
    private static final Pattern NOTE_SERVICE = Pattern.compile("\\bAdminPrivateNoteService\\b");

    @Test
    void shouldKeepThePrivateNotebookUnreachableOutsideItsOwnPackages() {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        boolean sawTheNotebookItself = false;

        for (Path file : SourceFiles.mainJavaFiles()) {
            String relative = file.toString().replace('\\', '/');
            if (OWNERS.stream().anyMatch(relative::contains)) {
                sawTheNotebookItself = true;
                continue;
            }
            scanned++;
            String source = SourceFiles.readWithoutComments(file);
            if (reachesTheNotebook(source)) {
                violations.add(relative);
            } else if (NOTE_SERVICE.matcher(source).find()
                    && SERVICE_CALLERS.stream().noneMatch(relative::endsWith)) {
                violations.add(relative + " (calls AdminPrivateNoteService)");
            }
        }

        // A source-reading gate rots by quietly matching nothing and passing forever. If either of
        // these fails, the reader broke -- not the codebase.
        assertTrue(scanned > 100, "scanned almost nothing — the source walk is broken");
        assertTrue(sawTheNotebookItself, "never found the notebook's own packages — the owner paths are stale");

        assertTrue(violations.isEmpty(), () -> """
            These files reach the owner's private notes: %s

            A private note may only be read inside %s. The risk is not a missing role check — it is a \
            field added to a DTO that is already shared with the person the note is about \
            (CalendarRangeResponse goes to the coach AND the client). Serve notes from their own \
            endpoint instead; then no shared shape and no cache has anything to leak.

            A file listed as "(calls AdminPrivateNoteService)" reached the notebook through its \
            service rather than its entity. That is a real door — copying a note along with a \
            duplicated training would leak one person's observation into another person's calendar. \
            If the call is right, add the file to SERVICE_CALLERS with a sentence saying why.""".formatted(
            violations, OWNERS));
    }

    /**
     * The detector has to be shown failing, or it is indistinguishable from one that answers "no
     * violations" to every question. The codebase is clean by design, so the proof runs against
     * written-down copies of each bypass -- including the three the sibling app's version let through.
     */
    @Test
    void theDetectorRecognisesEveryShapeOfBypass() {
        // 1. plain fully-qualified import
        assertEquals(true, reachesTheNotebook("""
            import pl.kdtraining.domain.adminnote.AdminPrivateNote;
            class X { AdminPrivateNote n; }
            """));

        // 2. star import + constant access — the name is followed by a DOT, never a space
        assertEquals(true, reachesTheNotebook("""
            import pl.kdtraining.domain.adminnote.*;
            class X { int max = AdminPrivateNote.MAX_BODY_LENGTH; }
            """));

        // 3. star import + generic — the name is followed by an ANGLE BRACKET
        assertEquals(true, reachesTheNotebook("""
            import pl.kdtraining.domain.adminnote.*;
            class X { List<AdminPrivateNote> all; }
            """));

        // 4. repository injected, entity never named
        assertEquals(true, reachesTheNotebook("""
            import pl.kdtraining.domain.adminnote.AdminPrivateNoteRepository;
            class X { X(AdminPrivateNoteRepository notes) {} }
            """));

        // 5. fully-qualified use with no import at all
        assertEquals(true, reachesTheNotebook("""
            class X { pl.kdtraining.domain.adminnote.AdminPrivateNote n; }
            """));

        // 6. the service seam — the door the entity rule does not cover. This one shipped open:
        //    copying a note alongside a duplicated training compiled, ran, and leaked one person's
        //    observation into another's calendar without this gate saying a word.
        assertEquals(true, reachesTheNotebookService("""
            import pl.kdtraining.api.admin.note.AdminPrivateNoteService;
            class X { X(AdminPrivateNoteService notes) {} }
            """));

        // ...and green once each one is taken back out.
        assertEquals(false, reachesTheNotebook("""
            import pl.kdtraining.domain.training.PersonalTraining;
            class X { PersonalTraining t; }
            """));

        // Prose about the notebook is not code reaching it — comments are stripped first.
        assertEquals(false, reachesTheNotebook("""
            // AdminPrivateNote is served from its own endpoint on purpose.
            class X {}
            """));
    }

    private static boolean reachesTheNotebook(String source) {
        String code = SourceFiles.stripComments(source);
        return NOTE_TYPE.matcher(code).find() || NOTE_PACKAGE.matcher(code).find();
    }

    private static boolean reachesTheNotebookService(String source) {
        return NOTE_SERVICE.matcher(SourceFiles.stripComments(source)).find();
    }
}
