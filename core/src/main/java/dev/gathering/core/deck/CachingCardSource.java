package dev.gathering.core.deck;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.CardMetadataStore;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.CollectionResult;
import dev.gathering.core.scryfall.ScryfallClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cache in front of the network, which is the whole reason a re-import of a known
 * decklist makes no requests at all.
 * <p>Anything the store already holds is answered from disk; only the remainder is batched
 * to Scryfall, and everything that comes back is stored before it is returned.
 */
public final class CachingCardSource implements CardSource {

    private final CardMetadataStore store;
    private final ScryfallClient client;

    public CachingCardSource(CardMetadataStore store, ScryfallClient client) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.client = java.util.Objects.requireNonNull(client, "client");
    }

    @Override
    public CollectionResult resolve(List<CardQuery> queries) throws IOException {
        if (queries == null || queries.isEmpty()) {
            return CollectionResult.empty();
        }

        Map<String, CardMetadata> found = new LinkedHashMap<>();
        List<CardQuery> misses = new ArrayList<>();

        for (CardQuery query : queries) {
            var cached = store.find(query);
            if (cached.isPresent()) {
                found.put(query.key(), cached.get());
            } else {
                misses.add(query);
            }
        }

        if (misses.isEmpty()) {
            return new CollectionResult(found, List.of());
        }

        CollectionResult fetched = client.resolve(misses);
        for (CardMetadata card : fetched.found().values()) {
            store.store(card, fetched.rawFor(card).orElse(null));
        }
        store.flush();

        found.putAll(fetched.found());
        return new CollectionResult(found, fetched.notFound(), fetched.raw());
    }
}
