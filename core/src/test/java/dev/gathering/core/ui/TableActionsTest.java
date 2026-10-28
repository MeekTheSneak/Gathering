package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.ui.TableActionSpec.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The one list every screen reads a verb's name out of. */
class TableActionsTest {

    @Nested
    @DisplayName("the catalogue")
    class TheCatalogue {

        @Test
        @DisplayName("gives every verb a label key under the menu's own prefix")
        void labelKeysAreTheMenusOwn() {
            // Not a second set of strings. Every one of these verbs is already on a menu, and
            // a palette that called something by a different name from the menu it came from
            // would be teaching two words for one thing.
            for (TableActionSpec spec : TableActions.all()) {
                assertThat(spec.labelKey()).isEqualTo("menu.gathering.table." + spec.id());
            }
        }

        @Test
        @DisplayName("has no two verbs sharing an id")
        void idsAreUnique() {
            List<String> ids = TableActions.all().stream().map(TableActionSpec::id).toList();
            assertThat(ids).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("puts every verb in a drawer, and no drawer is empty")
        void everyCategoryIsUsed() {
            for (Category category : Category.values()) {
                assertThat(TableActions.inCategory(category))
                        .as("nothing is in the %s drawer", category)
                        .isNotEmpty();
            }
            int counted = 0;
            for (Category category : Category.values()) {
                counted += TableActions.inCategory(category).size();
            }
            assertThat(counted).isEqualTo(TableActions.all().size());
        }

        @Test
        @DisplayName("finds a verb by id and refuses one that is not there")
        void lookup() {
            assertThat(TableActions.byId("draw")).isPresent();
            assertThat(TableActions.has("draw")).isTrue();
            assertThat(TableActions.byId("summon_a_horse")).isEmpty();
            assertThat(TableActions.has(null)).isFalse();
        }

        @Test
        @DisplayName("teaches the six the tutorial teaches")
        void theTutorialsSix() {
            // The guided first game points at these by name. A rename here that nobody noticed
            // would leave a tutorial step pointing at nothing.
            for (String id : List.of("draw", "play", "tap", "add_counter", "pass_turn", "counters")) {
                assertThat(TableActions.has(id)).as("%s", id).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("searching")
    class Searching {

        @Test
        @DisplayName("puts an exact match above a prefix above a word inside")
        void ordering() {
            int exact = TableActions.score("tap", "Tap");
            int prefix = TableActions.score("tap", "Tapped out");
            int inside = TableActions.score("tap", "Force tap");
            int loose = TableActions.score("tap", "Untap");
            assertThat(exact).isGreaterThan(prefix);
            assertThat(prefix).isGreaterThan(inside);
            assertThat(inside).isGreaterThan(loose);
            assertThat(loose).isGreaterThan(0);
        }

        @Test
        @DisplayName("ignores case and the space around a query")
        void forgiving() {
            assertThat(TableActions.score("  TAP ", "tap")).isEqualTo(TableActions.score("tap", "Tap"));
        }

        @Test
        @DisplayName("matches nothing for an empty query or an empty word")
        void nothingToMatch() {
            assertThat(TableActions.score("", "Tap")).isZero();
            assertThat(TableActions.score("tap", "  ")).isZero();
            assertThat(TableActions.score(null, "Tap")).isZero();
            assertThat(TableActions.score("tap", null)).isZero();
        }

        @Test
        @DisplayName("counts a match after an underscore or a slash as starting a word")
        void wordBoundaries() {
            // Ids and labels both use these, and "graveyard" has to find "to_graveyard" and
            // "power/toughness" has to be findable by "toughness".
            assertThat(TableActions.score("graveyard", "to_graveyard")).isEqualTo(600);
            assertThat(TableActions.score("toughness", "power/toughness")).isEqualTo(600);
        }

        @Test
        @DisplayName("ranks a label match above an alias match")
        void theLabelWins() {
            // Both are exact, and the label is the part a translator controls. A player typing
            // a word that is somebody's label should get that row, not one that happens to
            // carry the word as an English alias.
            int byLabel = TableActions.rank("bin", "Bin", List.of());
            int byAlias = TableActions.rank("bin", "To graveyard", List.of("bin"));
            assertThat(byLabel).isGreaterThan(byAlias);
            assertThat(byAlias).isGreaterThan(0);
        }

        @Test
        @DisplayName("finds a verb by an alias the label does not contain")
        void aliasesEarnTheirKeep() {
            TableActionSpec graveyard = TableActions.byId("to_graveyard").orElseThrow();
            assertThat(TableActions.rank("yard", "To graveyard", graveyard.aliases()))
                    .isGreaterThan(TableActions.rank("yard", "To graveyard", List.of()));
        }

        @Test
        @DisplayName("never returns a negative rank, however far an alias falls")
        void neverNegative() {
            // rank() docks aliases fifty points, and a weak alias match is worth less than
            // that. A negative would sort below "no match at all", which is not a thing.
            assertThat(TableActions.rank("a", "Zzz", List.of("qa"))).isGreaterThanOrEqualTo(0);
        }
    }
}
