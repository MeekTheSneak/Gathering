package dev.gathering.core.sealed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A catalog built from a handful of products and decks, for the tests that need one. */
final class Catalogs {

    private Catalogs() {
    }

    static SealedCatalog of(SealedProduct... products) {
        return of(List.of(), products);
    }

    static SealedCatalog of(List<SealedDeck> decks, SealedProduct... products) {
        Map<String, SealedProduct> byId = new LinkedHashMap<>();
        for (SealedProduct product : products) {
            byId.put(product.productId(), product);
        }
        return new SealedCatalog() {

            @Override
            public SealedProduct byId(String productId) {
                return byId.get(productId);
            }

            @Override
            public SealedDeck deck(String setCode, String name) {
                for (SealedDeck deck : decks) {
                    if (deck.is(setCode, name)) {
                        return deck;
                    }
                }
                return null;
            }
        };
    }
}
