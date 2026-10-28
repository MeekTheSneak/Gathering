package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Finding one card in a collection of ten thousand. */
class CollectionSearchTest {

    private static final CollectionSearch.Row BOLT =
            row("Lightning Bolt", "Instant", Set.of("R"), "lea", "161", Rarity.COMMON, 4);
    private static final CollectionSearch.Row COUNTERSPELL =
            row("Counterspell", "Instant", Set.of("U"), "lea", "055", Rarity.COMMON, 2);
    private static final CollectionSearch.Row TEFERI = row(
            "Teferi, Hero of Dominaria", "Legendary Planeswalker - Teferi",
            Set.of("W", "U"), "dom", "207", Rarity.MYTHIC, 1);
    private static final CollectionSearch.Row SOL_RING =
            row("Sol Ring", "Artifact", Set.of(), "lea", "270", Rarity.UNCOMMON, 3);
    private static final CollectionSearch.Row UNKNOWN = new CollectionSearch.Row(
            CardIdentity.ofPrinting(
                    UUID.fromString("99999999-9999-4999-8999-999999999999"), false),
            7, null);

    private static final List<CollectionSearch.Row> BINDER =
            List.of(BOLT, COUNTERSPELL, TEFERI, SOL_RING, UNKNOWN);

    @Test
    @DisplayName("nothing asked for is everything, by name")
    void everythingByName() {
        List<CollectionSearch.Row> found =
                CollectionSearch.run(BINDER, CollectionSearch.Query.everything());

        assertThat(names(found))
                .containsExactly("Counterspell", "Lightning Bolt", "Sol Ring",
                        "Teferi, Hero of Dominaria", "");
    }

    @Test
    @DisplayName("typing half a name finds it")
    void textFindsByName() {
        assertThat(names(run(CollectionSearch.Query.everything().searchingFor("bolt"))))
                .containsExactly("Lightning Bolt");
        assertThat(names(run(CollectionSearch.Query.everything().searchingFor("TEFERI"))))
                .containsExactly("Teferi, Hero of Dominaria");
    }

    @Test
    @DisplayName("words can be typed in any order")
    void wordsAreNotAPhrase() {
        // Somebody typing a half-remembered name types the words in any order, and "bolt
        // lightning" is the same question as "lightning bolt".
        assertThat(names(run(CollectionSearch.Query.everything().searchingFor("bolt lightning"))))
                .containsExactly("Lightning Bolt");
        assertThat(names(run(CollectionSearch.Query.everything().searchingFor("instant lea"))))
                .containsExactly("Counterspell", "Lightning Bolt");
    }

    @Test
    @DisplayName("a color means at least that color")
    void colorIsAtLeast() {
        // Asking for white and blue finds the Azorius card and not the mono-white ones,
        // which is what every Magic player already means by it.
        assertThat(names(run(query(Set.of("U"), null, "")))).
                containsExactly("Counterspell", "Teferi, Hero of Dominaria");
        assertThat(names(run(query(Set.of("W", "U"), null, ""))))
                .containsExactly("Teferi, Hero of Dominaria");
        assertThat(names(run(query(Set.of("G"), null, "")))).isEmpty();
    }

    @Test
    @DisplayName("colorless is the absence of every color, not a color")
    void colorlessIsItsOwnQuestion() {
        assertThat(names(run(query(Set.of("C"), null, "")))).containsExactly("Sol Ring");
    }

    @Test
    @DisplayName("a rarity and a type narrow it further, and every filter is and")
    void filtersCompound() {
        assertThat(names(run(query(Set.of(), Rarity.COMMON, ""))))
                .containsExactly("Counterspell", "Lightning Bolt");
        assertThat(names(run(query(Set.of(), null, "instant"))))
                .containsExactly("Counterspell", "Lightning Bolt");
        assertThat(names(run(query(Set.of("R"), Rarity.COMMON, "instant"))))
                .containsExactly("Lightning Bolt");
        assertThat(names(run(query(Set.of("R"), Rarity.MYTHIC, "instant")))).isEmpty();
    }

    @Test
    @DisplayName("one set at a time, and a set is the whole set")
    void bySet() {
        CollectionSearch.Query dominaria = new CollectionSearch.Query(
                "", "DOM", Set.of(), null, "", CollectionSearch.Sort.NAME, false);

        assertThat(names(run(dominaria))).containsExactly("Teferi, Hero of Dominaria");
    }

    @Test
    @DisplayName("a card nobody has looked up yet is still owned, and sorts to the end")
    void unknownCardsSurviveAndSortLast() {
        // It is in the collection and it is counted. Nothing true can be said about it, so
        // it answers no filter - but hiding a card somebody owns would be worse.
        assertThat(CollectionSearch.run(BINDER, CollectionSearch.Query.everything()))
                .last().isEqualTo(UNKNOWN);
        assertThat(run(CollectionSearch.Query.everything().searchingFor("bolt")))
                .doesNotContain(UNKNOWN);
        assertThat(CollectionSearch.run(
                        BINDER, CollectionSearch.Query.everything()
                                .orderedBy(CollectionSearch.Sort.NAME, true))
                .getLast())
                .as("last however the rest are ordered")
                .isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("by rarity puts the thing you were looking for at the top")
    void byRarity() {
        List<CollectionSearch.Row> found = CollectionSearch.run(BINDER,
                CollectionSearch.Query.everything()
                        .orderedBy(CollectionSearch.Sort.RARITY, false));

        assertThat(names(found)).containsExactly("Teferi, Hero of Dominaria", "Sol Ring",
                "Counterspell", "Lightning Bolt", "");
    }

    @Test
    @DisplayName("by color reads in the order a player reads colors in")
    void byColor() {
        // WUBRG rather than alphabetical, mono-colored before gold, colorless at the end -
        // which is how anybody lays a collection out.
        List<CollectionSearch.Row> found = CollectionSearch.run(BINDER,
                CollectionSearch.Query.everything()
                        .orderedBy(CollectionSearch.Sort.COLOR, false));

        assertThat(names(found)).containsExactly("Counterspell", "Lightning Bolt",
                "Teferi, Hero of Dominaria", "Sol Ring", "");
    }

    @Test
    @DisplayName("by count is how many you have, most first")
    void byCount() {
        List<CollectionSearch.Row> found = CollectionSearch.run(BINDER,
                CollectionSearch.Query.everything()
                        .orderedBy(CollectionSearch.Sort.COUNT, false));

        assertThat(names(found)).containsExactly("Lightning Bolt", "Sol Ring", "Counterspell",
                "Teferi, Hero of Dominaria", "");
    }

    @Test
    @DisplayName("by set puts a set in its own order, two before ten")
    void bySetThenCollectorNumber() {
        CollectionSearch.Row ten =
                row("Ten", "Instant", Set.of("R"), "tst", "10", Rarity.COMMON, 1);
        CollectionSearch.Row two =
                row("Two", "Instant", Set.of("R"), "tst", "2", Rarity.COMMON, 1);
        CollectionSearch.Row letters =
                row("Star", "Instant", Set.of("R"), "tst", "2a", Rarity.COMMON, 1);

        List<CollectionSearch.Row> found = CollectionSearch.run(List.of(ten, letters, two),
                CollectionSearch.Query.everything().orderedBy(CollectionSearch.Sort.SET, false));

        assertThat(names(found)).containsExactly("Two", "Star", "Ten");
    }

    @Test
    @DisplayName("the same collection sorts the same way twice")
    void theOrderDoesNotWander() {
        // A screen that reshuffles between two openings of it is a screen nobody can learn.
        List<CollectionSearch.Row> shuffled = new ArrayList<>(BINDER);
        java.util.Collections.reverse(shuffled);

        for (CollectionSearch.Sort sort : CollectionSearch.Sort.values()) {
            CollectionSearch.Query query =
                    CollectionSearch.Query.everything().orderedBy(sort, false);
            assertThat(CollectionSearch.run(shuffled, query))
                    .as(sort.toString())
                    .isEqualTo(CollectionSearch.run(BINDER, query));
        }
    }

    @Test
    @DisplayName("a row with none of a card in it is not in the collection")
    void zeroIsNotOwned() {
        CollectionSearch.Row none =
                new CollectionSearch.Row(BOLT.card(), 0, BOLT.about());

        assertThat(CollectionSearch.run(List.of(none), CollectionSearch.Query.everything()))
                .isEmpty();
    }

    @Test
    @DisplayName("nothing to search is nothing found, not a crash")
    void nothingIsNothing() {
        assertThat(CollectionSearch.run(null, null)).isEmpty();
        assertThat(CollectionSearch.run(List.of(), null)).isEmpty();
        assertThat(CollectionSearch.run(BINDER, null)).hasSize(BINDER.size());
    }

    // ------------------------------------------------------------------ bits

    private static List<CollectionSearch.Row> run(CollectionSearch.Query query) {
        return CollectionSearch.run(BINDER, query);
    }

    private static CollectionSearch.Query query(Set<String> colors, Rarity rarity, String type) {
        return new CollectionSearch.Query(
                "", "", colors, rarity, type, CollectionSearch.Sort.NAME, false);
    }

    private static List<String> names(List<CollectionSearch.Row> rows) {
        return rows.stream().map(CollectionSearch.Row::name).toList();
    }

    private static CollectionSearch.Row row(String name, String typeLine, Set<String> colors,
            String setCode, String collectorNumber, Rarity rarity, int count) {
        UUID id = UUID.nameUUIDFromBytes(
                (setCode + collectorNumber).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CardMetadata about = new CardMetadata(
                id, id, name, "", 0.0, typeLine, "", colors, colors, List.of(), "normal",
                setCode, setCode.equals("dom") ? "Dominaria" : "Limited Edition Alpha",
                collectorNumber, rarity, false, true, true, false, false,
                List.of("paper"), Map.of(), Map.of(), "");
        return new CollectionSearch.Row(CardIdentity.ofPrinting(id, false), count, about);
    }

    @Nested
    @DisplayName("compiling the query once")
    class CompiledOnce {

        /** A binder big enough that parsing per row would be doing it thousands of times. */
        private static List<CollectionSearch.Row> aBigBinder() {
            List<CollectionSearch.Row> rows = new java.util.ArrayList<>();
            for (int at = 0; at < 2_000; at++) {
                rows.addAll(BINDER);
            }
            return List.copyOf(rows);
        }

        @Test
        @DisplayName("finds exactly what testing each row on its own finds")
        void sameAnswerAsRowByRow() {
            // The property the optimisation has to keep: run() hoists the parse out of the
            // loop, and matches() still parses for its one row, so if hoisting changed any
            // answer these two would disagree.
            for (String typed : List.of("", "   ", "creature", "t:creature", "c:g",
                    "mv<=4", "o:draw", "t:creature c:g mv<=4 o:draw", "-t:land",
                    "\"exact phrase\"", "nonsense:term", "r:mythic")) {
                CollectionSearch.Query query = new CollectionSearch.Query(
                        typed, "", java.util.Set.of(), null, "",
                        CollectionSearch.Sort.NAME, false);
                List<CollectionSearch.Row> byRun = CollectionSearch.run(BINDER, query);
                List<CollectionSearch.Row> oneAtATime = BINDER.stream()
                        .filter(row -> row.count() > 0)
                        .filter(row -> CollectionSearch.matches(row, query))
                        .toList();
                assertThat(byRun)
                        .as("query %s", typed)
                        .containsExactlyInAnyOrderElementsOf(oneAtATime);
            }
        }

        @Test
        @DisplayName("answers a large binder the same way as a small one")
        void scaleChangesNothing() {
            CollectionSearch.Query query = new CollectionSearch.Query(
                    "t:creature", "", java.util.Set.of(), null, "",
                    CollectionSearch.Sort.NAME, false);
            int small = CollectionSearch.run(BINDER, query).size();
            int large = CollectionSearch.run(aBigBinder(), query).size();
            assertThat(large).isEqualTo(small * 2_000);
        }

        @Test
        @DisplayName("still says nothing is known about a card with no metadata")
        void unknownMetadataUnchanged() {
            // The one row whose answer depends on whether anything was asked at all, which is
            // exactly the branch that returns before the compiled terms are consulted.
            CollectionSearch.Row unknown = BINDER.stream()
                    .filter(row -> row.about() == null)
                    .findFirst()
                    .orElse(null);
            if (unknown == null) {
                return;
            }
            CollectionSearch.Query filtered = new CollectionSearch.Query(
                    "creature", "", java.util.Set.of(), null, "",
                    CollectionSearch.Sort.NAME, false);
            assertThat(CollectionSearch.run(List.of(unknown), filtered)).isEmpty();
            assertThat(CollectionSearch.run(List.of(unknown), CollectionSearch.Query.everything()))
                    .containsExactly(unknown);
        }
    }
}