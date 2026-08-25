package dev.gathering.core.sealed;

import java.util.Optional;

/**
 * A price, in the two piles a villager can be handed.
 *
 * <p>A trade takes at most two stacks and a stack is at most sixty-four, so a case worth four
 * hundred and thirty-two of anything cannot be paid for in one pile of it. Which is why there
 * are two denominations: the blocks go in the first slot and what is left over goes in the
 * second, the same way somebody counting out change would do it.
 *
 * <p>Exact, never rounded. A price that came to forty-eight blocks and three loose is what it
 * says; rounding it up to forty-nine blocks would be a shop quietly charging six more than the
 * number it worked out, and rounding down would be the reverse.
 *
 * <p>Pure.
 */
public record ShopPrice(int blocks, int loose) {

    /** As much of one thing as can be put in one slot of a trade. */
    public static final int MOST_IN_A_SLOT = 64;

    /** The dearest thing that can be paid for at all: both slots full. */
    public static int dearest(int perBlock) {
        int block = Math.max(1, perBlock);
        return MOST_IN_A_SLOT * block + Math.min(MOST_IN_A_SLOT, block - 1);
    }

    public ShopPrice {
        blocks = Math.max(0, blocks);
        loose = Math.max(0, loose);
    }

    /**
     * How to pay a price, or nothing where it is more than two stacks can carry.
     *
     * <p>Something too dear to pay for is not put on the shelf at all. A trade nobody can
     * afford because the arithmetic overflowed is worse than a shelf that is one row shorter.
     *
     * @param perBlock how many of the loose item one block is worth, as the server says
     */
    public static Optional<ShopPrice> of(int price, int perBlock) {
        if (price <= 0) {
            return Optional.empty();
        }
        if (price <= MOST_IN_A_SLOT) {
            // Small enough to hand over as itself. A booster costing two should cost two
            // emeralds, not a fraction of a block.
            return Optional.of(new ShopPrice(0, price));
        }
        int block = Math.max(1, perBlock);
        if (block <= 1) {
            // The server priced in something with no larger denomination. Anything past one
            // slot simply cannot be paid.
            return Optional.empty();
        }
        int blocks = price / block;
        int loose = price % block;
        if (blocks > MOST_IN_A_SLOT || loose > MOST_IN_A_SLOT) {
            return Optional.empty();
        }
        return Optional.of(new ShopPrice(blocks, loose));
    }


    /** What it comes to, back in the loose item. */
    public int total(int perBlock) {
        return blocks * Math.max(1, perBlock) + loose;
    }
}
