package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What the palette's search box gives back, and in what order. */
class ActionSearchTest {

    private static ActionSearch.Row row(String id, String label, String... aliases) {
        return new ActionSearch.Row(id, label, List.of(aliases));
    }

    private static final List<ActionSearch.Row> BOARD = List.of(
            row("tap", "Tap", "turn sideways", "use"),
            row("untap", "Untap", "straighten"),
            row("untap_all", "Untap all", "straighten all"),
            row("to_graveyard", "To graveyard", "bin", "yard"),
            row("to_exile", "To exile"),
            row("to_hand", "To hand", "bounce"),
            row("strength", "Set power/toughness", "p/t"),
            row("draw", "Draw"));

    private static List<String> idsFrom(List<ActionSearch.Row> rows) {
        return rows.stream().map(ActionSearch.Row::id).toList();
    }

    @Nested
    @DisplayName("an empty box")
    class AnEmptyBox {

        @Test
        @DisplayName("keeps every row in the order it was given")
        void keepsEverythingInOrder() {
            // The order it was given is the catalogue's, which is roughly how often a verb is
            // wanted. Sorting an unsearched list by anything else throws that away.
            assertThat(idsFrom(ActionSearch.matching("", BOARD))).isEqualTo(idsFrom(BOARD));
            assertThat(idsFrom(ActionSearch.matching(null, BOARD))).isEqualTo(idsFrom(BOARD));
            assertThat(idsFrom(ActionSearch.matching("   ", BOARD))).isEqualTo(idsFrom(BOARD));
        }
    }

    @Nested
    @DisplayName("a typed query")
    class ATypedQuery {

        @Test
        @DisplayName("puts the exact word first")
        void exactFirst() {
            // The one that matters. "tap" also appears inside "Untap" and "Untap all", and a
            // player who types the whole word and gets Untap has been given the opposite of
            // what they asked for.
            assertThat(idsFrom(ActionSearch.matching("tap", BOARD)).get(0)).isEqualTo("tap");
        }

        @Test
        @DisplayName("prefers a prefix to a word buried inside another")
        void prefixBeatsContains() {
            List<String> found = idsFrom(ActionSearch.matching("untap", BOARD));
            assertThat(found).containsExactly("untap", "untap_all");
        }

        @Test
        @DisplayName("finds a verb by an alias its label does not contain")
        void aliasesAreSearched() {
            // "bin" is nowhere in "To graveyard". Somebody who calls it that has to be able
            // to find it, which is the whole reason aliases exist.
            assertThat(idsFrom(ActionSearch.matching("bin", BOARD))).containsExactly("to_graveyard");
        }

        @Test
        @DisplayName("sorts a label match above an alias match")
        void labelBeatsAlias() {
            // "straighten" is Untap's alias and Untap all's alias. Neither has it in the
            // label, so both are alias matches - but "straighten" is exact for one and a
            // prefix for the other.
            assertThat(idsFrom(ActionSearch.matching("straighten", BOARD)))
                    .containsExactly("untap", "untap_all");
        }

        @Test
        @DisplayName("keeps catalogue order between rows that score the same")
        void tiesKeepTheirOrder() {
            // The three "To ..." rows score the same against "to", and coming out in menu
            // order rather than in whatever order the sort left them is what stops two
            // players typing the same letters seeing two different lists.
            // "Set power/toughness" is in this result too, and correctly: "to" starts a word
            // in it, after the slash. What is asserted is the order of the three, not that
            // they are alone - a search that found only what somebody had in mind would be a
            // search that had read their mind.
            assertThat(idsFrom(ActionSearch.matching("to", BOARD)))
                    .containsSubsequence("to_graveyard", "to_exile", "to_hand");
        }

        @Test
        @DisplayName("gives nothing rather than everything when nothing matches")
        void nothingMeansNothing() {
            // A list that ignores what was typed reads as the box being broken, and the
            // player presses the top row believing it is what they searched for.
            assertThat(ActionSearch.matching("zzzz", BOARD)).isEmpty();
        }

        @Test
        @DisplayName("ignores case and surrounding space")
        void caseAndSpace() {
            assertThat(idsFrom(ActionSearch.matching("  TAP  ", BOARD)).get(0)).isEqualTo("tap");
        }

        @Test
        @DisplayName("does not match a letter buried mid-word in every row")
        void notEveryRowForOneLetter() {
            // "a" is inside almost every label here. A search that returned all of them for
            // one letter would be a list nobody can use after their first keystroke, so a
            // match has to start a word to count for much - and the rows that only contain
            // it sort under the ones that start with it.
            List<String> found = idsFrom(ActionSearch.matching("d", BOARD));
            assertThat(found).isNotEmpty();
            assertThat(found.get(0)).isEqualTo("draw");
        }
    }

    @Nested
    @DisplayName("nothing to search")
    class NothingToSearch {

        @Test
        @DisplayName("answers empty rather than throwing")
        void emptyInEmptyOut() {
            assertThat(ActionSearch.matching("tap", List.of())).isEmpty();
            assertThat(ActionSearch.matching("tap", null)).isEmpty();
        }
    }
}
