package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A price, in the two piles a trade can hold")
class ShopPriceTest {

    private static final int PER_BLOCK = 9;

    @Test
    @DisplayName("something cheap is paid for in itself")
    void smallPricesStayLoose() {
        assertThat(ShopPrice.of(2, PER_BLOCK)).contains(new ShopPrice(0, 2));
        assertThat(ShopPrice.of(64, PER_BLOCK))
                .as("a full slot is still one slot")
                .contains(new ShopPrice(0, 64));
    }

    @Test
    @DisplayName("something dear is paid for in blocks and change")
    void bigPricesUseBlocks() {
        // Out of the real data at two a booster: a Bloomburrow play booster box case.
        assertThat(ShopPrice.of(432, PER_BLOCK)).contains(new ShopPrice(48, 0));
        // And a prerelease case, which does not divide.
        assertThat(ShopPrice.of(210, PER_BLOCK)).contains(new ShopPrice(23, 3));
    }

    @Test
    @DisplayName("nothing is rounded, in either direction")
    void nothingIsRounded() {
        for (int price = 1; price <= 600; price++) {
            ShopPrice paid = ShopPrice.of(price, PER_BLOCK).orElse(null);
            if (paid != null) {
                assertThat(paid.total(PER_BLOCK))
                        .as("a price of %d", price)
                        .isEqualTo(price);
            }
        }
    }

    @Test
    @DisplayName("what two slots cannot carry is not sold")
    void whatCannotBePaidIsNotSold() {
        assertThat(ShopPrice.of(ShopPrice.dearest(PER_BLOCK), PER_BLOCK)).isPresent();
        assertThat(ShopPrice.of(ShopPrice.dearest(PER_BLOCK) + 1, PER_BLOCK)).isEmpty();
        assertThat(ShopPrice.of(0, PER_BLOCK)).isEmpty();
        assertThat(ShopPrice.of(-5, PER_BLOCK)).isEmpty();
    }

    @Test
    @DisplayName("a server pricing in something with no bigger coin sells only what fits")
    void withoutABlockOnlyOneSlot() {
        assertThat(ShopPrice.of(64, 1)).contains(new ShopPrice(0, 64));
        assertThat(ShopPrice.of(65, 1)).isEmpty();
    }

    @Property
    @DisplayName("whatever comes back fits in two slots and comes to the price")
    void everyPriceThatSellsFitsAndAddsUp(
            @ForAll @IntRange(min = 1, max = 5000) int price,
            @ForAll @IntRange(min = 1, max = 64) int perBlock) {
        ShopPrice.of(price, perBlock).ifPresent(paid -> {
            assertThat(paid.blocks()).isBetween(0, ShopPrice.MOST_IN_A_SLOT);
            assertThat(paid.loose()).isBetween(0, ShopPrice.MOST_IN_A_SLOT);
            assertThat(paid.total(perBlock)).isEqualTo(price);
        });
    }
}
