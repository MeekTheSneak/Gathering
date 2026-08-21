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
 *
 * <p>Three lookups, matching the three query shapes: by canonical id, by set and collector
 * number, and by name. The name index keeps the cheapest known printing, which is the
 * default an import picks when a line names a card and not a printing.
 */
public class InMemoryCardMetadataStore implements CardMetadataStore {

    private final Map<UUID, CardMetadata> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byPrinting = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Map<UUID, JsonObject> raw = new ConcurrentHashMap<>();

    @Override
    public Optional<CardMetadata> find(CardQuery query) {
        return switch (query) {
            case CardQuery.ById byIdQuery -> Optional.ofNullable(byId.get(byIdQuery.id()));
            case CardQuery.ByPrinting printing ->
                    Optional.ofNullable(byPrinting.get(printingKey(printing.setCode(), printing.collectorNumber())))
                            .map(byId::get);
            case CardQuery.ByName name ->
                    Optional.ofNullable(byName.get(nameKey(name.name()))).map(byId::get);
            case CardQuery.ByNameInSet nameInSet ->
                    Optional.ofNullable(byName.get(nameKey(nameInSet.name())))
                            .map(byId::get)
                            .filter(card -> nameInSet.setCode().equalsIgnoreCase(card.setCode()));
        };
    }

    @Override
    public void store(CardMetadata card, JsonObject rawJson) {
        if (card == null || card.scryfallId() == null) {
            return;
        }
        byId.put(card.scryfallId(), card);
        if (rawJson != null) {
            raw.put(card.scryfallId(), rawJson);
        }
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
        String key = nameKey(name);
        byName.merge(key, card.scryfallId(), (existingId, candidateId) -> {
            CardMetadata existing = byId.get(existingId);
            if (existing == null) {
                return candidateId;
            }
            double existingPrice = existing.usdPrice().orElse(Double.MAX_VALUE);
            double candidatePrice = card.usdPrice().orElse(Double.MAX_VALUE);
            return candidatePrice < existingPrice ? candidateId : existingId;
        });
    }

    public Optional<JsonObject> rawJson(UUID id) {
        return Optional.ofNullable(raw.get(id));
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
