package dev.gathering.core.sealed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What is on a shop's shelf, and what each thing costs.
 * <p>Sealed only, ever. The line is the one paper Magic proved: the manufacturer sells sealed
 * and singles come from people. An infinite-stock shop selling single cards is the classic way
 * to kill a player economy, and there is no version of this where that is worth trying.
 * <p>Everything the set was really sold as, in the order somebody would want to see it: the
 * packs first and cheapest first, because a booster is what most people came in for and a
 * shelf that opens with a two-hundred-booster case is a shelf nobody reads to the end of.
 * <p>Pure. What a server actually stocks is which sets it points this at.
 */
public record SealedShelf(List<Item> items) {

    /** One thing on the shelf, at what it costs. */
    public record Item(SealedProduct product, int price) {

        /** Whether this is a single booster, which is what the shelf leads with. */
        public boolean isBooster() {
            return product != null && product.isOneBooster();
        }

        public String name() {
            return product == null ? "" : product.name();
        }
    }

    public SealedShelf {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static final SealedShelf EMPTY = new SealedShelf(List.of());

    public boolean isEmpty() {
        return items.isEmpty();
    }


    /**
     * The shelf for one set's catalog.
     * <p>Ordered rather than left as the file wrote it: packs first, then everything else by
     * price. Within each, by price and then by name, so two shelves built from the same file
     * are the same shelf - a shop whose rows moved between two visits is a shop nobody can
     * learn.
     *
     * @param perBooster what this server charges for one booster
     */
    public static SealedShelf of(MtgjsonProducts.Reading products, int perBooster) {
        return of(products, SealedCatalog.of(products), perBooster);
    }

    /**
     * The shelf for one set, looked up in a catalog that may span several.
     * <p>Which is what a precon needs: the box is published in the Commander set and the deck
     * it names is published there too, but a starter kit names decks belonging to the set it
     * came out beside.
     */
    public static SealedShelf of(MtgjsonProducts.Reading products, SealedCatalog catalog,
            int perBooster) {
        if (products == null) {
            return EMPTY;
        }
        SealedCatalog lookup = catalog == null
                ? SealedCatalog.of(products) : catalog;
        List<Item> items = new ArrayList<>();
        for (SealedProduct product : products.products()) {
            // Sellable and giveable, and the second is the real question. Anything the mod
            // could not actually put in somebody's hands is not on the shelf at all, whatever
            // the data calls it.
            if (SealedPrice.isSellable(product)
                    && SealedContents.canBeHandedOver(product, lookup)) {
                items.add(new Item(product, SealedPrice.of(product, lookup, perBooster)));
            }
        }
        items.sort(Comparator
                .comparing((Item item) -> item.isBooster() ? 0 : 1)
                .thenComparingInt(Item::price)
                .thenComparing(Item::name));
        return new SealedShelf(List.copyOf(items));
    }

    /** Several sets' shelves, run together into one. */
    public static SealedShelf of(List<SealedShelf> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return EMPTY;
        }
        List<Item> items = new ArrayList<>();
        for (SealedShelf shelf : shelves) {
            items.addAll(shelf.items());
        }
        items.sort(Comparator
                .comparing((Item item) -> item.isBooster() ? 0 : 1)
                .thenComparingInt(Item::price)
                .thenComparing(Item::name));
        return new SealedShelf(List.copyOf(items));
    }
}
