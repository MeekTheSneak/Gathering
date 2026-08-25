package dev.gathering.core.scryfall;

import com.google.gson.JsonObject;
import dev.gathering.core.card.CardMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a batch resolution came back with: the cards that matched, keyed by the query that
 * asked for them, and the queries nothing matched.
 *
 * <p>The unmatched list is not an error. It is exactly the list the import screen shows the
 * player, line by line, so a typo is a fixable thing rather than a failed import.
 *
 * @param raw each card's original Scryfall JSON, kept so the disk cache stores what Scryfall
 *            actually said rather than a re-serialisation of the fields the codec reads today
 */
public record CollectionResult(
        Map<String, CardMetadata> found, List<CardQuery> notFound, Map<UUID, JsonObject> raw) {

    public CollectionResult {
        // In the order the queries were asked, because callers take values() as a list and
        // hand it on - and a list whose order is a hash salted once per launch is a list that
        // comes back differently on every start for the same request.
        found = found == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(found));
        notFound = notFound == null ? List.of() : List.copyOf(notFound);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public CollectionResult(Map<String, CardMetadata> found, List<CardQuery> notFound) {
        this(found, notFound, Map.of());
    }

    public static CollectionResult empty() {
        return new CollectionResult(Map.of(), List.of(), Map.of());
    }

    public Optional<CardMetadata> get(CardQuery query) {
        return Optional.ofNullable(found.get(query.key()));
    }

    public Optional<JsonObject> rawFor(CardMetadata card) {
        return card == null ? Optional.empty() : Optional.ofNullable(raw.get(card.scryfallId()));
    }

    public boolean isComplete() {
        return notFound.isEmpty();
    }

    /** Merges two batches, which is how a request spanning several pages becomes one answer. */
    public CollectionResult merge(CollectionResult other) {
        Map<String, CardMetadata> mergedFound = new LinkedHashMap<>(found);
        mergedFound.putAll(other.found);
        List<CardQuery> mergedMissing = new ArrayList<>(notFound);
        mergedMissing.addAll(other.notFound);
        Map<UUID, JsonObject> mergedRaw = new LinkedHashMap<>(raw);
        mergedRaw.putAll(other.raw);
        return new CollectionResult(mergedFound, mergedMissing, mergedRaw);
    }
}
