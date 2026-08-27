package dev.gathering.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tests, checked for the ways a test can pass by not running.
 *
 * <p>A suite that grows for months is a suite nobody re-reads, and the worst thing in one is a
 * test that reports success because it never executed. Three property suites in this repo had
 * been silently skipped for weeks - long enough that one of them no longer compiled against
 * the generator it names, and nothing said so.
 *
 * <p>Reads the sources off disk, which is unusual for a unit test and is the point: what is
 * being checked is a property of the source text, and there is nothing in the compiled classes
 * that would show it.
 */
class TestHygieneTest {

    /** jqwik's own label annotation. {@code @DisplayName} is JUnit Jupiter's, and is not it. */
    private static final Pattern PROPERTY_WITH_DISPLAY_NAME = Pattern.compile(
            "@(?:Property|DisplayName\\([^)]*\\))\\s*\\n\\s*@(?:Property|DisplayName\\([^)]*\\))");

    private static final Pattern NAMED_FOR_ALL = Pattern.compile("@ForAll\\(\"([^\"]+)\"\\)");

    /** Written to allow a fully qualified {@code @net.jqwik.api.Provide}, which some use. */
    private static final Pattern PROVIDES = Pattern.compile(
            "@(?:[\\w.]+\\.)?Provide\\s*\\n"
                    + "(?:\\s*(?:public|private|protected)?\\s*[\\w.<>,\\[\\]? ]+\\s+)(\\w+)\\s*\\(");

    @Test
    @DisplayName("no property is labeled with an annotation that stops it running")
    void noPropertyCarriesADisplayName() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : testSources()) {
            String text = Files.readString(file);
            Matcher found = PROPERTY_WITH_DISPLAY_NAME.matcher(text);
            while (found.find()) {
                String both = found.group();
                if (both.contains("@Property") && both.contains("@DisplayName")) {
                    offenders.add(file.getFileName() + ": " + both.replaceAll("\\s+", " "));
                }
            }
        }
        assertThat(offenders)
                .as("@DisplayName on a jqwik @Property makes the engine skip it: use @Label")
                .isEmpty();
    }

    @Test
    @DisplayName("every named generator a property asks for exists in its own file")
    void everyNamedGeneratorExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : testSources()) {
            String text = Files.readString(file);
            List<String> provided = new ArrayList<>();
            Matcher provides = PROVIDES.matcher(text);
            while (provides.find()) {
                provided.add(provides.group(1));
            }
            Matcher asked = NAMED_FOR_ALL.matcher(text);
            while (asked.find()) {
                if (!provided.contains(asked.group(1))) {
                    missing.add(file.getFileName() + " asks for \"" + asked.group(1) + "\"");
                }
            }
        }
        assertThat(missing).as("a property naming a generator that is not there fails at run time")
                .isEmpty();
    }

    /**
     * Every test source in this module.
     *
     * <p>Found relative to the working directory, which Gradle sets to the module's own
     * directory. A run that cannot find them fails rather than passing on an empty list -
     * a hygiene check that quietly checks nothing is the thing it exists to catch.
     */
    private static List<Path> testSources() throws IOException {
        Path root = Path.of("src", "test", "java");
        assertThat(Files.isDirectory(root))
                .as("no test sources under " + root.toAbsolutePath())
                .isTrue();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> found = walk.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(found).isNotEmpty();
            return found;
        }
    }
}
