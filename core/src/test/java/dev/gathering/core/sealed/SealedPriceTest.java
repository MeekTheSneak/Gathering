package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a sealed product is worth, worked out from what is inside it. */
class SealedPriceTest {

    private static final SealedProduct PACK = booster("pack-play", "play");
    private static final SealedProduct COLLECTOR = booster("pack-collector", "collector");

    private static final SealedProduct BOX = new SealedProduct(
            "box-play", "Test Play Booster Box", "tst", "booster_box", "play", 420,
            new SealedProduct.Contents(
                    List.of(), List.of(new SealedProduct.Held("pack-play", "Pack", 30)),
                    List.of(), List.of(), List.of()));

    private static final SealedProduct CASE = new SealedProduct(
            "case", "Test Case", "tst", "case", "play", 2520,
            new SealedProduct.Contents(
                    List.of(), List.of(new SealedProduct.Held("box-play", "Box", 6)),
                    List.of(), List.of(), List.of()));

    private static final SealedProduct BUNDLE = new SealedProduct(
            "bundle", "Test Bundle", "tst", "bundle", "default", 145,
            new SealedProduct.Contents(
                    List.of(new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play"),
                            new SealedProduct.Booster("tst", "play")),
                    List.of(),
                    List.of(CardIdentity.ofPrinting(
                            UUID.fromString("11111111-1111-4111-8111-111111111111"), true)),
                    List.of(), List.of("spindown die")));

    private static final SealedProduct PRECON = new SealedProduct(
            "precon", "Test Commander Deck", "tst", "commander_deck", "default", 100,
            new SealedProduct.Contents(
                    List.of(), List.of(), List.of(),
                    List.of(new SealedProduct.InDeck("Test Commander Deck", "tst")),
                    List.of()));

    private static final SealedCatalogue CATALOGUE = Catalogues.of(PACK, COLLECTOR, BOX, CASE);

    @Test
    @DisplayName("a booster is the unit")
    void aBoosterIsOne() {
        assertThat(SealedPrice.inBoosters(PACK, CATALOGUE)).isEqualTo(1);
        assertThat(SealedPrice.inBoosters(COLLECTOR, CATALOGUE))
                .as("a collector booster is one booster to buy; being the rare one is what "
                        + "opening it is for, not what it costs")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a box of thirty costs thirty of them")
    void aBoxIsWhatItHolds() {
        assertThat(SealedPrice.inBoosters(BOX, CATALOGUE)).isEqualTo(30);
    }

    @Test
    @DisplayName("and a case of six boxes costs a hundred and eighty")
    void aCaseIsBoxesOfPacks() {
        // Two levels down. Nothing is cheaper for being bought in bulk: a box is one click
        // instead of thirty, not a saving.
        assertThat(SealedPrice.inBoosters(CASE, CATALOGUE)).isEqualTo(180);
    }

    @Test
    @DisplayName("a bundle is its packs plus what else is in the box")
    void aBundleIsPacksAndExtras() {
        // Nine packs and a foil promo. The die is not a card and costs nothing.
        assertThat(SealedPrice.inBoosters(BUNDLE, CATALOGUE)).isEqualTo(10);
    }

    @Test
    @DisplayName("a deck is priced by what is in it, because it has no packs")
    void aDeckIsPricedByItsCards() {
        // A hundred cards at fifteen to a booster is seven.
        assertThat(SealedPrice.inBoosters(PRECON, CATALOGUE)).isEqualTo(7);
    }

    @Test
    @DisplayName("a box whose pack this set has never heard of is still not free")
    void anUnknownPackIsStillWorthSomething() {
        SealedProduct strange = new SealedProduct(
                "box-strange", "Box of somebody else's packs", "tst", "booster_box", "play", 0,
                new SealedProduct.Contents(
                        List.of(), List.of(new SealedProduct.Held("nobody", "?", 12)),
                        List.of(), List.of(), List.of()));

        assertThat(SealedPrice.inBoosters(strange, CATALOGUE)).isEqualTo(12);
        assertThat(SealedPrice.inBoosters(strange, SealedCatalogue.EMPTY)).isEqualTo(12);
    }

    @Test
    @DisplayName("a product that holds itself does not hang the server")
    void nestingIsBounded() {
        SealedProduct itself = new SealedProduct(
                "loop", "A box of itself", "tst", "case", "play", 0,
                new SealedProduct.Contents(
                        List.of(), List.of(new SealedProduct.Held("loop", "itself", 2)),
                        List.of(), List.of(), List.of()));

        int worth = SealedPrice.inBoosters(itself, Catalogues.of(itself));

        assertThat(worth).isPositive();
        assertThat(worth).isLessThan(1000);
    }

    @Test
    @DisplayName("nothing is ever free")
    void nothingIsFree() {
        SealedProduct nothing = new SealedProduct(
                "empty", "An empty box", "tst", "booster_pack", "play", 0, null);

        assertThat(SealedPrice.inBoosters(nothing, CATALOGUE)).isEqualTo(SealedPrice.CHEAPEST);
        assertThat(SealedPrice.inBoosters(null, CATALOGUE)).isEqualTo(SealedPrice.CHEAPEST);
        assertThat(SealedPrice.of(nothing, CATALOGUE, 0)).isGreaterThanOrEqualTo(1);
        assertThat(SealedPrice.of(nothing, CATALOGUE, -5)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the money is the boosters times what a booster costs")
    void theMoneyIsJustMultiplication() {
        assertThat(SealedPrice.of(PACK, CATALOGUE, 2)).isEqualTo(2);
        assertThat(SealedPrice.of(BOX, CATALOGUE, 2)).isEqualTo(60);
        assertThat(SealedPrice.of(PRECON, CATALOGUE, 3)).isEqualTo(21);
    }

    @Test
    @DisplayName("a deck that comes with a sample pack is a deck and a sample pack")
    void aDeckWithExtrasIsBoth() {
        // Out of the real data: a Commander precon is a hundred cards, a collector sample
        // pack, a life counter and a deck box. Pricing it as one or the other put a hundred
        // cards on the shelf for two boosters.
        SealedProduct precon = new SealedProduct(
                "precon-plus", "Test Commander Deck", "tst", "deck", "commander", 100,
                new SealedProduct.Contents(
                        List.of(),
                        List.of(new SealedProduct.Held("pack-collector", "Sample", 1)),
                        List.of(), List.of(new SealedProduct.InDeck("Test Commander Deck", "tst")),
                        List.of("spinning life counter", "paper deck box")));

        assertThat(SealedPrice.inBoosters(precon, CATALOGUE)).isEqualTo(8);
    }

    @Test
    @DisplayName("a product that only exists on somebody else's screen is not sold")
    void digitalProductsAreNotSold() {
        // An MTGO redemption is a real published product and it is a code, not a box.
        SealedProduct redemption = new SealedProduct(
                "redemption", "Test MTGO Redemption", "tst", "box_set", "mtgo_redemption", 281,
                new SealedProduct.Contents(
                        List.of(), List.of(), List.of(),
                        List.of(new SealedProduct.InDeck("Test Redemption", "tst")),
                        List.of()));

        assertThat(SealedPrice.isSellable(redemption)).isFalse();
    }

    @Test
    @DisplayName("what a shop will sell is anything with something in the box")
    void onlyRealBoxesAreSold() {
        assertThat(SealedPrice.isSellable(PACK)).isTrue();
        assertThat(SealedPrice.isSellable(BOX)).isTrue();
        assertThat(SealedPrice.isSellable(BUNDLE)).isTrue();
        assertThat(SealedPrice.isSellable(PRECON)).isTrue();

        // Whatever MTGJSON happens to call it. There was a list of categories here and it
        // sold a case of fifteen prerelease packs while refusing one prerelease pack, because
        // only the first is called a "case".
        SealedProduct kit = new SealedProduct(
                "kit", "Test Prerelease Pack", "tst", "limited_aid_tool", "prerelease_kit", 0,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", "prerelease")),
                        List.of(), List.of(), List.of(), List.of("spindown")));
        assertThat(SealedPrice.isSellable(kit)).isTrue();

        SealedProduct empty = new SealedProduct(
                "art", "An art print", "tst", "art_series", "default", 0, null);
        assertThat(SealedPrice.isSellable(empty))
                .as("nothing inside it is nothing to sell")
                .isFalse();
        assertThat(SealedPrice.isSellable(null)).isFalse();
    }

    // ------------------------------------------------------------------ bits

    private static SealedProduct booster(String id, String kind) {
        return new SealedProduct(id, "Test " + kind, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }

}
