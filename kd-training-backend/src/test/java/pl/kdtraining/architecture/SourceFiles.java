package pl.kdtraining.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads project sources off disk so the architecture gates can assert over the whole tree.
 *
 * <p>These gates exist because a review is sampling, not proof: a reviewer checks the hypotheses
 * they happened to think of that day, so the same class of defect can survive several passes.
 * Anything expressible as "no occurrence of X anywhere" belongs here instead — then it is checked on
 * every push and stops being anybody's job to remember.
 */
final class SourceFiles {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private SourceFiles() {
    }

    /**
     * Root of the production sources. Depending on where the suite is started from, the backend
     * module may or may not be the working directory — fail loudly rather than scan nothing and call
     * it a pass, which is how a source-reading gate rots into one that checks nothing.
     */
    static Path mainJavaRoot() {
        Path root = Path.of("src/main/java");
        if (!Files.isDirectory(root)) {
            root = Path.of("kd-training-backend/src/main/java");
        }
        assertTrue(Files.isDirectory(root), "cannot locate the Java sources to scan");
        return root;
    }

    static List<Path> mainJavaFiles() {
        try (Stream<Path> files = Files.walk(mainJavaRoot())) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot walk " + mainJavaRoot().toAbsolutePath(), e);
        }
    }

    static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path.toAbsolutePath(), e);
        }
    }

    /** Source with block and line comments blanked out, so a gate never matches commentary. */
    static String readWithoutComments(Path path) {
        return stripComments(read(path));
    }

    static String stripComments(String source) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll("")).replaceAll("");
    }
}
