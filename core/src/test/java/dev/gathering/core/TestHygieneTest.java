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

    /**
     * A property inside an inner class runs only if every class around it is a jqwik group.
     * <p>jqwik finds inner classes by {@code @Group}; JUnit's {@code @Nested} is invisible to it.
     * Fourteen properties sat in {@code @Nested} classes and had never run - including the one
     * guarding clicks on the risen hand card, and a round-trip check that had gone wrong the
     * day the board started measuring cards from their middle. Everything else in those
     * classes ran, so the file looked covered.
     */
    @Test
    @DisplayName("no property sits in an inner class jqwik cannot see")
    void everyPropertyIsInAClassJqwikFinds() throws IOException {
        List<String> hidden = new ArrayList<>();
        for (Path file : testSources()) {
            for (String line : propertiesJqwikCannotSee(Files.readString(file))) {
                hidden.add(file.getFileName() + ": " + line);
            }
        }
        assertThat(hidden)
                .as("a @Property inside an inner class without @Group is skipped: add @net.jqwik.api.Group")
                .isEmpty();
    }

    @Test
    @DisplayName("the inner-class check sees a property jqwik would skip")
    void theInnerClassCheckSeesTheShape() {
        String hidden = "class A {\n    @Nested\n    class B {\n        @Property(tries = 5)\n        void p() {\n        }\n    }\n}\n";
        String seen = "class A {\n    @Nested\n    @net.jqwik.api.Group\n    class B {\n        @Property\n        void p() {\n        }\n    }\n}\n";
        String topLevel = "class A {\n    @Property\n    void p() {\n    }\n}\n";
        assertThat(propertiesJqwikCannotSee(hidden)).hasSize(1);
        assertThat(propertiesJqwikCannotSee(seen)).isEmpty();
        assertThat(propertiesJqwikCannotSee(topLevel)).isEmpty();
    }

    /**
     * Every {@code @Property} line with an enclosing inner class that is not a {@code @Group}.
     * <p>Tracks braces to know which classes are open, and the annotations directly above each
     * class declaration to know whether it is a group. The outermost class needs nothing.
     */
    private static List<String> propertiesJqwikCannotSee(String source) {
        List<String> found = new ArrayList<>();
        java.util.Deque<int[]> open = new java.util.ArrayDeque<>();
        int depth = 0;
        boolean group = false;
        for (String raw : source.split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("@")) {
                if (line.matches("@(?:net\\.jqwik\\.api\\.)?Group\\b.*")) {
                    group = true;
                }
                if (PROPERTY.matcher(line).find()
                        && open.stream().skip(0).anyMatch(each -> each[1] == 0 && each != open.peekLast())) {
                    found.add(line);
                }
            } else if (line.matches("(?:[a-z]+\\s+)*(?:class|record|interface)\\s+\\w+.*\\{.*")) {
                open.push(new int[] {depth, group ? 1 : 0});
                group = false;
            } else if (!line.isEmpty() && !line.startsWith("*") && !line.startsWith("/")) {
                group = false;
            }
            for (char each : raw.toCharArray()) {
                if (each == '{') {
                    depth++;
                } else if (each == '}') {
                    depth--;
                    while (!open.isEmpty() && depth <= open.peek()[0]) {
                        open.pop();
                    }
                }
            }
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
