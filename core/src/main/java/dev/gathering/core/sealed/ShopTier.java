package dev.gathering.core.sealed;

import java.util.ArrayList;
import java.util.List;

/**
 * How far into a shopkeeper's stock a thing sits.
 *
 * <p>A villager gets better the more you trade with them, and what that should buy you here is
 * the bigger boxes. Packs from the first day; a display box once you are a regular; a case from
 * somebody who knows you. It gives the profession the same shape every other villager has, and
 * it means a new world does not open with a two-hundred-booster case on the counter.
 *
 * <p>By what a thing is worth in boosters rather than by what it costs, because what it costs
 * is a server setting and the shelf would otherwise reshuffle itself every time somebody edited
 * one number. A booster is a booster on every server.
 *
 * <p>Pure.
 */
public final class ShopTier {

    /** Novice to master, as vanilla counts a villager's levels. */
    public static final int LEVELS = 5;

    /**
     * The most a thing at each level can be worth, in boosters.
     *
     * <p>Chosen against what sets are really sold as: a pack is one, a prerelease kit or a
     * precon is around eight, a bundle or a collector box is a dozen, a display box is
     * thirty-six, and everything past that is a case.
     */
    private static final int[] AT_MOST = {1, 9, 19, 71};

    private ShopTier() {
    }

    /** Which level of shopkeeper stocks this, from one to {@link #LEVELS}. */
    public static int of(SealedProduct product, SealedCatalog catalog) {
        return ofBoosters(SealedPrice.inBoosters(product, catalog));
    }

    /** Which level stocks something worth this many boosters. */
    public static int ofBoosters(int boosters) {
        for (int level = 0; level < AT_MOST.length; level++) {
            if (boosters <= AT_MOST[level]) {
                return level + 1;
            }
        }
        return LEVELS;
    }

    /**
     * What one level of shopkeeper has, in shelf order.
     *
     * <p>Everything at that level exactly - not everything up to it. A master's counter is the
     * cases, and a player who wants a single booster buys it from any of the other four.
     */
    public static List<SealedShelf.Item> at(
            SealedShelf shelf, SealedCatalog catalog, int level) {
        List<SealedShelf.Item> stock = new ArrayList<>();
        if (shelf == null || level < 1 || level > LEVELS) {
            return List.of();
        }
        for (SealedShelf.Item item : shelf.items()) {
            if (of(item.product(), catalog) == level) {
                stock.add(item);
            }
        }
        return List.copyOf(stock);
    }
}
