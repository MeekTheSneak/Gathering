package dev.gathering.core.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.testing.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * You play what you opened.
 *
 * <p>The one check in the mod that asks whether a card is yours rather than whether it is
 * legal, which is the whole of limited: four copies of the best card in the set is a fine
 * limited deck, and impossible because nobody opens four.
 */
class PoolCheckTest {

    private static final CardMetadata DELVER = Fixtures.card("delver_of_secrets");
    private static final CardMetadata SOL_RING = Fixtures.card("sol_ring");
    private static final CardMetadata LOTUS = Fixtures.card("black_lotus");
    private static final CardMetadata FOREST = Fixtures.card("forest");

    @Test
    @DisplayName("a deck built from its pool passes")
    void aDeckBuiltFromItsPoolPasses() {
        List<CardMetadata> pool = pool(DELVER, DELVER, SOL_RING);

        assertThat(PoolCheck.against(deck(copies(DELVER, 2), List.of(SOL_RING)), pool)).isEmpty();
        assertThat(PoolCheck.isCoveredBy(deck(copies(DELVER, 2), List.of(SOL_RING)), pool)).isTrue();
    }

    /** A card that came from somewhere else is named, which is the whole point of the check. */
    @Test
    void aCardThatWasNeverInThePoolIsNamed() {
        List<ValidationIssue> issues =
                PoolCheck.against(deck(List.of(LOTUS), List.of()), pool(DELVER, SOL_RING));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).code()).isEqualTo("not_in_pool");
        assertThat(issues.get(0).message()).contains(LOTUS.name());
        assertThat(issues.get(0).isError()).isTrue();
    }

    /** And so is a second copy of a card the pool has only one of. */
    @Test
    void aSecondCopyOfASingleCardIsCounted() {
        List<ValidationIssue> issues =
                PoolCheck.against(deck(copies(DELVER, 2), List.of()), pool(DELVER, SOL_RING));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).message()).contains("has 1").contains("uses 2");
    }

    /**
     * Basic lands are free, and unlimited.
     *
     * <p>The exception paper limited has always had, and the reason a forty-card deck out of
     * a forty-five-card pool is possible at all.
     */
    @Test
    void basicLandsAreFreeAndUnlimited() {
        assertThat(PoolCheck.against(deck(copies(FOREST, 17), List.of()), pool(DELVER)))
                .describedAs("seventeen forests out of a pool with none")
                .isEmpty();
    }

    /**
     * The sideboard counts too.
     *
     * <p>Boarding a card in between games puts it in the deck, so a card that is in neither
     * the pool nor a pack has appeared from somewhere either way - and checking only the
     * mainboard would leave the obvious way round the rule wide open.
     */
    @Test
    void theSideboardIsCheckedAsWell() {
        List<ValidationIssue> issues =
                PoolCheck.against(deck(List.of(DELVER), List.of(LOTUS)), pool(DELVER));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).message()).contains(LOTUS.name());
    }

    /** A pool that covers a deck exactly is still covered - the bound is inclusive. */
    @Test
    void usingEveryCopyInThePoolIsFine() {
        assertThat(PoolCheck.against(deck(copies(DELVER, 3), List.of()), pool(DELVER, DELVER, DELVER)))
                .isEmpty();
    }

    /** No pool means no opinion, which is what an ordinary imported deck gets. */
    @Test
    void aDeckWithNoPoolIsNotJudged() {
        assertThat(PoolCheck.against(deck(List.of(LOTUS), List.of()), null)).isEmpty();
    }

    /** An empty pool judges everything, which is what a pool of nothing should do. */
    @Test
    void anEmptyPoolCoversNothingButBasics() {
        assertThat(PoolCheck.against(deck(List.of(LOTUS), List.of()), List.of())).hasSize(1);
        assertThat(PoolCheck.against(deck(copies(FOREST, 40), List.of()), List.of())).isEmpty();
    }

    // --- helpers ---

    private static List<CardMetadata> pool(CardMetadata... cards) {
        return List.of(cards);
    }

    private static ValidatableDeck deck(List<CardMetadata> mainboard, List<CardMetadata> sideboard) {
        return new ValidatableDeck("Drafted", mainboard, List.of(), sideboard);
    }

    private static List<CardMetadata> copies(CardMetadata card, int many) {
        List<CardMetadata> cards = new ArrayList<>(many);
        for (int index = 0; index < many; index++) {
            cards.add(card);
        }
        return cards;
    }
}
