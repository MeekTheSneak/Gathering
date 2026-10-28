package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.SetRelease;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which cards of a set are still to find.
 * <p>The thing this has to get right is agreeing with {@link SetCompletion}, because a player
 * can now see both at once: a number saying three hundred and seventy-two over a list of
 * three hundred and eighty rows is worse than no list at all. So most of what is checked here
 * is that the two count by the same rules.
 */
class MissingCardsTest {

    private static final SetRelease SET =
            new SetRelease("tst", "The Test Set", "expansion", "2026-01-01", false, 320, 10);

    private static CardMetadata card(String collectorNumber) {
        return card(collectorNumber, Rarity.COMMON);
    }

    private static CardMetadata card(String collectorNumber, Rarity rarity) {
        UUID id = UUID.nameUUIDFromBytes(("tst" + collectorNumber).getBytes(StandardCharsets.UTF_8));
        return new CardMetadata(
                id, id, "Card " + collectorNumber, "", 0.0, "Creature", "", Set.of(), Set.of(),
                List.of(), "normal", "tst", "The Test Set", collectorNumber, rarity,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(), "");
    }

    private static List<CardMetadata> numbered(int from, int to) {
        List<CardMetadata> cards = new ArrayList<>();
        for (int number = from; number <= to; number++) {
            cards.add(card(Integer.toString(number)));
        }
        return cards;
    }

    @Test
    @DisplayName("an empty collection is missing the whole set")
    void missingEverything() {
        MissingCards missing = MissingCards.of(SET, numbered(1, 10), List.of());

        assertThat(missing.count()).isEqualTo(10);
        assertThat(missing.cards()).extracting(MissingCards.Card::number)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(missing.code()).isEqualTo("tst");
        assertThat(missing.name()).isEqualTo("The Test Set");
    }

    @Test
    @DisplayName("what is owned is not listed, and the rest stays in printed order")
    void ownedIsNotListed() {
        MissingCards missing = MissingCards.of(
                SET, numbered(1, 10), List.of(card("2"), card("5"), card("9")));

        assertThat(missing.cards()).extracting(MissingCards.Card::number)
                .containsExactly(1, 3, 4, 6, 7, 8, 10);
    }

    @Test
    @DisplayName("a complete set has nothing to find")
    void nothingMissing() {
        MissingCards missing = MissingCards.of(SET, numbered(1, 10), numbered(1, 10));

        assertThat(missing.count()).isZero();
        assertThat(missing.cards()).isEmpty();
    }

    @Test
    @DisplayName("cards numbered past the printed size are extras, never something to find")
    void extrasAreNotMissing() {
        // Eleven upwards are the showcases and promos. Nobody is short of those.
        List<CardMetadata> everything = new ArrayList<>(numbered(1, 10));
        everything.addAll(numbered(11, 40));

        MissingCards missing = MissingCards.of(SET, everything, numbered(1, 10));

        assertThat(missing.count()).isZero();
    }

    @Test
    @DisplayName("owning either variant of one number fills that one slot")
    void variantsFillOneSlot() {
        List<CardMetadata> everything = new ArrayList<>(numbered(1, 10));
        everything.add(card("4a"));
        everything.add(card("4b"));

        MissingCards missing = MissingCards.of(SET, everything, List.of(card("4b")));

        assertThat(missing.cards()).extracting(MissingCards.Card::number).doesNotContain(4);
        assertThat(missing.count()).isEqualTo(9);
    }

    @Test
    @DisplayName("a slot several printings could fill is one row, shown as the plainest")
    void oneRowPerSlot() {
        List<CardMetadata> everything = new ArrayList<>(numbered(1, 10));
        everything.add(card("7a"));
        everything.add(card("7b"));

        MissingCards missing = MissingCards.of(SET, everything, List.of());

        assertThat(missing.count()).isEqualTo(10);
        assertThat(missing.cards()).filteredOn(row -> row.number() == 7)
                .singleElement()
                .extracting(MissingCards.Card::name)
                .isEqualTo("Card 7");
    }

    @Test
    @DisplayName("it agrees with the number SetCompletion puts over it")
    void agreesWithTheCount() {
        List<CardMetadata> everything = new ArrayList<>(numbered(1, 10));
        everything.addAll(numbered(11, 25));
        List<CardMetadata> owned = List.of(card("1"), card("2"), card("3"), card("14"));

        SetCompletion counted =
                SetCompletion.of(owned, Map.of("tst", SET)).stream()
                        .filter(row -> row.code().equals("tst")).findFirst().orElseThrow();
        MissingCards missing = MissingCards.of(SET, everything, owned);

        assertThat(missing.count()).isEqualTo(counted.missing());
    }

    @Test
    @DisplayName("a card's rarity comes along, because that is how hard it will be to find")
    void rarityComesAlong() {
        MissingCards missing = MissingCards.of(
                SET, List.of(card("1", Rarity.MYTHIC), card("2", Rarity.COMMON)), List.of());

        assertThat(missing.cards()).extracting(MissingCards.Card::rarity)
                .containsExactly(Rarity.MYTHIC, Rarity.COMMON);
    }

    @Test
    @DisplayName("nothing known about the set is not the same as owning all of it")
    void nothingKnown() {
        assertThat(MissingCards.of(null, List.of(), List.of())).isEqualTo(MissingCards.NONE);
        assertThat(MissingCards.of(SET, null, List.of())).isEqualTo(MissingCards.NONE);
    }

    @Test
    @DisplayName("an old set with no printed size falls back to its card count")
    void oldSet() {
        SetRelease old = new SetRelease("leg", "Legends", "expansion", "1994-06-01", false, 5, 0);

        MissingCards missing = MissingCards.of(
                old, List.of(card("1"), card("2"), card("3"), card("4"), card("5")), List.of());

        assertThat(missing.count()).isEqualTo(5);
    }
}
