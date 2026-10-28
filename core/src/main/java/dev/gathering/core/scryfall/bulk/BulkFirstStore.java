package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.scryfall.CardMetadataStore;
import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.DiskCardMetadataStore;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The card cache, asked after the local copy of Scryfall's bulk file when there is one.
 * <p>What sits in front of the network for every lookup that goes through the caching source:
 * an import, a card by id, a card by name. A card the bulk copy answers is kept in memory the way a
 * fetched card is, so the game thread can still {@code peek} it - and is not written out as a file
 * of its own, because it is already on this disk.
 * <p>An id already in memory is answered from memory first, which is the same card and costs a
 * map lookup. Anything the copy does not have - a card printed since it was built, or every card
 * while it is still being built - goes to the cache as it always did, and past that to Scryfall.
 * <p>Blocking file I/O, like the store it wraps.
 */
public final class BulkFirstStore implements CardMetadataStore {

    private final DiskCardMetadataStore disk;
    private final Supplier<Optional<BulkCardIndex>> index;

    /**
     * @param index the copy to ask, or empty while there is none ready; asked on every lookup, so
     *              a copy that becomes ready or is replaced is used from then on
     */
    public BulkFirstStore(DiskCardMetadataStore disk, Supplier<Optional<BulkCardIndex>> index) {
        this.disk = Objects.requireNonNull(disk, "disk");
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public Optional<CardMetadata> find(CardQuery query) {
        if (query instanceof CardQuery.ById byId) {
            Optional<CardMetadata> known = disk.inMemory(byId.id());
            if (known.isPresent()) {
                return known;
            }
        }
        Optional<BulkCardIndex> ready = index.get();
        if (ready.isPresent()) {
            try {
                Optional<CardMetadata> found = ready.get().find(query);
                if (found.isPresent()) {
                    disk.remember(found.get(), ready.get().asOf());
                    return found;
                }
            } catch (IOException unreadable) {
                // A copy that cannot be read is a copy that does not have the card. The cache and
                // the network still do, and a lookup is not the place to say the copy is broken.
            }
        }
        return disk.find(query);
    }

    /** Keeps cards the bulk copy answered in memory, as {@link #find} does for one. */
    public void remember(List<CardMetadata> cards, BulkCardIndex from) {
        for (CardMetadata card : cards) {
            disk.remember(card, from.asOf());
        }
    }

    @Override
    public void store(CardMetadata card, JsonObject raw) {
        disk.store(card, raw);
    }

    @Override
    public void flush() throws IOException {
        disk.flush();
    }
}
