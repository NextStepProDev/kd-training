package pl.kdtraining.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the null-safety declaration on every package, not just the ones somebody remembered.
 * <p>
 * {@code @NullMarked} is what turns an unannotated type into "this is never null". Without it a type
 * has <em>unspecified</em> nullness — so a {@code @Nullable} standing next to unmarked neighbours
 * reads like a full contract while carrying half of one, and nothing anywhere says so. JSpecify is
 * explicit that the marker does not reach downwards: <em>"This annotation has no effect on
 * subpackages."</em> One declaration at the root therefore covers the root and nothing else, which
 * is exactly the state this gate was written to end.
 * <p>
 * There is no compiler error to lean on here and no runtime symptom either, which is why the rule is
 * checked against the source tree instead. Adding a package is the moment the convention is silently
 * dropped, and a new package is added without thinking about nullness roughly every time.
 */
class NullMarkedPackagesTest {

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern EQUALS_OVERRIDE =
        Pattern.compile("boolean\\s+equals\\s*\\(([^)]*)\\)");

    /**
     * Every package holding production code declares the marker.
     * <p>
     * Packages with no sources of their own are skipped rather than filled: an empty intermediate
     * directory has nothing to mark, and marking it would suggest a coverage it cannot provide.
     */
    @Test
    void everyPackageWithSourcesIsNullMarked() {
        Path root = SourceFiles.mainJavaRoot();
        var packagesWithSources = new TreeSet<Path>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            if (!file.getFileName().toString().equals("package-info.java")) {
                packagesWithSources.add(file.getParent());
            }
        }

        assertTrue(packagesWithSources.size() > 10,
            "found only " + packagesWithSources.size() + " packages — the scan is looking in the wrong place");

        List<String> unmarked = new ArrayList<>();
        for (Path pkg : packagesWithSources) {
            Path packageInfo = pkg.resolve("package-info.java");
            if (!Files.isRegularFile(packageInfo)) {
                unmarked.add(root.relativize(pkg) + " (no package-info.java)");
                continue;
            }
            if (!SourceFiles.readWithoutComments(packageInfo).contains("@NullMarked")) {
                unmarked.add(root.relativize(pkg) + " (package-info.java without @NullMarked)");
            }
        }

        assertEquals(List.of(), unmarked,
            "these packages are outside null-marked scope, so every @Nullable in them means less than it looks like:\n  "
                + String.join("\n  ", unmarked));
    }

    /**
     * Every {@code equals} override accepts null.
     * <p>
     * {@code Object.equals} is contractually required to answer false for null, and collections and
     * Hibernate both call it that way. Inside null-marked scope a bare {@code equals(Object o)}
     * declares the opposite, so the one method in Java that must take null says it will not — and
     * because the body almost always handles null correctly anyway, nothing ever fails to reveal it.
     * <p>
     * This is the same defect as an override that drops a framework's nullable marks, which is what
     * made it worth a gate: it was missed on the pass that marked every package, by an audit
     * specifically looking for exactly this.
     */
    @Test
    void everyEqualsOverrideAcceptsNull() {
        List<String> narrowed = new ArrayList<>();
        int found = 0;

        for (Path file : SourceFiles.mainJavaFiles()) {
            String source = SourceFiles.readWithoutComments(file);
            Matcher matcher = EQUALS_OVERRIDE.matcher(source);
            while (matcher.find()) {
                found++;
                if (!matcher.group(1).contains("@Nullable")) {
                    narrowed.add(SourceFiles.mainJavaRoot().relativize(file).toString());
                }
            }
        }

        assertTrue(found > 0, "no equals override found at all — the scan is looking in the wrong place");
        assertEquals(List.of(), narrowed,
            "these declare equals as refusing null, which Object does not allow:\n  "
                + String.join("\n  ", narrowed));
    }

    /**
     * Each declaration names the package it actually sits in.
     * <p>
     * The compiler accepts a {@code package-info.java} whose declared package does not match its
     * directory — nothing is generated for the real package, and the marker lands nowhere. That
     * failure looks identical to success from the outside, so a typo would otherwise survive both a
     * green build and this gate's first assertion.
     */
    @Test
    void everyPackageInfoDeclaresTheDirectoryItSitsIn() {
        Path root = SourceFiles.mainJavaRoot();
        List<String> mismatches = new ArrayList<>();
        int checked = 0;

        for (Path file : SourceFiles.mainJavaFiles()) {
            if (!file.getFileName().toString().equals("package-info.java")) {
                continue;
            }
            checked++;
            String expected = root.relativize(file.getParent()).toString().replace('/', '.');
            Matcher declared = PACKAGE_DECLARATION.matcher(SourceFiles.readWithoutComments(file));
            if (!declared.find()) {
                mismatches.add(expected + " — package-info.java declares no package at all");
            } else if (!declared.group(1).equals(expected)) {
                mismatches.add(expected + " — declares " + declared.group(1));
            }
        }

        assertTrue(checked > 10, "found only " + checked + " package-info files — the scan is looking in the wrong place");
        assertEquals(List.of(), mismatches,
            "these declarations mark a package that does not exist, so the real one stays unmarked:\n  "
                + String.join("\n  ", mismatches));
    }
}
