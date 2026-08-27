package dev.gathering.core.decklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The parser is the front door of the mod, so its edge cases get written down rather than
 * discovered by a friend pasting a list on a Friday night.
 */
class DecklistParserTest {

    @Nested
    @DisplayName("quantities")
    class Quantities {

        @ParameterizedTest
        @CsvSource({
            "'1 Sol Ring', 1, Sol Ring",
            "'4 Lightning Bolt', 4, Lightning Bolt",
            "'4x Lightning Bolt', 4, Lightning Bolt",
            "'4X Lightning Bolt', 4, Lightning Bolt",
            "'4 x Lightning Bolt', 4, Lightning Bolt",
            "'x4 Lightning Bolt', 4, Lightning Bolt",
            "'Sol Ring', 1, Sol Ring",
            "'10 Forest', 10, Forest",
        })
        void parsesQuantityForms(String line, int expectedQuantity, String expectedName) {
            DecklistEntry entry = onlyEntry(line);
            assertThat(entry.quantity()).isEqualTo(expectedQuantity);
            assertThat(entry.name()).isEqualTo(expectedName);
        }

        @Test
        @DisplayName("a card whose name starts with a digit still parses after a quantity")
        void nameStartingWithDigit() {
            DecklistEntry entry = onlyEntry("1 1996 World Champion");
            assertThat(entry.quantity()).isEqualTo(1);
            assertThat(entry.name()).isEqualTo("1996 World Champion");
        }

        @Test
        @DisplayName("a card whose name starts with X is not eaten by the quantity multiplier")
        void nameStartingWithX() {
            DecklistEntry entry = onlyEntry("4 Xathrid Necromancer");
            assertThat(entry.quantity()).isEqualTo(4);
            assertThat(entry.name()).isEqualTo("Xathrid Necromancer");
        }

        @Test
        void zeroQuantityIsAProblem() {
            ParsedDecklist result = DecklistParser.parse("0 Sol Ring");
            assertThat(result.entries()).isEmpty();
            assertThat(result.problems()).singleElement()
                    .satisfies(p -> assertThat(p.reason()).contains("at least 1"));
        }

        @Test
        void absurdQuantityIsAProblemRatherThanAnAllocation() {
            ParsedDecklist result = DecklistParser.parse("999999 Sol Ring");
            assertThat(result.entries()).isEmpty();
            assertThat(result.problems()).singleElement()
                    .satisfies(p -> assertThat(p.reason()).contains("exceeds the maximum"));
        }
    }

    @Nested
    @DisplayName("printing hints")
    class PrintingHints {

        @Test
        void moxfieldSetAndCollectorNumber() {
            DecklistEntry entry = onlyEntry("1 Sol Ring (LTC) 284");
            assertThat(entry.name()).isEqualTo("Sol Ring");
            assertThat(entry.setCode()).isEqualTo("LTC");
            assertThat(entry.collectorNumber()).isEqualTo("284");
            assertThat(entry.hasPrintingHint()).isTrue();
        }

        @Test
        @DisplayName("lowercase set codes are normalized, as Archidekt writes them")
        void lowercaseSetCode() {
            DecklistEntry entry = onlyEntry("1x Sol Ring (c21) 263");
            assertThat(entry.setCode()).isEqualTo("C21");
            assertThat(entry.collectorNumber()).isEqualTo("263");
        }

        @Test
        void setWithoutCollectorNumber() {
            DecklistEntry entry = onlyEntry("1 Sol Ring (C21)");
            assertThat(entry.setCode()).isEqualTo("C21");
            assertThat(entry.collectorNumber()).isNull();
        }

        @Test
        void bracketedSetCode() {
            DecklistEntry entry = onlyEntry("1 Sol Ring [C21] 263");
            assertThat(entry.name()).isEqualTo("Sol Ring");
            assertThat(entry.setCode()).isEqualTo("C21");
            assertThat(entry.collectorNumber()).isEqualTo("263");
        }

        @Test
        @DisplayName("deckstats puts the printing in front of the name")
        void leadingBracketedPrinting() {
            DecklistEntry entry = onlyEntry("1 [C21#263] Sol Ring");
            assertThat(entry.name()).isEqualTo("Sol Ring");
            assertThat(entry.setCode()).isEqualTo("C21");
            assertThat(entry.collectorNumber()).isEqualTo("263");
        }

        @Test
        void collectorNumbersMayCarrySuffixesAndStars() {
            assertThat(onlyEntry("1 Arcane Signet (WOE) 245★").collectorNumber()).isEqualTo("245★");
            assertThat(onlyEntry("1 Brainstorm (MH2) 60a").collectorNumber()).isEqualTo("60a");
        }

        @Test
        @DisplayName("no hint at all is fine; resolution picks the printing later")
        void noHint() {
            DecklistEntry entry = onlyEntry("1 Sol Ring");
            assertThat(entry.hasPrintingHint()).isFalse();
            assertThat(entry.set()).isEmpty();
        }
    }

    @Nested
    @DisplayName("finishes")
    class Finishes {

        @ParameterizedTest
        @ValueSource(strings = {
            "1 Sol Ring (LTC) 284 *F*",
            "1 Sol Ring (LTC) 284 *f*",
            "1 Sol Ring (LTC) 284 *E*",
            "1 Sol Ring (LTC) 284 *Foil*",
        })
        void foilMarkersAreRecognized(String line) {
            DecklistEntry entry = onlyEntry(line);
            assertThat(entry.foil()).isTrue();
            assertThat(entry.name()).isEqualTo("Sol Ring");
            assertThat(entry.setCode()).isEqualTo("LTC");
            assertThat(entry.collectorNumber()).isEqualTo("284");
        }

        @Test
        void nonFoilByDefault() {
            assertThat(onlyEntry("1 Sol Ring").foil()).isFalse();
        }
    }

    @Nested
    @DisplayName("exporter noise")
    class ExporterNoise {

        @Test
        @DisplayName("Archidekt categories and tags are stripped, the printing survives")
        void archidektFullLine() {
            DecklistEntry entry = onlyEntry("1x Sol Ring (c21) 263 *F* [Artifact{top}] ^Have,#7b68ee^");
            assertThat(entry.quantity()).isEqualTo(1);
            assertThat(entry.name()).isEqualTo("Sol Ring");
            assertThat(entry.setCode()).isEqualTo("C21");
            assertThat(entry.collectorNumber()).isEqualTo("263");
            assertThat(entry.foil()).isTrue();
        }

        @Test
        @DisplayName("a category that looks like a set code is not mistaken for one")
        void titleCaseCategoryIsNotASet() {
            DecklistEntry entry = onlyEntry("1x Forest (unf) 235 [Land]");
            assertThat(entry.name()).isEqualTo("Forest");
            assertThat(entry.setCode()).isEqualTo("UNF");
            assertThat(entry.collectorNumber()).isEqualTo("235");
        }

        @Test
        void commentsAreIgnored() {
            ParsedDecklist result = DecklistParser.parse("""
                    // exported from somewhere
                    # a note
                    1 Sol Ring
                    """);
            assertThat(result.entries()).hasSize(1);
            assertThat(result.problems()).isEmpty();
        }

        @Test
        @DisplayName("a split card keeps its slashes; only a leading // is a comment")
        void splitCardNameSurvives() {
            DecklistEntry entry = onlyEntry("1 Fire // Ice (MH2) 290");
            assertThat(entry.name()).isEqualTo("Fire // Ice");
            assertThat(entry.setCode()).isEqualTo("MH2");
        }

        @Test
        void windowsLineEndingsAreHandled() {
            ParsedDecklist result = DecklistParser.parse("1 Sol Ring\r\n2 Forest\r\n");
            assertThat(result.entries()).hasSize(2);
            assertThat(result.totalCards()).isEqualTo(3);
        }

        @Test
        void runsOfWhitespaceInNamesCollapse() {
            assertThat(onlyEntry("1    Sol    Ring").name()).isEqualTo("Sol Ring");
        }
    }

    @Nested
    @DisplayName("sections")
    class Sections {

        @Test
        void moxfieldCommanderSection() {
            ParsedDecklist result = DecklistParser.parse("""
                    Commander
                    1 Halana and Alena, Partners (VOW) 239

                    Deck
                    1 Sol Ring (LTC) 284
                    1 Arcane Signet (ELD) 331
                    """);

            assertThat(result.problems()).isEmpty();
            assertThat(result.entriesIn(DeckSection.COMMANDER))
                    .singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("Halana and Alena, Partners"));
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(2);
        }

        @Test
        void sideboardHeaderWithColonAndCount() {
            ParsedDecklist result = DecklistParser.parse("""
                    Deck (60)
                    4 Lightning Bolt

                    Sideboard: (15)
                    2 Pyroblast
                    """);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(4);
            assertThat(result.cardCount(DeckSection.SIDEBOARD)).isEqualTo(2);
        }

        @Test
        @DisplayName("MTGO style: a bare blank line separates main from sideboard")
        void blankLineSideboardConvention() {
            ParsedDecklist result = DecklistParser.parse("""
                    4 Lightning Bolt
                    4 Chain Lightning

                    2 Pyroblast
                    """);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(8);
            assertThat(result.cardCount(DeckSection.SIDEBOARD)).isEqualTo(2);
        }

        @Test
        @DisplayName("a comment between the blocks does not spend the blank line")
        void commentsAreInvisibleToBlockStructure() {
            // The hand-edited shape: somebody labels their sideboard with a comment. The
            // comment used to count as a block of its own, so the convention saw three
            // blocks, never fired, and the sideboard quietly joined the mainboard.
            ParsedDecklist result = DecklistParser.parse("""
                    4 Lightning Bolt
                    4 Chain Lightning

                    # Sideboard

                    2 Pyroblast
                    """);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(8);
            assertThat(result.cardCount(DeckSection.SIDEBOARD)).isEqualTo(2);
        }

        @Test
        @DisplayName("three blocks means stray blank lines, not a sideboard")
        void blankLineConventionDoesNotFireOnThreeBlocks() {
            ParsedDecklist result = DecklistParser.parse("""
                    4 Lightning Bolt

                    4 Chain Lightning

                    2 Pyroblast
                    """);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(10);
            assertThat(result.entriesIn(DeckSection.SIDEBOARD)).isEmpty();
        }

        @Test
        @DisplayName("explicit headers suppress the blank-line convention entirely")
        void explicitHeadersWin() {
            ParsedDecklist result = DecklistParser.parse("""
                    Deck
                    4 Lightning Bolt

                    4 Chain Lightning
                    """);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(8);
            assertThat(result.entriesIn(DeckSection.SIDEBOARD)).isEmpty();
        }

        @Test
        void arenaAboutBlockCarriesTheDeckName() {
            ParsedDecklist result = DecklistParser.parse("""
                    About
                    Name Halana and Alena

                    Commander
                    1 Halana and Alena, Partners (VOW) 239

                    Deck
                    1 Sol Ring (LTC) 284
                    """);
            assertThat(result.deckName()).contains("Halana and Alena");
            assertThat(result.cardCount(DeckSection.COMMANDER)).isEqualTo(1);
            assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(1);
        }

        @Test
        void maybeboardIsKeptSeparate() {
            ParsedDecklist result = DecklistParser.parse("""
                    Deck
                    1 Sol Ring

                    Maybeboard
                    1 Mana Crypt
                    """);
            assertThat(result.entriesIn(DeckSection.MAYBEBOARD))
                    .singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("Mana Crypt"));
        }
    }

    @Nested
    @DisplayName("problem reporting")
    class Problems {

        @Test
        @DisplayName("one bad line does not cost the other ninety-nine")
        void badLineIsIsolated() {
            ParsedDecklist result = DecklistParser.parse("""
                    1 Sol Ring
                    0 Arcane Signet
                    1 Command Tower
                    """);
            assertThat(result.entries()).hasSize(2);
            assertThat(result.problems()).singleElement()
                    .satisfies(p -> {
                        assertThat(p.lineNumber()).isEqualTo(2);
                        assertThat(p.sourceLine()).isEqualTo("0 Arcane Signet");
                    });
        }

        @Test
        @DisplayName("pasting a deck link says what to do instead of hunting for a card called https")
        void deckLinksAreRecognizedAndExplained() {
            ParsedDecklist archidekt = DecklistParser.parse("https://archidekt.com/decks/1234567/my_deck");
            ParsedDecklist moxfield = DecklistParser.parse("https://www.moxfield.com/decks/AbCdEf123456");
            ParsedDecklist bare = DecklistParser.parse("archidekt.com/decks/1234567");

            assertThat(archidekt.entries()).isEmpty();
            assertThat(archidekt.problems()).singleElement().satisfies(problem -> {
                assertThat(problem.reason()).contains("deck link").contains("Archidekt").contains("Export");
                assertThat(problem.lineNumber()).isEqualTo(1);
            });
            assertThat(moxfield.problems()).singleElement()
                    .satisfies(problem -> assertThat(problem.reason()).contains("Moxfield"));
            assertThat(bare.problems()).hasSize(1);
        }

        @Test
        @DisplayName("a link among real lines costs only its own line")
        void aLinkDoesNotCostTheRestOfTheList() {
            ParsedDecklist result = DecklistParser.parse("""
                    https://archidekt.com/decks/1234567/my_deck
                    1 Sol Ring
                    1 Command Tower
                    """);

            assertThat(result.entries()).hasSize(2);
            assertThat(result.problems()).hasSize(1);
        }

        @Test
        @DisplayName("a card with slashes in its name is still a card, not a web address")
        void splitCardsAreNotMistakenForLinks() {
            assertThat(DecklistParser.parse("1 Fire // Ice (MH2) 290").problems()).isEmpty();
            assertThat(DecklistParser.parse("Fire // Ice").entries()).singleElement()
                    .satisfies(entry -> assertThat(entry.name()).isEqualTo("Fire // Ice"));
            assertThat(DecklistParser.parse("1 Delver of Secrets // Insectile Aberration").problems()).isEmpty();
            assertThat(DecklistParser.parse("1 Mr. Orfeo, the Boulder").problems()).isEmpty();
        }

        @Test
        void emptyInputIsEmptyNotBroken() {
            assertThat(DecklistParser.parse("")).isEqualTo(ParsedDecklist.EMPTY);
            assertThat(DecklistParser.parse("   \n\n  ")).isEqualTo(ParsedDecklist.EMPTY);
            assertThat(DecklistParser.parse(null)).isEqualTo(ParsedDecklist.EMPTY);
        }
    }

    @Test
    @DisplayName("the reference deck from the brief round-trips")
    void referenceCommanderDeck() {
        ParsedDecklist result = DecklistParser.parse("""
                Commander
                1 Halana and Alena, Partners (VOW) 239

                Deck
                1 Sol Ring (LTC) 284 *F*
                1 Arcane Signet (ELD) 331
                1 Command Tower (CMR) 350
                1 Tevesh Szat, Doom of Fools (CMR) 153
                30 Forest (UNF) 235
                """);

        assertThat(result.problems()).isEmpty();
        assertThat(result.cardCount(DeckSection.COMMANDER)).isEqualTo(1);
        assertThat(result.cardCount(DeckSection.MAINBOARD)).isEqualTo(34);
        assertThat(result.totalCards()).isEqualTo(35);
        assertThat(result.entriesIn(DeckSection.MAINBOARD))
                .filteredOn(DecklistEntry::foil)
                .singleElement()
                .satisfies(e -> assertThat(e.name()).isEqualTo("Sol Ring"));
    }

    private static DecklistEntry onlyEntry(String line) {
        ParsedDecklist result = DecklistParser.parse(line);
        assertThat(result.problems()).isEmpty();
        List<DecklistEntry> entries = result.entries();
        assertThat(entries).hasSize(1);
        return entries.get(0);
    }
}
