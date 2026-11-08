package pl.mocnygrzbiet.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No log line carries a full e-mail address.
 *
 * <p>The server log rotates by size, not by date, so it outlives an account deletion: an address
 * written there is personal data the erasure cannot reach (RODO art. 17). A log names a person by
 * user id, or by {@code MailDispatcher.maskAddress} ("j***@gmail.com") where there is no id yet.
 *
 * <p>The dev seeder is exempt: it runs only under the {@code dev} profile and prints the throwaway
 * accounts it has just created, which is its whole point.
 */
class LogPersonalDataArchTest {

    private static final Pattern LOG_CALL =
        Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\((.*?)\\);", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern MASKED = Pattern.compile("maskAddress\\((?:[^()]|\\([^()]*\\))*\\)");
    private static final Pattern ADDRESS =
        Pattern.compile("getEmail\\(\\)|\\.email\\(\\)|\\bemail\\b|\\bto\\b|\\badminEmails\\b(?!.*::maskAddress)");

    @Test
    void shouldNotLogAFullEmailAddress() {
        List<String> offenders = new ArrayList<>();
        int calls = 0;
        for (Path file : SourceFiles.mainJavaFiles()) {
            if (file.endsWith("DevDataSeeder.java")) {
                continue;
            }
            Matcher call = LOG_CALL.matcher(SourceFiles.readWithoutComments(file));
            while (call.find()) {
                calls++;
                String args = MASKED.matcher(STRING_LITERAL.matcher(call.group(2)).replaceAll("\"\""))
                    .replaceAll("masked");
                if (ADDRESS.matcher(args).find()) {
                    offenders.add(file.getFileName() + ": " + call.group().replaceAll("\\s+", " "));
                }
            }
        }
        assertTrue(calls > 50, "the scan found only " + calls + " log calls — it is not reading the sources");
        assertTrue(offenders.isEmpty(),
            "Log by user id, or wrap the address in MailDispatcher.maskAddress:\n" + String.join("\n", offenders));
    }
}
