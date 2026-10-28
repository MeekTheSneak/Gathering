package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.SetRelease;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How much of a set somebody has.
 * <p>Almost all of this is about the difference between the cards a set was printed as and
 * the cards that merely carry its code, because that difference is the whole of what a
 * complete set means and getting it wrong tells a player who owns everything that they are
 * three hundred cards short.
 */
class SetCompletionTest {

    private static final SetRelease MODERN =
            new SetRelease("tst", "The Test Set", "expansion", "2026-01-01", false, 320, 281);
    private static final SetRelease OLD =
            new SetRelease("leg", "Legends", "expansion", "1994-06-01", false, 310, 0);
    private static final Map<String, SetRelease> SETS =
            Map.of("tst", MODERN, "leg", OLD);

    private static CardMetadata card(String setCode, String collectorNumber) {
        UUID id = UUID.nameUUIDFromBytes(
                (setCode + collectorNumber).getBytes(StandardCharsets.UTF_8));
        return new CardMetadata(
                id, id, "Card " + collectorNumber, "", 0.0, "Creature", "", Set.of(), Set.of(),
                List.of(), "normal", setCode, setCode, collectorNumber, Rarity.COMMON,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(), "");
    }

    private static List<CardMetadata> numbered(String setCode, int from, int to) {
        List<CardMetadata> cards = new ArrayList<>();
        for (int number = from; number <= to; number++) {
            cards.add(card(setCode, Integer.toString(number)));
        }
        return cards;
    }

    private static SetCompletion only(List<CardMetadata> owned) {
        List<SetCompletion> rows = SetCompletion.of(owned, SETS);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("a set counts what its cards say it has, not everything carrying its code")
    void theDenominatorIsThePrintedSize() {
        SetCompletion progress = only(numbered("tst", 1, 281));

        assertThat(progress.size()).isEqualTo(281);
        assertThat(progress.owned()).isEqualTo(281);
        assertThat(progress.isComplete())
                .describedAs("one of every numbered card is a complete set")
                .isTrue();
        assertThat(progress.missing()).isZero();
    }

    @Test
    @DisplayName("the borderless and the showcases are extras, not cards you are missing")
    void extrasAreNotMissingCards() {
        List<CardMetadata> owned = new ArrayList<>(numbered("tst", 1, 281));
        owned.add(card("tst", "300"));
        owned.add(card("tst", "301"));

        SetCompletion progress = only(owned);

        assertThat(progress.isComplete()).isTrue();
        assertThat(progress.extras()).isEqualTo(2);
        assertThat(progress.size()).isEqualTo(281);
    }

    @Test
    @DisplayName("two printings of one card fill one slot")
    void variantsOfANumberFillOneSlot() {
        SetCompletion progress = only(List.of(card("tst", "12a"), card("tst", "12b")));

        assertThat(progress.owned())
                .describedAs("12a and 12b are two printings of card twelve")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a set nobody printed a size for is as big as the cards in it")
    void anOldSetUsesItsCount() {
        SetCompletion progress = only(numbered("leg", 1, 10));

        assertThat(progress.size()).isEqualTo(310);
        assertThat(progress.missing()).isEqualTo(300);
    }

    @Test
    @DisplayName("a set is listed even when everything owned from it is an extra")
    void extrasAloneStillListTheSet() {
        SetCompletion progress = only(List.of(card("tst", "300")));

        assertThat(progress.owned()).isZero();
        assertThat(progress.extras()).isEqualTo(1);
        assertThat(progress.share()).isZero();
    }

    @Test
    @DisplayName("a set nothing is known about is left out rather than shown with no size")
    void unknownSetsAreLeftOut() {
        assertThat(SetCompletion.of(List.of(card("zzz", "1")), SETS)).isEmpty();
    }

    @Test
    @DisplayName("the set you are closest to finishing is first")
    void closestFirst() {
        List<CardMetadata> owned = new ArrayList<>(numbered("tst", 1, 200));
        owned.addAll(numbered("leg", 1, 10));

        List<SetCompletion> rows = SetCompletion.of(owned, SETS);

        assertThat(rows.stream().map(SetCompletion::code)).containsExactly("tst", "leg");
        assertThat(rows.get(0).share()).isGreaterThan(rows.get(1).share());
    }

    @Test
    @DisplayName("nothing owned is no rows, and nothing here divides by a size of zero")
    void theEmptyCases() {
        assertThat(SetCompletion.of(List.of(), SETS)).isEmpty();
        assertThat(SetCompletion.of(null, SETS)).isEmpty();
        assertThat(new SetCompletion("x", "", 4, 0, 0).share()).isZero();
        assertThat(new SetCompletion("x", "", 4, 0, 0).isComplete())
                .describedAs("a set whose size nobody knows is never claimed finished")
                .isFalse();
    }
}
