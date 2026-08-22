package dev.gathering.core.deck;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.core.decklist.ParseProblem;
import dev.gathering.core.decklist.ParsedDecklist;
import dev.gathering.core.scryfall.HttpTransport;
import dev.gathering.core.scryfall.ScryfallException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads a deck from Archidekt's public API.
 *
 * <p>Better than a text export in one way that matters: every card entry carries the
 * printing's Scryfall id, so the import resolves by exact identity. A text export can only
 * name a card, which leaves the mod guessing at printings and tripping over split cards.
 *
 * <p>Sections come from Archidekt's own categories rather than from a name convention. The
 * deck declares its categories with two flags - {@code isPremier} marks the command zone,
 * {@code includedInDeck} marks whether a category counts at all - and a card lists which
 * categories it belongs to. Reading the flags rather than matching on the word "Commander"
 * means a deck that renamed its categories still imports correctly.
 */
public final class ArchidektDeckSource {

    /** Enough for a very large deck with every card's full printing data. */
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final HttpTransport transport;
    private final Map<String, String> headers;

    public ArchidektDeckSource(HttpTransport transport, String userAgent) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.headers = Map.of("User-Agent", userAgent, "Accept", "application/json");
    }

    /**
     * Fetches and converts a deck.
     *
     * @throws ScryfallException when the deck cannot be read; the message is written to be
     *                           shown to the player, because that is where it ends up
     */
    public ParsedDecklist fetch(DeckLink link) throws IOException {
        if (link.provider() != DeckLink.Provider.ARCHIDEKT) {
            throw new IllegalArgumentException("Not an Archidekt link: " + link);
        }

        HttpTransport.HttpReply reply = transport.get(link.apiUrl(), headers);
        if (reply.status() == 404) {
            throw new ScryfallException(
                    "That Archidekt deck does not exist, or it is private. Only public decks can be imported.",
                    reply.status());
        }
        if (!reply.isSuccess()) {
            throw new ScryfallException(
                    "Archidekt could not be reached (HTTP " + reply.status() + "). Try again, or paste the text export.",
                    reply.status());
        }
        if (reply.body() != null && reply.body().length() > MAX_RESPONSE_BYTES) {
            throw new ScryfallException("That deck is implausibly large; paste the text export instead.", -1);
        }
        return convert(parseJson(reply.body()));
    }

    static ParsedDecklist convert(JsonObject deck) {
        Map<String, CategoryRules> categories = readCategories(deck);
        List<DecklistEntry> entries = new ArrayList<>();
        List<ParseProblem> problems = new ArrayList<>();

        JsonArray cards = array(deck, "cards");
        int index = 0;
        for (JsonElement element : cards == null ? new JsonArray() : cards) {
            index++;
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonObject card = object(entry, "card");
            if (card == null) {
                problems.add(new ParseProblem(index, "card " + index, "Archidekt sent an entry with no card in it"));
                continue;
            }

            UUID printing = uuid(card, "uid");
            String name = displayName(card);
            if (printing == null && name == null) {
                // A custom card, which has no Scryfall printing to point at.
                problems.add(new ParseProblem(index, name == null ? "card " + index : name,
                        "that card is not a real printing and cannot be imported"));
                continue;
            }

            int quantity = Math.max(1, integer(entry, "quantity", 1));
            DeckSection section = sectionFor(entry, categories);

            entries.add(new DecklistEntry(
                    quantity,
                    name == null ? "" : name,
                    editionCode(card),
                    string(card, "collectorNumber"),
                    isFoil(entry),
                    section,
                    index,
                    quantity + " " + name,
                    printing));
        }

        return new ParsedDecklist(string(deck, "name"), entries, problems);
    }

    /**
     * Which pile a card belongs to.
     *
     * <p>Checked in order: an explicitly excluded category wins over everything, because a
     * maybeboard card is not in the deck whatever else it is tagged with.
     */
    private static DeckSection sectionFor(JsonObject entry, Map<String, CategoryRules> categories) {
        List<String> names = stringList(entry, "categories");

        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT);
            if (key.equals("maybeboard")) {
                return DeckSection.MAYBEBOARD;
            }
            if (key.equals("sideboard")) {
                return DeckSection.SIDEBOARD;
            }
        }
        for (String name : names) {
            CategoryRules rules = categories.get(name.toLowerCase(Locale.ROOT));
            if (rules != null && rules.premier()) {
                return DeckSection.COMMANDER;
            }
        }
        for (String name : names) {
            CategoryRules rules = categories.get(name.toLowerCase(Locale.ROOT));
            if (rules != null && !rules.includedInDeck()) {
                // A category the deck's owner marked as not counting, whatever they named it.
                return DeckSection.MAYBEBOARD;
            }
        }
        return DeckSection.MAINBOARD;
    }

    private static Map<String, CategoryRules> readCategories(JsonObject deck) {
        Map<String, CategoryRules> rules = new LinkedHashMap<>();
        JsonArray categories = array(deck, "categories");
        if (categories == null) {
            return rules;
        }
        for (JsonElement element : categories) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject category = element.getAsJsonObject();
            String name = string(category, "name");
            if (name != null) {
                rules.put(name.toLowerCase(Locale.ROOT),
                        new CategoryRules(bool(category, "isPremier"), bool(category, "includedInDeck", true)));
            }
        }
        return rules;
    }

    /** Archidekt records the finish on the deck entry rather than on the printing. */
    private static boolean isFoil(JsonObject entry) {
        String modifier = string(entry, "modifier");
        return modifier != null && !modifier.equalsIgnoreCase("Normal");
    }

    private static String displayName(JsonObject card) {
        JsonObject oracle = object(card, "oracleCard");
        String oracleName = oracle == null ? null : string(oracle, "name");
        return oracleName != null ? oracleName : string(card, "displayName");
    }

    private static String editionCode(JsonObject card) {
        JsonObject edition = object(card, "edition");
        String code = edition == null ? null : string(edition, "editioncode");
        return code == null ? null : code.toUpperCase(Locale.ROOT);
    }

    private static JsonObject parseJson(String body) throws ScryfallException {
        try {
            JsonElement element = JsonParser.parseString(body == null ? "" : body);
            if (!element.isJsonObject()) {
                throw new ScryfallException("Archidekt sent something that is not a deck.", -1);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ScryfallException("Archidekt sent a reply that could not be read.", e);
        }
    }

    private record CategoryRules(boolean premier, boolean includedInDeck) {
    }

    // ------------------------------------------------------------- json bits

    private static String string(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString();
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

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement element = json == null ? null : json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key) {
        return bool(json, key, false);
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json == null ? null : json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
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
        List<String> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                out.add(element.getAsString());
            }
        }
        return out;
    }
}
