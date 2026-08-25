package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What is on a shop's shelf, and in what order. */
class SealedShelfTest {

    private static final SealedProduct PACK = booster("pack-play", "play");
    private static final SealedProduct COLLECTOR = booster("pack-collector", "collector");

    private static final SealedProduct BOX = new SealedProduct(
            "box", "Test Play Booster Box", "tst", "booster_box", "play", 420,
            new SealedProduct.Contents(
                    List.of(), List.of(new SealedProduct.Held("pack-play", "Pack", 30)),
                    List.of(), List.of(), List.of()));

    private static final SealedProduct PRECON = new SealedProduct(
            "precon", "Test Commander Deck", "tst", "deck", "commander", 100,
            new SealedProduct.Contents(
                    List.of(), List.of(), List.of(),
                    List.of(new SealedProduct.InDeck("Test Commander Deck", "tst")),
                    List.of()));

    private static final SealedProduct ART = new SealedProduct(
            "art", "Test Art Series", "tst", "art_series", "default", 0, null);

    private static final MtgjsonProducts.Reading CATALOGUE = new MtgjsonProducts.Reading(
            "tst", List.of(BOX, ART, PRECON, COLLECTOR, PACK), List.of());

    @Test
    @DisplayName("packs come first, and the cheapest of them first of all")
    void packsLeadTheShelf() {
        SealedShelf shelf = SealedShelf.of(CATALOGUE, 2);

        assertThat(shelf.items()).extracting(SealedShelf.Item::name)
                .containsExactly("Test collector", "Test play", "Test Play Booster Box");
    }

    @Test
    @DisplayName("and everything is priced by what is in it")
    void everythingIsPriced() {
        SealedShelf shelf = SealedShelf.of(CATALOGUE, 2);

        assertThat(priceOf(shelf, "Test play")).isEqualTo(2);
        assertThat(priceOf(shelf, "Test Play Booster Box")).isEqualTo(60);
    }

    @Test
    @DisplayName("nothing that is not a product anybody published is on it")
    void onlyRealProducts() {
        SealedShelf shelf = SealedShelf.of(CATALOGUE, 2);

        assertThat(shelf.items()).extracting(SealedShelf.Item::name)
                .doesNotContain("Test Art Series");
        assertThat(shelf.items()).hasSize(3);
    }

    @Test
    @DisplayName("nor anything the shop could not actually hand over")
    void onlyProductsThatCanBeGiven() {
        // A Commander precon is a real product a real shop stocks, and MTGJSON names its deck
        // rather than listing it. Taking somebody's diamonds and handing them the sample pack
        // that came in the box would be worse than not stocking it at all.
        SealedShelf shelf = SealedShelf.of(CATALOGUE, 2);

        assertThat(shelf.items()).extracting(SealedShelf.Item::name)
                .doesNotContain("Test Commander Deck");
    }

    @Test
    @DisplayName("the same catalogue is the same shelf twice")
    void theShelfDoesNotWander() {
        // A shop whose rows moved between two visits is a shop nobody can learn.
        assertThat(SealedShelf.of(CATALOGUE, 2)).isEqualTo(SealedShelf.of(CATALOGUE, 2));
    }

    @Test
    @DisplayName("what a booster costs moves everything on it")
    void theOneNumberMovesTheShelf() {
        SealedShelf cheap = SealedShelf.of(CATALOGUE, 1);
        SealedShelf dear = SealedShelf.of(CATALOGUE, 4);

        assertThat(priceOf(cheap, "Test Play Booster Box")).isEqualTo(30);
        assertThat(priceOf(dear, "Test Play Booster Box")).isEqualTo(120);
    }

    @Test
    @DisplayName("several sets run together into one shelf, still in order")
    void severalSetsAreOneShelf() {
        MtgjsonProducts.Reading other = new MtgjsonProducts.Reading(
                "oth", List.of(booster("pack-other", "draft")), List.of());

        SealedShelf both = SealedShelf.of(List.of(
                SealedShelf.of(CATALOGUE, 2), SealedShelf.of(other, 2)));

        assertThat(both.items()).hasSize(4);
        assertThat(both.items().subList(0, 3)).allMatch(SealedShelf.Item::isBooster);
    }

    @Test
    @DisplayName("nothing to sell is an empty shelf, not a crash")
    void nothingIsNothing() {
        assertThat(SealedShelf.of((MtgjsonProducts.Reading) null, 2)).isEqualTo(SealedShelf.EMPTY);
        assertThat(SealedShelf.of(List.of())).isEqualTo(SealedShelf.EMPTY);
        assertThat(SealedShelf.of((List<SealedShelf>) null)).isEqualTo(SealedShelf.EMPTY);
        assertThat(SealedShelf.EMPTY.isEmpty()).isTrue();
    }

    /** What the shelf charges for the thing with this name on it. */
    private static int priceOf(SealedShelf shelf, String name) {
        for (SealedShelf.Item item : shelf.items()) {
            if (item.name().equals(name)) {
                return item.price();
            }
        }
        throw new AssertionError("Nothing called '" + name + "' is on the shelf");
    }

    private static SealedProduct booster(String id, String kind) {
        return new SealedProduct(id, "Test " + kind, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }
}
