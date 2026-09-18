package dev.gathering.core.scryfall;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The index every store needs, kept separately from any question of where bytes live.
 * <p>Three lookups, matching the three query shapes: by canonical id, by set and collector
 * number, and by name. The name index keeps the cheapest known printing, which is the
 * default an import picks when a line names a card and not a printing.
 */
public class InMemoryCardMetadataStore implements CardMetadataStore {

    /**
     * How many whole cards are held in memory at once, least recently asked for first out.
     * <p>The three indexes below are a name, a set-and-number and a price per card, which is tens of
     * bytes; a {@link CardMetadata} is a parsed card with its faces, its legalities and its lists,
     * which is orders of magnitude more. So the cards are bounded and the indexes are not: a lookup
     * for a card that has been let go of still knows which printing it wants and reads it back off
     * the disk beside this, which is what the disk-backed store does with a miss.
     * <p>Sixteen thousand is far more than a session plays with and far fewer than Magic has printed.
     */
    private static final int MOST_CARDS_IN_MEMORY = 16_384;

    /** Bounded and in access order: the least recently asked for card goes when the room runs out. */
    private final Map<UUID, CardMetadata> byId = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, CardMetadata> eldest) {
                    return size() > MOST_CARDS_IN_MEMORY;
                }
            });

    /** Which printing a name or a set and number means, and what it costs. Cheap, so unbounded. */
    private record Indexed(UUID id, double price) {
    }

    private final Map<String, UUID> byPrinting = new ConcurrentHashMap<>();
    private final Map<String, Indexed> byName = new ConcurrentHashMap<>();
    private final Map<String, Indexed> byNameInSet = new ConcurrentHashMap<>();

    /**
     * How many times anything has been stored, so a caller holding answers built from
     * {@link #inMemory} can tell whether they might now be different.
     * <p>A count rather than a timestamp, and bumped after the write rather than before: a
     * reader that notes the count and then reads the map can only ever be told "maybe stale"
     * about something it already saw, never "fresh" about something it missed.
     */
    private final java.util.concurrent.atomic.AtomicLong generation =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * What is already in memory for this printing, and never anything else.
     * <p>Deliberately not {@link #find}: the disk-backed subclass overrides that and will read
     * a file, which is why this class says nothing here may be called from a game thread.
     * This one touches a map and returns, so it is the one lookup a game thread may make - a
     * "do you happen to know" rather than a "go and find out".
     * <p>Never final in the sense that matters: an empty answer means not looked up yet, not
     * no such card. A caller on a game thread has to be able to live with that.
     */
    public final Optional<CardMetadata> inMemory(UUID scryfallId) {
        return scryfallId == null ? Optional.empty() : Optional.ofNullable(byId.get(scryfallId));
    }

    /** Which generation of what is known this store is on. See {@link #generation}. */
    public final long generation() {
        return generation.get();
    }

    /**
     * Which printing this query means, from the indexes alone, whether or not the card itself is
     * still held in memory.
     * <p>How a store with a disk under it turns a let-go-of card back into a file to read.
     */
    protected final Optional<UUID> idFor(CardQuery query) {
        return switch (query) {
            case CardQuery.ById byIdQuery -> Optional.ofNullable(byIdQuery.id());
            case CardQuery.ByPrinting printing ->
                    Optional.ofNullable(byPrinting.get(printingKey(printing.setCode(), printing.collectorNumber())));
            case CardQuery.ByName name ->
                    Optional.ofNullable(byName.get(nameKey(name.name()))).map(Indexed::id);
            case CardQuery.ByNameInSet nameInSet ->
                    Optional.ofNullable(byNameInSet.get(nameSetKey(nameInSet.name(), nameInSet.setCode())))
                            .map(Indexed::id);
        };
    }

    @Override
    public Optional<CardMetadata> find(CardQuery query) {
        return switch (query) {
            case CardQuery.ById byIdQuery -> Optional.ofNullable(byId.get(byIdQuery.id()));
            case CardQuery.ByPrinting printing ->
                    Optional.ofNullable(byPrinting.get(printingKey(printing.setCode(), printing.collectorNumber())))
                            .map(byId::get);
            case CardQuery.ByName name ->
                    Optional.ofNullable(byName.get(nameKey(name.name()))).map(Indexed::id).map(byId::get);
            // Its own index, not the name index filtered by set. The name index keeps the
            // one cheapest printing across every set, so whenever a cheaper printing from
            // some other set was stored, filtering it made this query a permanent miss -
            // and every "Name (SET)" decklist line went back to the network on every
            // import, found the same card, and missed again.
            case CardQuery.ByNameInSet nameInSet ->
                    Optional.ofNullable(byNameInSet.get(
                                    nameSetKey(nameInSet.name(), nameInSet.setCode())))
                            .map(Indexed::id).map(byId::get);
        };
    }

    @Override
    public void store(CardMetadata card, JsonObject rawJson) {
        if (card == null || card.scryfallId() == null) {
            return;
        }
        byId.put(card.scryfallId(), card);
        if (card.setCode() != null && card.collectorNumber() != null) {
            byPrinting.put(printingKey(card.setCode(), card.collectorNumber()), card.scryfallId());
        }
        indexName(card.name(), card);
        for (var face : card.faces()) {
            indexName(face.name(), card);
        }
        generation.incrementAndGet();
    }

    /**
     * Keeps the cheapest printing under a name. A decklist line that says only "Sol Ring"
     * should not silently resolve to the one that costs a house.
     */
    private void indexName(String name, CardMetadata card) {
        if (name == null || name.isBlank()) {
            return;
        }
        keepCheapest(byName, nameKey(name), card);
        if (card.setCode() != null) {
            keepCheapest(byNameInSet, nameSetKey(name, card.setCode()), card);
        }
    }

    private void keepCheapest(Map<String, Indexed> index, String key, CardMetadata card) {
        // The price is kept in the index rather than read back out of the card, because the card may
        // have been let go of - and comparing against a card that is no longer in memory used to take
        // whichever printing was stored last, which is not the cheapest and is not even the same
        // answer twice.
        Indexed candidate = new Indexed(card.scryfallId(), card.usdPrice().orElse(Double.MAX_VALUE));
        index.merge(key, candidate,
                (existing, offered) -> offered.price() < existing.price() ? offered : existing);
    }

    static String nameSetKey(String name, String setCode) {
        return nameKey(name) + "@" + setCode.toLowerCase(Locale.ROOT);
    }

    public int size() {
        return byId.size();
    }

    /** A snapshot of the cards still held in memory, for persistence. */
    protected Map<UUID, CardMetadata> snapshot() {
        synchronized (byId) {
            return new LinkedHashMap<>(byId);
        }
    }

    static String printingKey(String setCode, String collectorNumber) {
        return setCode.toLowerCase(Locale.ROOT) + "/" + collectorNumber.toLowerCase(Locale.ROOT);
    }

    static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT).strip();
    }
}
