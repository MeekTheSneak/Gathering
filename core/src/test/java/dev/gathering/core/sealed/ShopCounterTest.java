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

    private static SealedProduct booster(String id, String name, String kind) {
        return new SealedProduct(id, name, "tst", "booster_pack", kind, 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", kind)),
                        List.of(), List.of(), List.of(), List.of()));
    }
}
