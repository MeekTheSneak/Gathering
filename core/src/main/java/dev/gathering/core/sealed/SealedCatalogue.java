package dev.gathering.core.sealed;

/**
 * Everything one set published, as far as anything selling it needs to know.
 *
 * <p>Sealed product describes itself by reference. A box names the pack it holds by that
 * pack's own id; a Commander deck names its hundred cards by the deck's name and leaves the
 * list further down the same file. So a product on its own is never enough to price or to
 * hand over, and this is the thing that turns those names back into what they meant.
 *
 * <p>Two lookups and no state. What reads the file is
 * {@link MtgjsonProducts} and {@link MtgjsonDecks}; what does the arithmetic is
 * {@link SealedPrice} and {@link SealedContents}. This is only the join between them, so that
 * neither of those has to know a file exists.
 *
 * <p>Pure.
 */
public interface SealedCatalogue {

    /** Nothing was published: every lookup misses. */
    SealedCatalogue EMPTY = new SealedCatalogue() {

        @Override
        public SealedProduct byId(String productId) {
            return null;
        }

        @Override
        public SealedDeck deck(String setCode, String name) {
            return null;
        }
    };

    /** The product with this published id, or null. */
    SealedProduct byId(String productId);

    /** The deck of this set with this name, or null. */
    SealedDeck deck(String setCode, String name);

    /** The catalogue for one set file, from what was read out of it. */
    static SealedCatalogue of(MtgjsonProducts.Reading products, MtgjsonDecks.Reading decks) {
        if (products == null && decks == null) {
            return EMPTY;
        }
        return new SealedCatalogue() {

            @Override
            public SealedProduct byId(String productId) {
                return products == null ? null : products.byId(productId);
            }

            @Override
            public SealedDeck deck(String setCode, String name) {
                return decks == null ? null : decks.named(setCode, name);
            }
        };
    }

    /** A catalogue of products alone, for a set whose decks were never read. */
    static SealedCatalogue of(MtgjsonProducts.Reading products) {
        return of(products, null);
    }

    /**
     * Several sets' catalogues, asked in turn.
     *
     * <p>What a shop selling more than one set looks things up in, and what makes a starter
     * kit whose decks belong to another set resolvable at all.
     */
    static SealedCatalogue of(java.util.List<SealedCatalogue> catalogues) {
        java.util.List<SealedCatalogue> all = catalogues == null
                ? java.util.List.of() : java.util.List.copyOf(catalogues);
        if (all.isEmpty()) {
            return EMPTY;
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        return new SealedCatalogue() {

            @Override
            public SealedProduct byId(String productId) {
                for (SealedCatalogue one : all) {
                    SealedProduct found = one.byId(productId);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }

            @Override
            public SealedDeck deck(String setCode, String name) {
                for (SealedCatalogue one : all) {
                    SealedDeck found = one.deck(setCode, name);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
        };
    }
}
