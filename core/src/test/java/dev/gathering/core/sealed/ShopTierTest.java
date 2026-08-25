package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("How far into a shopkeeper's stock a thing sits")
class ShopTierTest {

    @Test
    @DisplayName("a booster is on the counter from the first day")
    void aBoosterIsNovice() {
        assertThat(ShopTier.ofBoosters(1)).isEqualTo(1);
    }

    @Test
    @DisplayName("the bigger the box, the longer you have to have been coming in")
    void biggerBoxesAreFurtherUp() {
        // What the real Bloomburrow products are worth, in boosters.
        assertThat(ShopTier.ofBoosters(7)).as("prerelease pack").isEqualTo(2);
        assertThat(ShopTier.ofBoosters(8)).as("commander precon").isEqualTo(2);
        assertThat(ShopTier.ofBoosters(12)).as("bundle, collector box").isEqualTo(3);
        assertThat(ShopTier.ofBoosters(36)).as("play booster box").isEqualTo(4);
        assertThat(ShopTier.ofBoosters(216)).as("play booster box case").isEqualTo(5);
    }

    @Test
    @DisplayName("nothing sits past master, however big somebody's data says it is")
    void nothingIsPastTheTop() {
        assertThat(ShopTier.ofBoosters(100_000)).isEqualTo(ShopTier.LEVELS);
    }

    @Test
    @DisplayName("a level stocks what belongs to it and nothing else")
    void eachLevelIsItsOwnStock() {
        SealedProduct pack = booster("pack", "Pack");
        SealedProduct box = holding("box", "Box", "pack", 36);
        SealedShelf shelf = new SealedShelf(List.of(
                new SealedShelf.Item(pack, 2), new SealedShelf.Item(box, 72)));
        SealedCatalogue catalogue = Catalogues.of(pack, box);

        assertThat(ShopTier.at(shelf, catalogue, 1))
                .extracting(SealedShelf.Item::name).containsExactly("Pack");
        assertThat(ShopTier.at(shelf, catalogue, 4))
                .extracting(SealedShelf.Item::name).containsExactly("Box");
        assertThat(ShopTier.at(shelf, catalogue, 3))
                .as("a level with nothing at it has nothing at it").isEmpty();
        assertThat(ShopTier.at(shelf, catalogue, 0)).isEmpty();
        assertThat(ShopTier.at(shelf, catalogue, ShopTier.LEVELS + 1)).isEmpty();
    }

    private static SealedProduct booster(String id, String name) {
        return new SealedProduct(id, name, "tst", "booster_pack", "play", 15,
                new SealedProduct.Contents(
                        List.of(new SealedProduct.Booster("tst", "play")),
                        List.of(), List.of(), List.of(), List.of()));
    }

    private static SealedProduct holding(String id, String name, String inside, int count) {
        return new SealedProduct(id, name, "tst", "booster_box", "play", 0,
                new SealedProduct.Contents(
                        List.of(), List.of(new SealedProduct.Held(inside, inside, count)),
                        List.of(), List.of(), List.of()));
    }
}
