package dev.gathering.core.decklist;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Example tests cover the inputs somebody thought of. These cover the ones nobody did.
 * <p>The interesting property of a parser at the front door of a mod is not that it
 * understands good input - it is that no input at all, however malformed, can make it
 * throw, return null, or emit an entry the rest of the pipeline cannot handle.
 */
class DecklistParserPropertyTest {

    @Property(tries = 2000)
    void neverThrowsAndNeverReturnsNullForAnyInput(@ForAll("arbitraryText") String text) {
        ParsedDecklist result = DecklistParser.parse(text);

        assertThat(result).isNotNull();
        assertThat(result.entries()).isNotNull();
        assertThat(result.problems()).isNotNull();
    }

    @Property(tries = 2000)
    void everyEmittedEntryIsWellFormed(@ForAll("arbitraryText") String text) {
        ParsedDecklist result = DecklistParser.parse(text);

        for (DecklistEntry entry : result.entries()) {
            assertThat(entry.quantity()).isBetween(1, DecklistParser.MAX_QUANTITY);
            assertThat(entry.name()).isNotBlank();
            assertThat(entry.name()).isEqualTo(entry.name().strip());
            assertThat(entry.name()).doesNotContain("  ");
            assertThat(entry.section()).isNotNull();
            assertThat(entry.lineNumber()).isPositive();
            if (entry.setCode() != null) {
                assertThat(entry.setCode()).isEqualTo(entry.setCode().toUpperCase(java.util.Locale.ROOT));
            }
        }
    }

    @Property(tries = 2000)
    void everyProblemPointsAtARealLine(@ForAll("arbitraryText") String text) {
        ParsedDecklist result = DecklistParser.parse(text);
        int lineCount = text.split("\r\n|\r|\n", -1).length;

        for (ParseProblem problem : result.problems()) {
            assertThat(problem.lineNumber()).isBetween(1, lineCount);
            assertThat(problem.reason()).isNotBlank();
        }
    }

    @Property(tries = 1000)
    void moxfieldLinesRoundTrip(
            @ForAll @IntRange(min = 1, max = 999) int quantity,
            @ForAll("cardName") String name,
            @ForAll("setCode") String setCode,
            @ForAll("collectorNumber") String collectorNumber,
            @ForAll boolean foil) {

        String line = quantity + " " + name + " (" + setCode + ") " + collectorNumber + (foil ? " *F*" : "");

        ParsedDecklist result = DecklistParser.parse(line);

        assertThat(result.problems()).isEmpty();
        assertThat(result.entries()).hasSize(1);
        DecklistEntry entry = result.entries().get(0);
        assertThat(entry.quantity()).isEqualTo(quantity);
        assertThat(entry.name()).isEqualTo(name);
        assertThat(entry.setCode()).isEqualTo(setCode.toUpperCase(java.util.Locale.ROOT));
        assertThat(entry.collectorNumber()).isEqualTo(collectorNumber);
        assertThat(entry.foil()).isEqualTo(foil);
    }

    @Property(tries = 500)
    void quantitiesAlwaysSumToTheTotal(
            @ForAll("cardName") String name, @ForAll @IntRange(min = 1, max = 20) int lines) {
        StringBuilder text = new StringBuilder();
        int expected = 0;
        for (int i = 1; i <= lines; i++) {
            text.append(i).append(' ').append(name).append(' ').append(i).append('\n');
            expected += i;
        }

        ParsedDecklist result = DecklistParser.parse(text.toString());

        assertThat(result.problems()).isEmpty();
        assertThat(result.totalCards()).isEqualTo(expected);
    }

    /** Anything at all, biased toward things that look almost like decklists. */
    @Provide
    Arbitrary<String> arbitraryText() {
        Arbitrary<String> junk = Arbitraries.strings().ofMaxLength(40);
        Arbitrary<String> lineish = Arbitraries.of(
                "1 Sol Ring", "4x Lightning Bolt", "0 Sol Ring", "999999 Forest", "", "   ",
                "//comment", "#tag", "Commander", "Deck", "Sideboard", "About", "Name Thing",
                "1 Fire // Ice (MH2) 290", "1x Forest (unf) 235 [Land]", "*F*", "()", "[]", "^^",
                "1 (ABC) 12", "1 [C21#263] Sol Ring", "1 Sol Ring (LTC) 284 *F*", "x", "1", "-",
                "1 Sol Ring (LTC) 284 [Artifact{top}] ^Have,#7b68ee^");
        return Arbitraries.oneOf(junk, lineish)
                .list().ofMaxSize(12)
                .map(lines -> String.join("\n", lines));
    }

    /** Names as the exporters write them: no markers, no brackets, no leading digit. */
    @Provide
    Arbitrary<String> cardName() {
        Arbitrary<Character> first = Arbitraries.chars().alpha();
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withChars(' ', '\'')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.contains("  ") && s.equals(s.strip()));
        return Combinators.combine(first, rest);
    }

    @Provide
    Arbitrary<String> setCode() {
        return Arbitraries.strings().withCharRange('a', 'z').withCharRange('A', 'Z')
                .withCharRange('0', '9').ofMinLength(3).ofMaxLength(4);
    }

    @Provide
    Arbitrary<String> collectorNumber() {
        return Arbitraries.integers().between(1, 999).map(String::valueOf);
    }

    /** Small local helper so the name generator reads as one thing rather than three. */
    private static final class Combinators {
        static Arbitrary<String> combine(Arbitrary<Character> first, Arbitrary<String> rest) {
            return net.jqwik.api.Combinators.combine(first, rest)
                    .as((c, s) -> (c + s).strip())
                    .filter(s -> !s.isBlank() && !s.contains("  ") && !s.endsWith(",") && !s.endsWith("-"));
        }
    }
}
