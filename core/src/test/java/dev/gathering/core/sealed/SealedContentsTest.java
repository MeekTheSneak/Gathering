package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a shop can actually hand over when somebody buys something. */
class SealedContentsTest {

    private static final CardIdentity PROMO = CardIdentity.ofPrinting(
            UUID.fromString("11111111-1111-4111-8111-111111111111"), true);

    private static final SealedProduct PACK = booster("pack-play", "play");

    private static final SealedProduct BOX = holding("box", "Box", 30, "pack-play");
    private static final SealedProduct CASE = holding("case", "Case", 6, "box");

    private static final SealedProduct BUNDLE = new SealedProduct(
            "bundle", "Bundle", "tst", "bundle", "default", 145,
            new SealedProduct.Contents(
                    List.of(new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play")),
                    List.of(), List.of(PROMO), List.of(), List.of("spindown die")));

    private static final SealedProduct PRECON = new SealedProduct(
            "precon", "Commander Deck", "tst", "deck", "commander", 100,
            new SealedProduct.Contents(
                    List.of(), List.of(new SealedProduct.Held("pack-play", "Sample", 1)),
                    List.of(), List.of(new SealedProduct.InDeck("Animated Army", "tst")),
                    List.of()));

    private static final SealedDeck DECK = new SealedDeck(
            "Animated Army", "tst",
            List.of(card(1)),
            List.of(card(2), card(2), card(3)),
            List.of());

    private static final SealedCatalogue CATALOGUE =
            Catalogues.of(List.of(DECK), PACK, BOX, CASE, BUNDLE, PRECON);

    /** The same catalogue with the deck lists never read, which is what an old file gives. */
    private static final SealedCatalogue WITHOUT_DECKS =
            Catalogues.of(PACK, BOX, CASE, BUNDLE, PRECON);

    @Test
    @DisplayName("a pack is one booster")
    void aPackIsOneBooster() {
        SealedContents.Bag bag = SealedContents.of(PACK, CATALOGUE).orElseThrow();

        assertThat(bag.boosters()).hasSize(1);
        assertThat(bag.cards()).isEmpty();
        assertThat(bag.pieces()).isEqualTo(1);
    }

    @Test
    @DisplayName("a box is thirty of the pack it names")
    void aBoxIsItsPacks() {
        assertThat(SealedContents.of(BOX, CATALOGUE).orElseThrow().boosters()).hasSize(30);
    }

    @Test
    @DisplayName("and a case is six boxes of them")
    void aCaseIsBoxesOfPacks() {
        assertThat(SealedContents.of(CASE, CATALOGUE).orElseThrow().boosters()).hasSize(180);
    }

    @Test
    @DisplayName("a bundle is its packs and its promo, and not its die")
    void aBundleIsPacksAndAPromo() {
        SealedContents.Bag bag = SealedContents.of(BUNDLE, CATALOGUE).orElseThrow();

        assertThat(bag.boosters()).hasSize(2);
        assertThat(bag.cards()).containsExactly(PROMO);
    }

    @Test
    @DisplayName("a precon is its deck and the sample pack that came in the box")
    void aPreconIsItsDeck() {
        SealedContents.Bag bag = SealedContents.of(PRECON, CATALOGUE).orElseThrow();

        assertThat(bag.decks()).containsExactly(DECK);
        assertThat(bag.boosters()).as("the sample pack is in the box too").hasSize(1);
        assertThat(bag.pieces())
                .as("a deck counts as its cards, not as one thing")
                .isEqualTo(1 + DECK.size());
    }

    @Test
    @DisplayName("a precon whose deck nothing has read is not on the shelf at all")
    void anUnreadDeckIsNotSold() {
        // Taking somebody's diamonds and giving them the sample pack that came in the box
        // would be worse than not stocking it.
        assertThat(SealedContents.of(PRECON, WITHOUT_DECKS)).isEmpty();
        assertThat(SealedContents.canBeHandedOver(PRECON, WITHOUT_DECKS)).isFalse();
    }

    @Test
    @DisplayName("opening goes one level down, not all the way")
    void openingIsOneLevel() {
        SealedContents.Layer layer = SealedContents.opening(CASE, CATALOGUE);

        assertThat(layer.boxes()).hasSize(1);
        assertThat(layer.boxes().get(0).product()).isEqualTo(BOX);
        assertThat(layer.boxes().get(0).count())
                .as("six boxes, not a hundred and eighty loose packs")
                .isEqualTo(6);
        assertThat(layer.boosters()).isEmpty();

        SealedContents.Layer box = SealedContents.opening(BOX, CATALOGUE);
        assertThat(box.boxes()).hasSize(1);
        assertThat(box.boxes().get(0).product()).isEqualTo(PACK);
        assertThat(box.boxes().get(0).count()).isEqualTo(30);
    }

    @Test
    @DisplayName("opening a precon gives the deck and the pack")
    void openingAPreconGivesTheDeck() {
        SealedContents.Layer layer = SealedContents.opening(PRECON, CATALOGUE);

        assertThat(layer.decks()).containsExactly(DECK);
        assertThat(layer.boxes()).hasSize(1);
    }

    @Test
    @DisplayName("a booster has nothing to open into; the opener does that")
    void aBoosterDoesNotOpenHere() {
        assertThat(SealedContents.opening(PACK, CATALOGUE).isEmpty()).isTrue();
        assertThat(SealedContents.opening(null, CATALOGUE).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("nothing opens into a hole")
    void openingIsWholeOrNothing() {
        SealedProduct strange = holding("strange", "Box of nothing", 12, "nobody");

        assertThat(SealedContents.opening(strange, CATALOGUE).isEmpty()).isTrue();
        assertThat(SealedContents.opening(PRECON, WITHOUT_DECKS).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a box whose pack this catalogue has never heard of is sold whole or not at all")
    void halfABoxIsWorseThanNoBox() {
        SealedProduct strange = holding("strange", "Box of nothing", 12, "nobody");

        assertThat(SealedContents.of(strange, CATALOGUE)).isEmpty();
        assertThat(SealedContents.of(BOX, SealedCatalogue.EMPTY)).isEmpty();
    }

    @Test
    @DisplayName("a product that holds itself does not hang the server")
    void nestingIsBounded() {
        SealedProduct itself = holding("loop", "A box of itself", 2, "loop");

        assertThat(SealedContents.of(itself, Catalogues.of(itself))).isEmpty();
    }

    @Test
    @DisplayName("nothing that comes to more than one purchase can carry")
    void aPurchaseIsBounded() {
        SealedProduct pallet = holding("pallet", "A pallet", 40, "case");

        assertThat(SealedContents.of(pallet, Catalogues.of(PACK, BOX, CASE, pallet))).isEmpty();
    }

    @Test
    @DisplayName("nothing inside is nothing to give")
    void nothingIsNothing() {
        SealedProduct empty = new SealedProduct(
                "empty", "An empty box", "tst", "booster_pack", "play", 0, null);

        assertThat(SealedContents.of(empty, CATALOGUE)).isEmpty();
        assertThat(SealedContents.of(null, CATALOGUE)).isEmpty();
    }

    // ------------------------------------------------------------------ bits

    private static CardIdentity card(int which) {
        return CardIdentity.ofPrinting(
                UUID.fromString("2222222" + which + "-1111-4111-8111-111111111111"), false);
    }

    private static SealedProduct booster(String id, String kind) {
        return new SealedProduct(id, "Test " + kind, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }

    private static SealedProduct holding(String id, String name, int count, String what) {
        return new SealedProduct(id, name, "tst", "booster_box", "play", 0,
                new SealedProduct.Contents(
                        List.of(), List.of(new SealedProduct.Held(what, what, count)),
                        List.of(), List.of(), List.of()));
    }

}
