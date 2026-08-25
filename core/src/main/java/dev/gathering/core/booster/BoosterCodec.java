package dev.gathering.core.booster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes booster collation as JSON a server can author by hand.
 *
 * <p>The interpreter consumes data and nothing else, which is what makes it work for every
 * set past and future - but data has to arrive from somewhere, and this is the plainest
 * somewhere there is: a file. It is the fallback when no published collation exists for a
 * set, and it is a feature on its own, because a server that wants to curate its own product
 * out of real printings can write one down.
 *
 * <p>The shape is the interpreter's own concepts and nothing else - sheets of weighted
 * printings, arrangements of weighted slots. It is deliberately not a claim to match any
 * particular published schema: an adapter that maps one onto this belongs with that feed,
 * where it can be checked against the real thing rather than against a memory of it.
 *
 * <pre>
 * {
 *   "set": "abc",
 *   "kind": "draft",
 *   "sheets": {
 *     "common":  { "foil": false, "duplicates": false,
 *                  "cards": { "&lt;scryfall-uuid&gt;": 4, "&lt;scryfall-uuid&gt;": 1 } },
 *     "rare":    { "cards": { "&lt;scryfall-uuid&gt;": 1 } }
 *   },
 *   "variants": [
 *     { "name": "plain",    "weight": 7, "slots": { "common": 10, "rare": 1 } },
 *     { "name": "upgraded", "weight": 1, "slots": { "common": 10, "mythic": 1 } }
 *   ]
 * }
 * </pre>
 *
 * <p>Strict and loud. A file with a typo in a sheet name, a weight that is not a number, or a
 * card id that is not a UUID says so and says where, because the alternative - quietly
 * dropping what it could not read - is a server whose packs are subtly wrong and whose admin
 * has no way to find out.
 *
 * <p>Pure.
 */
public final class BoosterCodec {

    private BoosterCodec() {
    }

    public static BoosterConfig read(JsonObject json) throws BoosterCodecException {
        if (json == null) {
            throw new BoosterCodecException("There is no booster here at all");
        }
        String set = string(json, "set");
        String kind = json.has("kind") ? string(json, "kind") : "default";

        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        JsonObject sheetsJson = object(json, "sheets");
        for (Map.Entry<String, JsonElement> entry : sheetsJson.entrySet()) {
            sheets.put(entry.getKey(), sheet(entry.getKey(), entry.getValue()));
        }

        List<BoosterVariant> variants = new ArrayList<>();
        JsonArray variantsJson = array(json, "variants");
        for (int index = 0; index < variantsJson.size(); index++) {
            variants.add(variant(index, variantsJson.get(index)));
        }

        BoosterConfig config = new BoosterConfig(set, kind, sheets, variants);
        // Checked here rather than left for whoever opens a pack. An admin who mistyped a
        // sheet name in a slot finds out when they save the file, not when a player opens a
        // booster and gets something wrong in a way nobody can see.
        List<String> missing = config.whatIsMissing();
        if (!missing.isEmpty()) {
            throw new BoosterCodecException(
                    config.id() + ": variants ask for sheets that are not here: " + missing);
        }
        if (config.variants().isEmpty()) {
            throw new BoosterCodecException(config.id() + ": no variant can ever be opened");
        }
        return config;
    }

    public static JsonObject write(BoosterConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("set", config.setCode());
        json.addProperty("kind", config.kind());

        JsonObject sheets = new JsonObject();
        config.sheets().forEach((name, sheet) -> {
            JsonObject written = new JsonObject();
            written.addProperty("foil", sheet.foil());
            written.addProperty("duplicates", sheet.duplicates());
            JsonObject cards = new JsonObject();
            sheet.weights().forEach((printing, weight) ->
                    cards.addProperty(printing.toString(), weight));
            written.add("cards", cards);
            sheets.add(name, written);
        });
        json.add("sheets", sheets);

        JsonArray variants = new JsonArray();
        for (BoosterVariant variant : config.variants()) {
            JsonObject written = new JsonObject();
            written.addProperty("name", variant.name());
            written.addProperty("weight", variant.weight());
            JsonObject slots = new JsonObject();
            variant.slots().forEach(slots::addProperty);
            written.add("slots", slots);
            variants.add(written);
        }
        json.add("variants", variants);
        return json;
    }

    // ------------------------------------------------------------------ sheets

    private static BoosterSheet sheet(String name, JsonElement element) throws BoosterCodecException {
        if (element == null || !element.isJsonObject()) {
            throw new BoosterCodecException("sheet '" + name + "': not an object");
        }
        JsonObject json = element.getAsJsonObject();
        boolean foil = flag(json, "foil", name);
        boolean duplicates = flag(json, "duplicates", name);

        Map<UUID, Integer> weights = new LinkedHashMap<>();
        JsonObject cards = object(json, "cards", "sheet '" + name + "'");
        for (Map.Entry<String, JsonElement> card : cards.entrySet()) {
            UUID printing = printing(card.getKey(), name);
            weights.put(printing, weight(card.getValue(), "sheet '" + name + "', card "
                    + card.getKey()));
        }
        if (weights.isEmpty()) {
            throw new BoosterCodecException("sheet '" + name + "': no cards on it");
        }
        return new BoosterSheet(name, foil, duplicates, weights);
    }

    // ---------------------------------------------------------------- variants

    private static BoosterVariant variant(int index, JsonElement element)
            throws BoosterCodecException {
        String where = "variant " + index;
        if (element == null || !element.isJsonObject()) {
            throw new BoosterCodecException(where + ": not an object");
        }
        JsonObject json = element.getAsJsonObject();
        String name = json.has("name") ? string(json, "name") : String.valueOf(index);
        where = "variant '" + name + "'";
        int weight = weight(json.get("weight"), where);

        Map<String, Integer> slots = new LinkedHashMap<>();
        JsonObject slotsJson = object(json, "slots", where);
        for (Map.Entry<String, JsonElement> slot : slotsJson.entrySet()) {
            slots.put(slot.getKey(),
                    weight(slot.getValue(), where + ", slot '" + slot.getKey() + "'"));
        }
        if (slots.isEmpty()) {
            throw new BoosterCodecException(where + ": a pack of no cards");
        }
        return new BoosterVariant(name, weight, slots);
    }

    // ------------------------------------------------------------------- bits

    private static String string(JsonObject json, String field) throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || element.getAsString().isBlank()) {
            throw new BoosterCodecException("'" + field + "' is missing or blank");
        }
        return element.getAsString();
    }

    private static JsonObject object(JsonObject json, String field) throws BoosterCodecException {
        return object(json, field, null);
    }

    private static JsonObject object(JsonObject json, String field, String where)
            throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonObject()) {
            throw new BoosterCodecException(
                    (where == null ? "" : where + ": ") + "'" + field + "' is missing or not an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String field) throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new BoosterCodecException("'" + field + "' is missing or not a list");
        }
        return element.getAsJsonArray();
    }

    /** Absent means false, which is what leaves a plain sheet needing neither line. */
    private static boolean flag(JsonObject json, String field, String where)
            throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new BoosterCodecException(
                    "sheet '" + where + "': '" + field + "' is not true or false");
        }
        return element.getAsBoolean();
    }

    private static int weight(JsonElement element, String where) throws BoosterCodecException {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new BoosterCodecException(where + ": weight is missing or not a number");
        }
        int weight = element.getAsInt();
        if (weight < 0) {
            throw new BoosterCodecException(where + ": weight " + weight + " is negative");
        }
        return weight;
    }

    private static UUID printing(String raw, String sheet) throws BoosterCodecException {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new BoosterCodecException(
                    "sheet '" + sheet + "': '" + raw + "' is not a Scryfall id");
        }
    }
}
