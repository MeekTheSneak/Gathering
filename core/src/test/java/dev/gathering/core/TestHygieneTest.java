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
 * <p>A suite that grows for months is a suite nobody re-reads, and the worst thing in one is a
 * test that reports success because it never executed. Three property suites in this repo had
 * been silently skipped for weeks - long enough that one of them no longer compiled against
 * the generator it names, and nothing said so.
 * <p>Reads the sources off disk, which is unusual for a unit test and is the point: what is
 * being checked is a property of the source text, and there is nothing in the compiled classes
 * that would show it.
 */
class TestHygieneTest {

    /** A {@code @Property}, bare or with arguments, possibly fully qualified. */
    private static final Pattern PROPERTY = Pattern.compile("^@(?:net\\.jqwik\\.api\\.)?Property\\b");

    /** JUnit Jupiter's label, which is not jqwik's and makes the engine skip the property. */
    private static final Pattern DISPLAY_NAME = Pattern.compile("^@(?:[\\w.]+\\.)?DisplayName\\b");

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
            for (String block : propertiesWithADisplayName(Files.readString(file))) {
                offenders.add(file.getFileName() + ": " + block);
            }
        }
        assertThat(offenders)
                .as("@DisplayName on a jqwik @Property makes the engine skip it: use @Label")
                .isEmpty();
    }

    /**
     * The shapes the check has to see, checked against the check.
     * <p>It was a regex that allowed no arguments on {@code @Property}, and 93 of this module's
     * 146 properties are written {@code @Property(tries = 500)}. It also missed a third
     * annotation between the two and a {@code @DisplayName} with a bracket in its text - so in
     * the form the original defect would most likely come back, it did not fire.
     */
    @Test
    @DisplayName("the display-name check sees every way the two annotations are written together")
    void theDisplayNameCheckSeesEveryShape() {
        for (String shape : List.of(
                "@Property\n@DisplayName(\"x\")\nvoid a() {}",
                "@Property(tries = 500)\n@DisplayName(\"x\")\nvoid a() {}",
                "@DisplayName(\"x\")\n@Property(tries = 500)\nvoid a() {}",
                "@Property\n@Tag(\"slow\")\n@DisplayName(\"x\")\nvoid a() {}",
                "@DisplayName(\"x (y)\")\n@Property\nvoid a() {}",
                "    @net.jqwik.api.Property(tries = 50)\n    @org.junit.jupiter.api.DisplayName(\"x\")\n    void a() {}")) {
            assertThat(propertiesWithADisplayName(shape)).as(shape).hasSize(1);
        }
        assertThat(propertiesWithADisplayName(
                "@Property(tries = 5)\n@Label(\"x\")\nvoid a() {}\n@Test\n@DisplayName(\"y\")\nvoid b() {}"))
                .as("a labeled property and a displayed test are both fine")
                .isEmpty();
    }

    /**
     * Every run of annotations in this source that holds both a {@code @Property} and a
     * {@code @DisplayName}, as one line each.
     * <p>A run is consecutive lines that start with {@code @}, which is how every annotation in
     * this module is written: one per line, directly above what it annotates.
     */
    private static List<String> propertiesWithADisplayName(String source) {
        List<String> found = new ArrayList<>();
        List<String> run = new ArrayList<>();
        for (String raw : (source + "\n").split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("@")) {
                run.add(line);
                continue;
            }
            if (run.stream().anyMatch(each -> PROPERTY.matcher(each).find())
                    && run.stream().anyMatch(each -> DISPLAY_NAME.matcher(each).find())) {
                found.add(String.join(" ", run));
            }
            run.clear();
        }
        return found;
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
