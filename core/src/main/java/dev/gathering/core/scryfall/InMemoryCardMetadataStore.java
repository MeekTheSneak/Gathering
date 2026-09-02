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

    private final Map<UUID, CardMetadata> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byPrinting = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Map<String, UUID> byNameInSet = new ConcurrentHashMap<>();

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

    @Override
    public Optional<CardMetadata> find(CardQuery query) {
        return switch (query) {
            case CardQuery.ById byIdQuery -> Optional.ofNullable(byId.get(byIdQuery.id()));
            case CardQuery.ByPrinting printing ->
                    Optional.ofNullable(byPrinting.get(printingKey(printing.setCode(), printing.collectorNumber())))
                            .map(byId::get);
            case CardQuery.ByName name ->
                    Optional.ofNullable(byName.get(nameKey(name.name()))).map(byId::get);
            // Its own index, not the name index filtered by set. The name index keeps the
            // one cheapest printing across every set, so whenever a cheaper printing from
            // some other set was stored, filtering it made this query a permanent miss -
            // and every "Name (SET)" decklist line went back to the network on every
            // import, found the same card, and missed again.
            case CardQuery.ByNameInSet nameInSet ->
                    Optional.ofNullable(byNameInSet.get(
                                    nameSetKey(nameInSet.name(), nameInSet.setCode())))
                            .map(byId::get);
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

    private void keepCheapest(Map<String, UUID> index, String key, CardMetadata card) {
        index.merge(key, card.scryfallId(), (existingId, candidateId) -> {
            CardMetadata existing = byId.get(existingId);
            if (existing == null) {
                return candidateId;
            }
            double existingPrice = existing.usdPrice().orElse(Double.MAX_VALUE);
            double candidatePrice = card.usdPrice().orElse(Double.MAX_VALUE);
            return candidatePrice < existingPrice ? candidateId : existingId;
        });
    }

    static String nameSetKey(String name, String setCode) {
        return nameKey(name) + "@" + setCode.toLowerCase(Locale.ROOT);
    }

    public int size() {
        return byId.size();
    }

    /** A snapshot of everything indexed, in insertion order, for persistence. */
    protected Map<UUID, CardMetadata> snapshot() {
        return new LinkedHashMap<>(byId);
    }

    static String printingKey(String setCode, String collectorNumber) {
        return setCode.toLowerCase(Locale.ROOT) + "/" + collectorNumber.toLowerCase(Locale.ROOT);
    }

    static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT).strip();
    }
}
