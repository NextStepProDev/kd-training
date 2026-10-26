package pl.kdtraining.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reads our own source and refuses one specific mistake: a scheduler holding transactional work that
 * only it can reach.
 * <p>
 * {@code @Transactional} is applied by a proxy wrapped around the bean. A call arriving from outside
 * passes through it; a call from a sibling method inside the same object does not, and the
 * annotation then does nothing at all — no error, no warning, no visible change, because every
 * repository call opens a transaction of its own and the work still completes. Only the atomicity of
 * the batch quietly disappears.
 * <p>
 * That combination is why this gate reads source instead of behaviour. A normal test of a scheduler
 * injects the bean and calls it, which is entry through the proxy — the one path where the
 * annotation works. Production takes the other one. The bug shipped once exactly like that, past a
 * review and a green suite, and nothing about the running system would ever have said so.
 * <p>
 * The rule is narrow on purpose. Self-invocation of a transactional method is perfectly correct when
 * the caller is itself transactional, and this codebase does it in eight places that are all fine;
 * a gate flagging those would cry wolf on the first day and be muted by the end of the week.
 */
class SchedulerTransactionArchTest {

    /** An annotation run — one or more annotations — immediately followed by a method declaration. */
    private static final Pattern ANNOTATED_METHOD = Pattern.compile(
        "((?:@\\w+(?:\\([^)]*\\))?\\s+)+)(?:public|protected|private)\\s+[\\w<>\\[\\],.?\\s]+?\\s(\\w+)\\s*\\(");

    /**
     * The rule. A transactional method inside a scheduler is reachable only from that scheduler —
     * nothing injects a cron holder — so unless it is the scheduled method itself, the only caller it
     * can have is a sibling, and the annotation on it is dead.
     * <p>
     * The fix is always the same shape: move the work to its own bean and let the scheduler call it.
     * Then there is no path that skips the proxy, rather than one that must be remembered.
     */
    @Test
    void schedulersMustNotDeclareTransactionalWorkOnlyTheyCanReach() {
        List<String> violations = new ArrayList<>();
        int schedulerClasses = 0;
        boolean sawScheduledAndTransactionalTogether = false;

        for (Path file : SourceFiles.mainJavaFiles()) {
            String source = SourceFiles.readWithoutComments(file);
            if (!source.contains("@Scheduled")) {
                continue;
            }
            schedulerClasses++;

            Matcher matcher = ANNOTATED_METHOD.matcher(source);
            while (matcher.find()) {
                String annotations = matcher.group(1);
                if (!annotations.contains("@Transactional")) {
                    continue;
                }
                if (annotations.contains("@Scheduled")) {
                    // Correct: Spring's infrastructure calls this one, which means through the proxy
                    sawScheduledAndTransactionalTogether = true;
                    continue;
                }
                violations.add("%s#%s".formatted(file.getFileName(), matcher.group(2)));
            }
        }

        // Guards against the way a source-reading test rots: it stops matching, finds nothing, and
        // passes forever while checking nothing at all. If either of these fails, the reader broke —
        // not the codebase.
        assertTrue(schedulerClasses > 0, "found no @Scheduled classes at all — the source scan is broken");
        assertTrue(sawScheduledAndTransactionalTogether,
            "found no @Transactional @Scheduled method — the annotation matcher no longer matches real code");

        assertTrue(violations.isEmpty(), () -> """
            A scheduler declares a transactional method that only it can call: %s

            Called from a sibling method, @Transactional does nothing — the call never passes the \
            proxy that would open the transaction, and nothing reports it. Move the work into its own \
            bean and have the scheduler call that bean instead. @Transactional on the @Scheduled \
            method itself is fine; Spring calls that one from outside.""".formatted(violations));
    }

    /**
     * The detector has to be shown failing on something, or it is indistinguishable from a detector
     * that answers "no violations" to every question. The codebase is clean — deliberately, that is
     * the point of the gate — so the proof runs against a written-down copy of the shape that shipped.
     */
    @Test
    void theDetectorStillRecognisesTheMistakeItExistsToCatch() {
        String broken = """
            @Component
            class SomeScheduler {
                @Scheduled(cron = "0 45 3 * * *")
                public void sweep() {
                    deleteExpired();
                }

                @Transactional
                public int deleteExpired() {
                    return 0;
                }
            }
            """;
        String correct = """
            @Component
            class SomeScheduler {
                @Scheduled(cron = "0 45 3 * * *")
                @Transactional
                public void sweep() {
                }
            }
            """;

        assertEquals(List.of("deleteExpired"), transactionalNonScheduledMethods(broken));
        assertEquals(List.of(), transactionalNonScheduledMethods(correct));
    }

    // --- the reader ------------------------------------------------------------------------------

    private static List<String> transactionalNonScheduledMethods(String source) {
        List<String> found = new ArrayList<>();
        // Comments go first: prose about @Transactional is not code carrying it.
        Matcher matcher = ANNOTATED_METHOD.matcher(SourceFiles.stripComments(source));
        while (matcher.find()) {
            String annotations = matcher.group(1);
            if (annotations.contains("@Transactional") && !annotations.contains("@Scheduled")) {
                found.add(matcher.group(2));
            }
        }
        return found;
    }
}
