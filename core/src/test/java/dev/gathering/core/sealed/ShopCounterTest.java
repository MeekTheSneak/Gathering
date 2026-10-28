package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The shelf every card shop stocks")
class ShopCounterTest {

    private static final SealedProduct PLAY = booster("play-pack", "Play Booster Pack", "play");
    private static final SealedProduct COLLECTOR =
            booster("coll-pack", "Collector Booster Pack", "collector");
    private static final SealedProduct SAMPLE =
            booster("coll-sample", "Collector Booster Sample Pack", "collector");
    private static final SealedProduct VALUE = booster("value", "Value Booster", "value");

    private static final SealedCatalog CATALOG =
            Catalogs.of(PLAY, COLLECTOR, SAMPLE, VALUE);

    /** Shelf order for boosters all at the same price is by name, which is the hard case. */
    private static final SealedShelf SHELF = new SealedShelf(List.of(
            new SealedShelf.Item(COLLECTOR, 2),
            new SealedShelf.Item(SAMPLE, 2),
            new SealedShelf.Item(PLAY, 2),
            new SealedShelf.Item(VALUE, 2)));

    @Test
    @DisplayName("two shopkeepers of the same level stock exactly the same things")
    void everyCounterIsTheSame() {
        // The point of the whole class. A shelf that varied per villager would be a shelf you
        // could break the counter and re-place until it offered what you wanted.
        List<SealedShelf.Item> once = ShopCounter.at(SHELF, CATALOG, 1);
        for (int again = 0; again < 20; again++) {
            assertThat(ShopCounter.at(SHELF, CATALOG, 1)).isEqualTo(once);
        }
    }

    @Test
    @DisplayName("the ordinary booster is there before the collector one")
    void theOrdinaryPackComesFirst() {
        // By name alone the shelf leads with two collector packs, which would waste a counter.
        assertThat(ShopCounter.at(SHELF, CATALOG, 1))
                .extracting(SealedShelf.Item::name)
                .containsExactly("Play Booster Pack", "Collector Booster Pack");
    }

    @Test
    @DisplayName("no counter holds the same sort of thing twice while something else waits")
    void varietyBeforeOrder() {
        assertThat(ShopCounter.pick(List.of(
                new SealedShelf.Item(SAMPLE, 2),
                new SealedShelf.Item(COLLECTOR, 2),
                new SealedShelf.Item(VALUE, 2)), 2))
                .extracting(SealedShelf.Item::name)
                .containsExactly("Collector Booster Sample Pack", "Value Booster");
    }

    @Test
    @DisplayName("a level with only one kind of thing still fills what it can")
    void oneKindStillFills() {
        List<SealedShelf.Item> onlyCollectors = List.of(
                new SealedShelf.Item(COLLECTOR, 2), new SealedShelf.Item(SAMPLE, 2));

        assertThat(ShopCounter.pick(onlyCollectors, 2)).hasSize(2);
        assertThat(ShopCounter.pick(List.of(new SealedShelf.Item(PLAY, 2)), 2)).hasSize(1);
    }

    @Test
    @DisplayName("nothing on the shelf is nothing on the counter")
    void anEmptyShelfIsAnEmptyCounter() {
        assertThat(ShopCounter.at(SealedShelf.EMPTY, CATALOG, 1)).isEmpty();
        assertThat(ShopCounter.pick(null, 2)).isEmpty();
        assertThat(ShopCounter.pick(List.of(new SealedShelf.Item(PLAY, 2)), 0)).isEmpty();
    }

    @Test
    @DisplayName("nothing on a shelf is priced past what one trade can carry")
    void theDearestThingIsStillBuyable() {
        // The owner's decision: a case is worth over two hundred boosters and a trade carries at
        // most two slots of sixty-four, so without a ceiling the top of the shop - what a master
        // shopkeeper sells - could not be bought at any amount of play.
        SealedProduct crate = holding("case", "Case", PLAY, 216);
        SealedCatalog catalog = Catalogs.of(PLAY, crate);
        int dearest = ShopPrice.dearest(1);

        SealedShelf shelf = SealedShelf.of(
                new MtgjsonProducts.Reading("tst", List.of(PLAY, crate), List.of()), catalog, 1, dearest);

        assertThat(shelf.items()).isNotEmpty();
        for (SealedShelf.Item item : shelf.items()) {
            assertThat(item.price())
                    .as("%s must be payable in one trade", item.name())
                    .isLessThanOrEqualTo(dearest);
            assertThat(ShopPrice.of(item.price(), 1))
                    .as("%s must have a price somebody can hand over", item.name())
                    .isPresent();
        }
        assertThat(dearest).isEqualTo(128);
    }

    @Test
    @DisplayName("a counter stocks only what this server's prices can be paid in")
    void thingsTooDearToHandOverAreNotStocked() {
        // A case worth 216 boosters, priced in a currency with no larger denomination: 216 of it
        // cannot be handed over in a trade's two slots, and the offer was simply dropped - so a
        // shopkeeper trained all the way to master, which is the reward of the whole profession,
        // had an empty counter. The display box below it can be paid for.
        SealedProduct box = holding("box", "Display Box", PLAY, 36);
        SealedProduct crate = holding("case", "Case", PLAY, 216);
        SealedCatalog catalog = Catalogs.of(PLAY, box, crate);
        SealedShelf shelf = new SealedShelf(List.of(
                new SealedShelf.Item(crate, 216), new SealedShelf.Item(box, 36)));

        assertThat(ShopTier.of(crate, catalog)).isEqualTo(ShopTier.LEVELS);
        assertThat(ShopCounter.at(shelf, catalog, ShopTier.LEVELS, 0, 1))
                .extracting(SealedShelf.Item::name)
                .containsExactly("Display Box");
        // And with a currency that can carry it, the case is what a master sells.
        assertThat(ShopCounter.at(shelf, catalog, ShopTier.LEVELS, 0, 9))
                .extracting(SealedShelf.Item::name)
                .containsExactly("Case");
    }

    /** A box holding this many of one booster, which is how a shelf's big product is built. */
    private static SealedProduct holding(String id, String name, SealedProduct pack, int howMany) {
        return new SealedProduct(id, name, "tst", "box", "", 15 * howMany,
                new SealedProduct.Contents(
                        List.of(), List.of(new SealedProduct.Held(pack.productId(), pack.name(), howMany)),
                        List.of(), List.of(), List.of(), 0));
    }

    private static SealedProduct booster(String id, String name, String kind) {
        return new SealedProduct(id, name, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }
}
