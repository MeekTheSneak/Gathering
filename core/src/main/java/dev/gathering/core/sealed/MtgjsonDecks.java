package dev.gathering.core.sealed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gathering.core.booster.BoosterCodec;
import dev.gathering.core.booster.BoosterCodecException;
import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The decks a set publishes, read out of its own file.
 *
 * <p>Every product that comes with a deck names it, and the deck itself is listed further
 * down the same file - which is what makes it possible to sell somebody a Commander precon
 * and hand them the hundred cards rather than the box it came in.
 *
 * <p>A card the bridge cannot turn into a Scryfall printing is left out and counted, the same
 * way collation does it: a deck ninety-nine cards long is a deck, and a deck nobody can build
 * because one card of it has no id is not.
 *
 * <p>Pure.
 */
public final class MtgjsonDecks {

    /** As long as any real deck, with room for a cube somebody called a deck. */
    private static final int MOST_CARDS = 600;

    private MtgjsonDecks() {
    }

    /** What was read, and what could not be. */
    public record Reading(List<SealedDeck> decks, List<String> notes) {

        public static final Reading NOTHING = new Reading(List.of(), List.of());

        public Reading {
            decks = decks == null ? List.of() : List.copyOf(decks);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        /** The deck of this set with this name, or null. */
        public SealedDeck named(String setCode, String name) {
            for (SealedDeck deck : decks) {
                if (deck.is(setCode, name)) {
                    return deck;
                }
            }
            return null;
        }
    }

    /**
     * Every deck in one set file.
     *
     * @param bridge MTGJSON uuid to Scryfall printing, as collation builds it
     */
    public static Reading read(JsonObject file, Map<String, UUID> bridge) {
        if (file == null) {
            return Reading.NOTHING;
        }
        JsonObject data;
        try {
            data = BoosterCodec.object(file, "data");
        } catch (BoosterCodecException notASetFile) {
            return Reading.NOTHING;
        }
        JsonElement listed = data.get("decks");
        if (listed == null || !listed.isJsonArray()) {
            // A set with no decks. Most of them.
            return Reading.NOTHING;
        }

        String setCode = text(data, "code").toLowerCase(java.util.Locale.ROOT);
        List<SealedDeck> decks = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (JsonElement entry : listed.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject deck = entry.getAsJsonObject();
            String name = text(deck, "name");
            if (name.isEmpty()) {
                continue;
            }
            int[] missing = new int[1];
            List<CardIdentity> commanders = cards(deck, "commander", bridge, missing);
            List<CardIdentity> mainboard = cards(deck, "mainBoard", bridge, missing);
            List<CardIdentity> sideboard = cards(deck, "sideBoard", bridge, missing);
            if (commanders.isEmpty() && mainboard.isEmpty()) {
                notes.add(name + " has no cards this could read");
                continue;
            }
            if (missing[0] > 0) {
                notes.add(name + " is missing " + missing[0]
                        + " card(s) nothing could turn into a printing");
            }
            decks.add(new SealedDeck(
                    name,
                    text(deck, "code").isEmpty() ? setCode : text(deck, "code"),
                    commanders, mainboard, sideboard));
        }
        return new Reading(decks, notes);
    }

    /** One section of a deck, expanded so four Forests are four entries. */
    private static List<CardIdentity> cards(
            JsonObject deck, String section, Map<String, UUID> bridge, int[] missing) {
        JsonElement listed = deck.get(section);
        if (listed == null || !listed.isJsonArray()) {
            return List.of();
        }
        JsonArray entries = listed.getAsJsonArray();
        List<CardIdentity> cards = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject card = entry.getAsJsonObject();
            UUID printing = bridge.get(text(card, "uuid"));
            int count = number(card, "count");
            if (printing == null) {
                missing[0] += Math.max(1, count);
                continue;
            }
            boolean foil = flag(card, "isFoil");
            for (int copy = 0; copy < count && cards.size() < MOST_CARDS; copy++) {
                cards.add(CardIdentity.ofPrinting(printing, foil));
            }
        }
        return List.copyOf(cards);
    }

    private static String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static boolean flag(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static int number(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        long count = value.getAsLong();
        return count <= 0 || count > MOST_CARDS ? 0 : (int) count;
    }
}
