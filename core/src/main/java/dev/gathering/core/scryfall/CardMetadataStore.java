package dev.gathering.core.scryfall;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server's card metadata cache, expressed as the two questions the pipeline asks it:
 * "do you already have this?" and "here, keep this".
 *
 * <p>Raw Scryfall JSON goes in rather than parsed metadata, so a field the mod starts using
 * later is already on disk and no server has to refetch a hundred thousand cards to get it.
 */
public interface CardMetadataStore {

    Optional<CardMetadata> find(CardQuery query);

    void store(CardMetadata card, JsonObject raw);

    /** Persists anything held only in memory. A no-op for stores that are only memory. */
    default void flush() throws IOException {
        // Nothing to do by default.
    }

    /**
     * Splits queries into what the cache can answer and what has to go to the network. This
     * is the whole point of the cache: a re-import of a known decklist costs zero requests.
     */
    default Partition partition(List<CardQuery> queries) {
        List<CardQuery> misses = new ArrayList<>();
        List<CardMetadata> hits = new ArrayList<>();
        List<CardQuery> hitQueries = new ArrayList<>();
        for (CardQuery query : queries) {
            Optional<CardMetadata> cached = find(query);
            if (cached.isPresent()) {
                hits.add(cached.get());
                hitQueries.add(query);
            } else {
                misses.add(query);
            }
        }
        return new Partition(hitQueries, hits, misses);
    }

    record Partition(List<CardQuery> hitQueries, List<CardMetadata> hits, List<CardQuery> misses) {

        public Partition {
            hitQueries = List.copyOf(hitQueries);
            hits = List.copyOf(hits);
            misses = List.copyOf(misses);
        }

        public boolean fullyCached() {
            return misses.isEmpty();
        }
    }
}
