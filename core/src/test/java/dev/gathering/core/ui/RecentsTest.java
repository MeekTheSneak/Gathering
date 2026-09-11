package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The short list of things somebody keeps asking for. */
class RecentsTest {

    @Nested
    @DisplayName("using something")
    class UsingSomething {

        @Test
        @DisplayName("puts it at the front")
        void mostRecentFirst() {
            List<String> had = Recents.remember(Recents.remember(List.of(), "Clue"), "Treasure");
            assertThat(had).containsExactly("Treasure", "Clue");
        }

        @Test
        @DisplayName("moves one already there rather than adding it twice")
        void reuseMovesRatherThanGrows() {
            // The commonest thing that happens to this list, and it must not grow it.
            List<String> had = List.of("Clue", "Treasure", "Food");
            assertThat(Recents.remember(had, "Treasure"))
                    .containsExactly("Treasure", "Clue", "Food");
        }

        @Test
        @DisplayName("treats two spellings of one name as one")
        void caseIsNotADifference() {
            // "treasure" and "Treasure" are one token, and two rows of them is the list
            // failing at its one job.
            assertThat(Recents.remember(List.of("Treasure"), "treasure")).hasSize(1);
        }

        @Test
        @DisplayName("keeps a bounded number, dropping the one used longest ago")
        void bounded() {
            List<String> had = List.of();
            for (int index = 0; index < Recents.MOST_KEPT + 20; index++) {
                had = Recents.remember(had, "token " + index);
            }
            assertThat(had).hasSize(Recents.MOST_KEPT);
            assertThat(had.get(0)).isEqualTo("token " + (Recents.MOST_KEPT + 19));
            assertThat(had).doesNotContain("token 0");
        }

        @Test
        @DisplayName("ignores a name that is nothing")
        void nothingIsNotAName() {
            assertThat(Recents.remember(List.of("Treasure"), "   ")).containsExactly("Treasure");
            assertThat(Recents.remember(List.of("Treasure"), null)).containsExactly("Treasure");
        }

        @Test
        @DisplayName("cuts a name nobody could have meant")
        void namesAreBounded() {
            // A counter name is whatever somebody typed. A settings file is not the place to
            // find out how long that can get.
            String silly = "x".repeat(500);
            assertThat(Recents.remember(List.of(), silly).get(0))
                    .hasSizeLessThanOrEqualTo(Recents.LONGEST_NAME);
        }
    }

    @Nested
    @DisplayName("forgetting one")
    class ForgettingOne {

        @Test
        @DisplayName("drops it and leaves the rest in order")
        void dropsIt() {
            assertThat(Recents.forget(List.of("Treasure", "Clue", "Food"), "Clue"))
                    .containsExactly("Treasure", "Food");
        }

        @Test
        @DisplayName("is a no-op for one that was never there")
        void unknownIsFine() {
            assertThat(Recents.forget(List.of("Treasure"), "Blood")).containsExactly("Treasure");
            assertThat(Recents.forget(null, "Blood")).isEmpty();
        }
    }

    @Nested
    @DisplayName("the settings line")
    class TheSettingsLine {

        @Test
        @DisplayName("round-trips an ordinary list")
        void roundTrips() {
            List<String> had = List.of("Treasure", "Clue", "Zombie Army");
            assertThat(Recents.unpack(Recents.pack(had))).isEqualTo(had);
        }

        @Test
        @DisplayName("round-trips a name with the separator in it")
        void separatorSurvives() {
            // A counter somebody named "Stun|Shield" is still a counter. A pack that quietly
            // lost half of it would be worse than one that is uglier to read.
            List<String> had = List.of("Stun|Shield", "back\\slash", "plain");
            assertThat(Recents.unpack(Recents.pack(had))).isEqualTo(had);
        }

        @Test
        @DisplayName("survives an empty or absent line")
        void emptyIsEmpty() {
            assertThat(Recents.unpack("")).isEmpty();
            assertThat(Recents.unpack(null)).isEmpty();
            assertThat(Recents.unpack("   ")).isEmpty();
            assertThat(Recents.pack(List.of())).isEmpty();
            assertThat(Recents.pack(null)).isEmpty();
        }

        @Test
        @DisplayName("is bounded on the way in, because the file can be hand-edited")
        void unpackIsBoundedToo() {
            List<String> huge = new ArrayList<>();
            for (int index = 0; index < 400; index++) {
                huge.add("token " + index);
            }
            // Packed by hand rather than through remember, which is what editing the file is.
            StringBuilder line = new StringBuilder();
            for (String name : huge) {
                if (line.length() > 0) {
                    line.append('|');
                }
                line.append(name);
            }
            assertThat(Recents.unpack(line.toString())).hasSize(Recents.MOST_KEPT);
        }

        @Test
        @DisplayName("drops duplicates a hand-edited line may contain")
        void duplicatesAreDropped() {
            assertThat(Recents.unpack("Treasure|treasure|Clue")).containsExactly("Treasure", "Clue");
        }
    }

    @Nested
    @DisplayName("which server it was")
    class WhichServer {

        @Test
        @DisplayName("gives two servers two keys")
        void twoServersTwoKeys() {
            // A name remembered on one server is not an offer worth making on another, which
            // is the whole reason these are kept apart.
            assertThat(Recents.scopeKey("play.example.com"))
                    .isNotEqualTo(Recents.scopeKey("other.example.com"));
        }

        @Test
        @DisplayName("gives the same server the same key every time")
        void stableAcrossRestarts() {
            assertThat(Recents.scopeKey("play.example.com:25565"))
                    .isEqualTo(Recents.scopeKey("play.example.com:25565"));
        }

        @Test
        @DisplayName("is safe to write into a settings key")
        void safeToWrite() {
            for (String server : List.of(
                    "play.example.com:25565", "My Save (1)", "[::1]:25565", "a/b\\c=d",
                    "", "   ", "тест")) {
                assertThat(Recents.scopeKey(server)).matches("[a-z0-9_]+");
            }
        }

        @Test
        @DisplayName("does not collide between two that differ only in punctuation")
        void punctuationStillTellsThemApart() {
            // The readable part is stripped to letters and digits, so these two look alike;
            // the stamp is what keeps them apart.
            assertThat(Recents.scopeKey("a.b.c")).isNotEqualTo(Recents.scopeKey("abc"));
        }
    }
}
