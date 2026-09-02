package dev.gathering.core.scryfall;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One request for one card, in the shapes Scryfall's collection endpoint accepts.
 * <p>Decklist import produces these directly from parsed entries: a line with a set and
 * collector number becomes {@link #byPrinting}, a line with only a name becomes
 * {@link #byName}. Resolution keeps the query alongside its answer so an ambiguity can be
 * reported against the line the importer actually wrote.
 */
public sealed interface CardQuery {

    /** The JSON object Scryfall's {@code /cards/collection} endpoint expects for this query. */
    JsonObject toJson();

    /** A stable key for deduplicating queries within one import. */
    String key();

    static CardQuery byId(UUID id) {
        return new ById(Objects.requireNonNull(id, "id"));
    }

    static CardQuery byName(String name) {
        return new ByName(requireText(name, "name"));
    }

    /** A named card restricted to one set, which is what a line with a set but no number means. */
    static CardQuery byNameInSet(String name, String setCode) {
        return new ByNameInSet(requireText(name, "name"), requireText(setCode, "setCode").toLowerCase(Locale.ROOT));
    }

    static CardQuery byPrinting(String setCode, String collectorNumber) {
        return new ByPrinting(
                requireText(setCode, "setCode").toLowerCase(Locale.ROOT),
                requireText(collectorNumber, "collectorNumber"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /**
     * The name to actually put on the wire.
     * <p>Scryfall's collection endpoint does not accept a combined name. Asking it for
     * "Fire // Ice", "Wear // Tear", or "Delver of Secrets // Insectile Aberration" - all
     * exactly as every decklist exporter writes them - comes back not-found, while asking for
     * either half alone returns the whole card. So every split card, transform card and modal
     * double-faced card in a list would silently fail to import.
     * <p>Verified against the live API rather than inferred; see DIALECT.md.
     */
    static String lookupName(String name) {
        int split = name.indexOf("//");
        return split < 0 ? name : name.substring(0, split).strip();
    }

    record ById(UUID id) implements CardQuery {
        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            return json;
        }

        @Override
        public String key() {
            return "id:" + id;
        }
    }

    record ByName(String name) implements CardQuery {
        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", lookupName(name));
            return json;
        }

        @Override
        public String key() {
            return "name:" + name.toLowerCase(Locale.ROOT);
        }
    }

    record ByNameInSet(String name, String setCode) implements CardQuery {
        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", lookupName(name));
            json.addProperty("set", setCode);
            return json;
        }

        @Override
        public String key() {
            return "nameInSet:" + name.toLowerCase(Locale.ROOT) + "@" + setCode;
        }
    }

    record ByPrinting(String setCode, String collectorNumber) implements CardQuery {
        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("set", setCode);
            json.addProperty("collector_number", collectorNumber);
            return json;
        }

        @Override
        public String key() {
            return "printing:" + setCode + "/" + collectorNumber;
        }
    }
}
