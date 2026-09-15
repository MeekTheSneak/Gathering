package dev.gathering.core.scryfall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Legality;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.RelatedCard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads Scryfall's card JSON into {@link CardMetadata}.
 * <p>Deliberately hand-written rather than reflective: Scryfall's schema is somebody
 * else's and it grows, so every field the mod depends on is named here explicitly and a
 * field that disappears fails one card rather than the import. Unknown fields are ignored,
 * which is why the disk cache stores the raw response and parses on read - a field added
 * later becomes available without refetching anything.
 */
public final class ScryfallCardCodec {

    private ScryfallCardCodec() {
    }

    /**
     * Words that repeat across cards, kept once.
     * <p>A server keeps every card it has looked up in memory, and a card read straight off
     * JSON is sixty-odd separate strings - twenty-one of them the names of the formats in its
     * legality table, identical on every card, and most of the rest a set name, a type line or
     * an artist that hundreds of other cards share. Measured on a cache of three thousand cards,
     * strings were four of every five kilobytes. The same word is now the same string.
     * <p>Bounded, so a flood of distinct values stops being shared rather than growing this.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> WORDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Legality tables, which repeat whole: a few dozen distinct tables cover thousands of cards. */
    private static final java.util.concurrent.ConcurrentHashMap<Map<String, Legality>, Map<String, Legality>> TABLES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int MOST_WORDS = 1 << 16;
    private static final int MOST_TABLES = 1 << 12;

    static String shared(String word) {
        if (word == null) {
            return null;
        }
        String kept = WORDS.get(word);
        if (kept != null) {
            return kept;
        }
        if (WORDS.size() >= MOST_WORDS) {
            return word;
        }
        kept = WORDS.putIfAbsent(word, word);
        return kept == null ? word : kept;
    }

    private static Map<String, Legality> sharedTable(Map<String, Legality> table) {
        Map<String, Legality> copied = Map.copyOf(table);
        Map<String, Legality> kept = TABLES.get(copied);
        if (kept != null) {
            return kept;
        }
        if (TABLES.size() >= MOST_TABLES) {
            return copied;
        }
        kept = TABLES.putIfAbsent(copied, copied);
        return kept == null ? copied : kept;
    }

    /** Returns empty rather than throwing when the object is not a usable card. */
    public static Optional<CardMetadata> parse(JsonObject json) {
        if (json == null) {
            return Optional.empty();
        }
        UUID id = uuid(json, "id");
        if (id == null) {
            return Optional.empty();
        }

        List<CardFace> faces = parseFaces(json);

        return Optional.of(new CardMetadata(
                id,
                uuid(json, "oracle_id"),
                string(json, "name"),
                shared(string(json, "mana_cost")),
                number(json, "cmc"),
                shared(string(json, "type_line")),
                string(json, "oracle_text"),
                stringSet(json, "colors"),
                stringSet(json, "color_identity"),
                faces,
                shared(string(json, "layout")),
                shared(string(json, "set")),
                shared(string(json, "set_name")),
                string(json, "collector_number"),
                Rarity.parse(string(json, "rarity")),
                bool(json, "reserved"),
                bool(json, "foil"),
                bool(json, "nonfoil"),
                bool(json, "digital"),
                bool(json, "oversized"),
                stringList(json, "games"),
                parseLegalities(json),
                parsePrices(json),
                string(json, "scryfall_uri"),
                parseRelated(json),
                specialTreatment(json)));
    }

    /**
     * Scryfall returns a list of card objects under {@code data}, plus the queries it could
     * not answer under {@code not_found}. Both matter: the second is what the import screen
     * shows the player.
     */
    public static List<CardMetadata> parseCollection(JsonObject response) {
        return parseCollectionEntries(response).stream().map(ParsedCard::metadata).toList();
    }

    /**
     * The same parse, keeping each card's original JSON beside it.
     * <p>The disk cache stores the raw body rather than a re-serialized model, so this pair
     * is what the caching layer actually needs.
     */
    public static List<ParsedCard> parseCollectionEntries(JsonObject response) {
        List<ParsedCard> cards = new ArrayList<>();
        if (response == null) {
            return cards;
        }
        JsonArray data = array(response, "data");
        if (data == null) {
            return cards;
        }
        for (JsonElement element : data) {
            if (element.isJsonObject()) {
                JsonObject raw = element.getAsJsonObject();
                parse(raw).ifPresent(card -> cards.add(new ParsedCard(card, raw)));
            }
        }
        return cards;
    }

    /** A card and the JSON it was read from. */
    public record ParsedCard(CardMetadata metadata, JsonObject raw) {
    }

    /**
     * Scryfall's {@code all_parts}, which most cards do not have at all.
     * <p>Entries with no id are dropped rather than defaulted: the id is what makes an entry a
     * card, and a nameless relationship to nothing is not worth a row in a menu.
     */
    private static List<RelatedCard> parseRelated(JsonObject json) {
        JsonArray parts = array(json, "all_parts");
        if (parts == null) {
            return List.of();
        }
        List<RelatedCard> related = new ArrayList<>();
        for (JsonElement element : parts) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            UUID id = uuid(part, "id");
            if (id == null) {
                continue;
            }
            related.add(new RelatedCard(
                    id,
                    string(part, "name"),
                    string(part, "type_line"),
                    string(part, "component")));
        }
        return related;
    }

    private static List<CardFace> parseFaces(JsonObject json) {
        List<CardFace> faces = new ArrayList<>();
        JsonArray cardFaces = array(json, "card_faces");
        if (cardFaces != null && !cardFaces.isEmpty()) {
            ImageUris cardLevelImages = parseImageUris(object(json, "image_uris"));
            boolean cardLevelImageUsed = false;
            for (JsonElement element : cardFaces) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject face = element.getAsJsonObject();
                ImageUris faceImages = parseImageUris(object(face, "image_uris"));
                // Split, flip, adventure and aftermath cards are two lots of rules text on
                // one piece of card, so Scryfall publishes one image at card level and none
                // per face. That image belongs to the front and to no other face: giving it
                // to both makes a split card look like two faces that happen to be identical,
                // and everything downstream then draws the same picture twice. A transform
                // card, which really is two pictures, carries its own art on each face and is
                // untouched by this.
                ImageUris images = faceImages;
                if (images.isEmpty() && !cardLevelImageUsed) {
                    images = cardLevelImages;
                    cardLevelImageUsed = true;
                }
                faces.add(new CardFace(
                        string(face, "name"),
                        string(face, "mana_cost"),
                        string(face, "type_line"),
                        string(face, "oracle_text"),
                        string(face, "power"),
                        string(face, "toughness"),
                        string(face, "loyalty"),
                        string(face, "flavor_text"),
                        string(face, "artist"),
                        images));
            }
            return faces;
        }

        faces.add(new CardFace(
                string(json, "name"),
                shared(string(json, "mana_cost")),
                shared(string(json, "type_line")),
                string(json, "oracle_text"),
                shared(string(json, "power")),
                shared(string(json, "toughness")),
                shared(string(json, "loyalty")),
                string(json, "flavor_text"),
                shared(string(json, "artist")),
                parseImageUris(object(json, "image_uris"))));
        return faces;
    }

    private static ImageUris parseImageUris(JsonObject json) {
        if (json == null) {
            return ImageUris.EMPTY;
        }
        return new ImageUris(
                string(json, "small"),
                string(json, "normal"),
                string(json, "large"),
                string(json, "png"),
                string(json, "art_crop"),
                string(json, "border_crop"));
    }

    private static Map<String, Legality> parseLegalities(JsonObject json) {
        JsonObject legalities = object(json, "legalities");
        if (legalities == null) {
            return Map.of();
        }
        Map<String, Legality> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : legalities.entrySet()) {
            out.put(shared(entry.getKey()), Legality.parse(asString(entry.getValue())));
        }
        return sharedTable(out);
    }

    private static Map<String, String> parsePrices(JsonObject json) {
        JsonObject prices = object(json, "prices");
        if (prices == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : prices.entrySet()) {
            String value = asString(entry.getValue());
            if (value != null) {
                out.put(shared(entry.getKey()), value);
            }
        }
        return out;
    }

    private static String string(JsonObject json, String key) {
        return json == null ? null : asString(json.get(key));
    }

    private static String asString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static UUID uuid(JsonObject json, String key) {
        String raw = string(json, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether a printing is one of the special versions a pack can hold beside its ordinary one: a
     * showcase or extended-art frame, no border, full art, or a textured or serialized promo. Read
     * from what Scryfall says about the frame, not guessed from a collector number.
     */
    static boolean specialTreatment(JsonObject json) {
        List<String> effects = stringList(json, "frame_effects");
        if (effects.contains("showcase") || effects.contains("extendedart") || effects.contains("inverted")) {
            return true;
        }
        if ("borderless".equals(string(json, "border_color")) || bool(json, "full_art")) {
            return true;
        }
        List<String> promos = stringList(json, "promo_types");
        return promos.contains("textured") || promos.contains("serialized") || promos.contains("galaxyfoil")
                || promos.contains("surgefoil");
    }

    private static double number(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return 0.0;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static List<String> stringList(JsonObject json, String key) {
        JsonArray array = array(json, key);
        if (array == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            String value = asString(element);
            if (value != null) {
                out.add(shared(value));
            }
        }
        return List.copyOf(out);
    }

    private static Set<String> stringSet(JsonObject json, String key) {
        return new LinkedHashSet<>(stringList(json, key));
    }
}
