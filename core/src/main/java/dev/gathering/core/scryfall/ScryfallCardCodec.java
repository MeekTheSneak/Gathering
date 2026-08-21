package dev.gathering.core.scryfall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Legality;
import dev.gathering.core.card.Rarity;
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
 *
 * <p>Deliberately hand-written rather than reflective: Scryfall's schema is somebody
 * else's and it grows, so every field the mod depends on is named here explicitly and a
 * field that disappears fails one card rather than the import. Unknown fields are ignored,
 * which is why the disk cache stores the raw response and parses on read - a field added
 * later becomes available without refetching anything.
 */
public final class ScryfallCardCodec {

    private ScryfallCardCodec() {
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
                string(json, "mana_cost"),
                number(json, "cmc"),
                string(json, "type_line"),
                string(json, "oracle_text"),
                stringSet(json, "colors"),
                stringSet(json, "color_identity"),
                faces,
                string(json, "layout"),
                string(json, "set"),
                string(json, "set_name"),
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
                string(json, "scryfall_uri")));
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
     *
     * <p>The disk cache stores the raw body rather than a re-serialised model, so this pair
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

    private static List<CardFace> parseFaces(JsonObject json) {
        List<CardFace> faces = new ArrayList<>();
        JsonArray cardFaces = array(json, "card_faces");
        if (cardFaces != null && !cardFaces.isEmpty()) {
            ImageUris cardLevelImages = parseImageUris(object(json, "image_uris"));
            for (JsonElement element : cardFaces) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject face = element.getAsJsonObject();
                ImageUris faceImages = parseImageUris(object(face, "image_uris"));
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
                        // Split and adventure cards carry one image at card level and none
                        // per face; both halves then share the printed image.
                        faceImages.isEmpty() ? cardLevelImages : faceImages));
            }
            return faces;
        }

        faces.add(new CardFace(
                string(json, "name"),
                string(json, "mana_cost"),
                string(json, "type_line"),
                string(json, "oracle_text"),
                string(json, "power"),
                string(json, "toughness"),
                string(json, "loyalty"),
                string(json, "flavor_text"),
                string(json, "artist"),
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
            out.put(entry.getKey(), Legality.parse(asString(entry.getValue())));
        }
        return out;
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
                out.put(entry.getKey(), value);
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
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    private static Set<String> stringSet(JsonObject json, String key) {
        return new LinkedHashSet<>(stringList(json, key));
    }
}
