package dev.gathering.core.sealed;

import java.util.Locale;

/**
 * What one sealed product is worth, in boosters.
 *
 * <p>Worked out from what is inside rather than typed in per product. A server owner sets one
 * number - what a booster costs - and everything else follows: a box of thirty costs thirty,
 * a bundle costs its packs plus what else is in it, a Commander deck costs what a hundred
 * cards would cost to open. A catalogue with a price per product would be a thousand numbers
 * nobody will ever finish filling in, and a thousand places for one of them to be wrong.
 *
 * <p><strong>No real-world price is used anywhere.</strong> Not to set this, not to weight it,
 * not to show beside it. What a card is worth on a server is discovered at its tables; a shop
 * that priced a Secret Lair off a website would be importing the supply history of a different
 * universe into somebody's Minecraft world.
 *
 * <p>Nor is there a discount for buying big. A box is one click instead of thirty and a promo
 * that comes with it; it is not a saving, because a shop that is cheaper per pack the more you
 * buy is a shop that rewards having diamonds rather than playing.
 *
 * <p>Pure.
 */
public final class SealedPrice {

    /**
     * How many cards a booster is taken to hold, for pricing something that has no packs in it.
     *
     * <p>A modern booster is fourteen or fifteen; this is what a Commander deck or a Secret
     * Lair is measured against, so it is one number rather than a lookup per product.
     */
    public static final int CARDS_IN_A_BOOSTER = 15;

    /**
     * How deep a product may hold other products before this stops counting.
     *
     * <p>A case holds boxes holds packs, which is three. Four is room to spare, and it exists
     * because the data is somebody else's: a product that holds itself would otherwise be a
     * server that never finishes starting.
     */
    public static final int MOST_NESTING = 4;

    /** Nothing is ever free. A product nothing can be said about still costs a booster. */
    public static final int CHEAPEST = 1;

    /** Product subtypes that are not a thing anybody can hold. */
    private static final java.util.List<String> NOT_ON_PAPER =
            java.util.List.of("mtgo", "arena", "digital", "redemption");

    private SealedPrice() {
    }

    /** What a product is worth, in the item a server prices in. */
    public static int of(SealedProduct product, Catalogue catalogue, int perBooster) {
        return Math.max(CHEAPEST, inBoosters(product, catalogue) * Math.max(1, perBooster));
    }

    /**
     * What a product is worth, in boosters.
     *
     * <p>The unit, kept apart from the money so it can be argued about without anybody having
     * to agree on what a diamond is worth.
     */
    public static int inBoosters(SealedProduct product, Catalogue catalogue) {
        return inBoosters(product, catalogue, MOST_NESTING);
    }

    /** Where the other products a box holds are looked up. */
    @FunctionalInterface
    public interface Catalogue {

        /** The product with this published id, or null. */
        SealedProduct byId(String productId);

        /** A catalogue with nothing in it, for a product that holds nothing. */
        Catalogue EMPTY = productId -> null;
    }

    private static int inBoosters(SealedProduct product, Catalogue catalogue, int depth) {
        if (product == null) {
            return CHEAPEST;
        }
        if (product.isOneBooster()) {
            return 1;
        }

        int worth = 0;

        // Boosters in the wrapper - a bundle's nine packs, a prerelease kit's six.
        worth += product.contents().boosters().size();

        // And boxes of them, by the id of the pack they hold rather than by copying it.
        if (depth > 0 && catalogue != null) {
            for (SealedProduct.Held held : product.contents().holds()) {
                SealedProduct inside = catalogue.byId(held.productId());
                worth += held.count() * (inside == null
                        // A box whose pack is not in this set's catalogue: it names another
                        // set's product, or the data is short. A booster each is the honest
                        // guess and it is never zero.
                        ? 1
                        : inBoosters(inside, catalogue, depth - 1));
            }
        }

        // Exact cards - a bundle's foil promo, a Secret Lair's four.
        worth += asBoosters(product.contents().cards().size());

        // And decks. The data names a deck rather than listing it, so the product's own card
        // count is what there is to go on - which is the right number anyway, because that is
        // what the deck is. Added to what else is in the box rather than instead of it: a
        // Commander deck that comes with a sample pack is a deck and a sample pack, and
        // pricing it as one of them was a hundred cards going for two boosters.
        if (!product.contents().decks().isEmpty()) {
            worth += asBoosters(product.cardCount());
        }

        // Nothing said about what is inside at all. Its card count is the last thing to go on,
        // and a product with none of that is a booster.
        if (worth == 0) {
            worth = asBoosters(product.cardCount());
        }
        return Math.max(CHEAPEST, worth);
    }

    /** How many boosters a loose pile of cards is worth, rounded up so nothing is free. */
    private static int asBoosters(int cards) {
        if (cards <= 0) {
            return 0;
        }
        return (cards + CARDS_IN_A_BOOSTER - 1) / CARDS_IN_A_BOOSTER;
    }

    /**
     * Whether a product is one this shop will ever sell.
     *
     * <p>Sealed only, which is every product in the published data: what the line actually
     * excludes is anything the mod would have to invent. A category nobody publishes is not
     * something to guess a price for.
     */
    public static boolean isSellable(SealedProduct product) {
        if (product == null || product.contents().isEmpty()) {
            return false;
        }
        // Nothing that only exists on a screen somewhere else. An MTGO redemption is a real
        // published product and it is a code, not a box: a shop that took diamonds for one
        // would be selling something nobody in the world can open.
        String subtype = product.subtype().toLowerCase(Locale.ROOT);
        for (String elsewhere : NOT_ON_PAPER) {
            if (subtype.contains(elsewhere)) {
                return false;
            }
        }
        String category = product.category().toLowerCase(Locale.ROOT);
        // Named rather than excluded: a category added later is not sold until somebody has
        // looked at it, which is the safe direction for a thing that takes people's diamonds.
        return category.contains("booster")
                || category.contains("bundle")
                || category.contains("deck")
                || category.contains("box")
                || category.contains("case")
                || category.contains("kit")
                || category.contains("pack");
    }
}
