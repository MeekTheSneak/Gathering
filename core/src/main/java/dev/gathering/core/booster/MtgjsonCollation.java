package dev.gathering.core.booster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads real collation out of one MTGJSON set file.
 *
 * <p>The interpreter next door consumes data and knows nothing about any set, which is what
 * makes it work for every set past and future. This is where the data comes from: MTGJSON
 * publishes, per set, the actual print sheets and the actual pack arrangements, and they map
 * onto the interpreter's own concepts almost exactly, because both are describing the same
 * physical thing.
 *
 * <p>The shapes were read off the published data model and off real set files, not off a
 * memory of them; checked against MTGJSON 5.3.0. A sheet there is
 * {@code { foil, allowDuplicates?, fixed?, balanceColors?, totalWeight, cards: { uuid: weight } }}
 * and a pack arrangement is {@code { weight, contents: { sheetName: count } }}, which is
 * {@link BoosterSheet} and {@link BoosterVariant} under other names.
 *
 * <p>Two things do not map, and both are handled rather than ignored:
 *
 * <p><b>Identity.</b> MTGJSON keys cards by its own uuid, and this mod's one canonical
 * identity is the Scryfall printing id. Every card in a set file carries both, so a set file
 * is its own bridge - but a sheet may name cards printed in <em>another</em> set. The List
 * slot in a modern set booster is nothing but that. So the bridge is a parameter: hand in the
 * printings from as many set files as you have, and what could not be bridged is reported
 * rather than quietly dropped, along with the set codes to go and fetch.
 *
 * <p><b>Colour balancing.</b> Real common sheets are cut so a pack is not five cards of one
 * colour. MTGJSON says which sheets that applies to; this reproduces the sheet and the odds
 * but not the balancing, and says so in the reading's notes rather than pretending otherwise.
 *
 * <p>Strict where the file makes a claim it can check. MTGJSON writes down both a sheet's
 * total weight and every card's weight, and both a pack's total weight and every arrangement's
 * weight; if those disagree the file is not what this thinks it is, and reading on would
 * produce packs that are wrong in a way nobody could see. Every real set checked agrees, so a
 * disagreement is news.
 *
 * <p>Pure. Nothing here reaches a network or a file; something else fetches the JSON.
 */
public final class MtgjsonCollation {

    private MtgjsonCollation() {
    }

    /**
     * What one set file's collation came to.
     *
     * @param setCode   the set read, lower case as Scryfall writes it
     * @param packs     the kinds of pack that can be opened, by kind ("draft", "play",
     *                  "collector"), in the order the file wrote them
     * @param alsoNeeds set codes, as MTGJSON writes them, whose printings would have completed
     *                  a kind that came out short or was dropped. Read those files too and
     *                  pass their printings in.
     * @param notes     everything that was dropped or not reproduced, in plain words, so an
     *                  admin looking at a set that opens oddly has somewhere to look
     */
    public record Reading(
            String setCode,
            Map<String, BoosterConfig> packs,
            List<String> alsoNeeds,
            List<String> notes) {

        public Reading {
            setCode = setCode == null ? "" : setCode;
            packs = packs == null
                    ? Map.of()
                    // Not Map.copyOf: the order kinds were published in is the order they are
                    // offered in, and a per-launch hash order would reshuffle a shop's shelf
                    // every restart.
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(packs));
            alsoNeeds = alsoNeeds == null ? List.of() : List.copyOf(alsoNeeds);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        /** Whether anything at all can be opened from this set. */
        public boolean isEmpty() {
            return packs.isEmpty();
        }

        /** One kind of pack, or null if this set does not publish that one. */
        public BoosterConfig pack(String kind) {
            return packs.get(kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The uuid-to-printing bridge one set file carries.
     *
     * <p>Every card and token in the file, keyed by the uuid its sheets refer to them by. Join
     * several of these to open a set whose packs reach into other sets.
     */
    public static Map<String, UUID> printings(JsonObject file) throws BoosterCodecException {
        JsonObject data = BoosterCodec.object(file, "data");
        Map<String, UUID> bridge = new LinkedHashMap<>();
        gather(bridge, data, "cards");
        gather(bridge, data, "tokens");
        return bridge;
    }

    /** Reads a set file using only the printings that same file carries. */
    public static Reading read(JsonObject file) throws BoosterCodecException {
        return read(file, printings(file));
    }

    /**
     * Reads a set file, bridging card ids through the printings given.
     *
     * @param printings uuid to Scryfall printing id, from {@link #printings} over this file and
     *                  any other set file whose cards its sheets reach into
     */
    public static Reading read(JsonObject file, Map<String, UUID> printings)
            throws BoosterCodecException {
        JsonObject data = BoosterCodec.object(file, "data");
        String setCode = BoosterCodec.string(data, "code").trim().toLowerCase(Locale.ROOT);
        Map<String, UUID> bridge = printings == null ? Map.of() : printings;

        List<String> notes = new ArrayList<>();
        Set<String> alsoNeeds = new LinkedHashSet<>();
        Map<String, BoosterConfig> packs = new LinkedHashMap<>();

        JsonElement boosters = data.get("booster");
        if (boosters == null || boosters.isJsonNull()) {
            notes.add(setCode + " publishes no booster collation");
            return new Reading(setCode, packs, List.of(), notes);
        }
        if (!boosters.isJsonObject()) {
            throw new BoosterCodecException(setCode + ": 'booster' is not an object");
        }

        for (Map.Entry<String, JsonElement> entry : boosters.getAsJsonObject().entrySet()) {
            String kind = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String where = setCode + " " + kind;
            if (!entry.getValue().isJsonObject()) {
                throw new BoosterCodecException(where + ": not an object");
            }
            JsonObject published = entry.getValue().getAsJsonObject();

            int before = notes.size();
            Map<String, BoosterSheet> sheets = sheets(published, where, bridge, notes);
            List<BoosterVariant> variants = variants(published, where);
            BoosterConfig config = new BoosterConfig(setCode, kind, sheets, variants);

            List<String> missing = config.whatIsMissing();
            if (!missing.isEmpty()) {
                notes.add(where + ": dropped, because its packs want sheets that could not be "
                        + "built here: " + missing);
            } else if (config.isUsable()) {
                packs.put(kind, config);
            } else {
                notes.add(where + ": dropped, because nothing in it can be opened");
            }
            if (notes.size() != before) {
                // Something went short in this kind, and the file says which other sets its
                // cards were printed in. That list is the answer to "what do I fetch next".
                for (String source : sourceSets(published)) {
                    if (!source.equalsIgnoreCase(setCode)) {
                        alsoNeeds.add(source);
                    }
                }
            }
        }
        return new Reading(setCode, packs, List.copyOf(alsoNeeds), notes);
    }

    // ------------------------------------------------------------------ sheets

    private static Map<String, BoosterSheet> sheets(
            JsonObject published, String where, Map<String, UUID> bridge, List<String> notes)
            throws BoosterCodecException {
        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry
                : BoosterCodec.object(published, "sheets", where).entrySet()) {
            String name = entry.getKey();
            String here = where + ", sheet '" + name + "'";
            if (!entry.getValue().isJsonObject()) {
                throw new BoosterCodecException(here + ": not an object");
            }
            JsonObject json = entry.getValue().getAsJsonObject();

            Map<UUID, Integer> weights = new LinkedHashMap<>();
            long claimed = 0;
            int unbridged = 0;
            int cards = 0;
            for (Map.Entry<String, JsonElement> card
                    : BoosterCodec.object(json, "cards", here).entrySet()) {
                int weight = BoosterCodec.weight(card.getValue(), here + ", card "
                        + card.getKey());
                claimed += weight;
                cards++;
                UUID printing = bridge.get(card.getKey());
                if (printing == null) {
                    unbridged++;
                } else {
                    // Two of a set's uuids can name the same printing. Summed with the
                    // overflow checked, because a weight that wrapped round to a negative
                    // number would take a card off its own sheet without saying anything.
                    Integer already = weights.get(printing);
                    long sum = already == null ? weight : (long) already + weight;
                    if (sum > Integer.MAX_VALUE) {
                        throw new BoosterCodecException(here + ": the weights on '"
                                + card.getKey() + "' come to " + sum
                                + ", past what one card can carry");
                    }
                    weights.put(printing, (int) sum);
                }
            }

            // The file says what its own cards add up to. If it is wrong about that, this is
            // reading something other than what it thinks it is, and every pack that comes out
            // would be wrong in a way no player could ever notice.
            long stated = stated(json, "totalWeight", here);
            if (stated != claimed) {
                throw new BoosterCodecException(here + ": card weights come to " + claimed
                        + " but the sheet says " + stated);
            }

            if (unbridged > 0) {
                notes.add(here + ": " + unbridged + " of " + cards
                        + " cards are printed in another set and were left off");
            }
            if (flag(json, "balanceColors")) {
                notes.add(here + ": colours are balanced in the real sheet, and are not here");
            }
            if (weights.isEmpty()) {
                notes.add(here + ": dropped, because none of its cards could be identified");
                continue;
            }
            sheets.put(name, new BoosterSheet(
                    name, flag(json, "foil"), flag(json, "allowDuplicates"),
                    flag(json, "fixed"), weights));
        }
        return sheets;
    }

    // ---------------------------------------------------------------- variants

    private static List<BoosterVariant> variants(JsonObject published, String where)
            throws BoosterCodecException {
        JsonArray boosters = BoosterCodec.array(published, "boosters", where);
        List<BoosterVariant> variants = new ArrayList<>();
        long claimed = 0;
        for (int index = 0; index < boosters.size(); index++) {
            String here = where + ", pack " + index;
            JsonElement element = boosters.get(index);
            if (!element.isJsonObject()) {
                throw new BoosterCodecException(here + ": not an object");
            }
            JsonObject json = element.getAsJsonObject();
            int weight = BoosterCodec.weight(json.get("weight"), here);
            claimed += weight;

            Map<String, Integer> slots = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> slot
                    : BoosterCodec.object(json, "contents", here).entrySet()) {
                slots.put(slot.getKey(), BoosterCodec.weight(slot.getValue(),
                        here + ", slot '" + slot.getKey() + "'"));
            }
            // Named by where it sits, because MTGJSON does not name arrangements and a number
            // that says which one it was beats a blank.
            variants.add(new BoosterVariant(String.valueOf(index), weight, slots));
        }
        long stated = stated(published, "boostersTotalWeight", where);
        if (stated != claimed) {
            throw new BoosterCodecException(where + ": pack weights come to " + claimed
                    + " but the set says " + stated);
        }
        return variants;
    }

    // ------------------------------------------------------------------- bits

    private static void gather(Map<String, UUID> into, JsonObject data, String field)
            throws BoosterCodecException {
        JsonElement element = data.get(field);
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (!element.isJsonArray()) {
            throw new BoosterCodecException("'" + field + "' is not a list");
        }
        for (JsonElement each : element.getAsJsonArray()) {
            if (!each.isJsonObject()) {
                continue;
            }
            JsonObject card = each.getAsJsonObject();
            JsonElement uuid = card.get("uuid");
            JsonElement identifiers = card.get("identifiers");
            if (uuid == null || !uuid.isJsonPrimitive() || identifiers == null
                    || !identifiers.isJsonObject()) {
                continue;
            }
            JsonElement scryfall = identifiers.getAsJsonObject().get("scryfallId");
            if (scryfall == null || !scryfall.isJsonPrimitive()) {
                continue;
            }
            try {
                into.put(uuid.getAsString(), UUID.fromString(scryfall.getAsString().trim()));
            } catch (IllegalArgumentException notAUuid) {
                // A card whose Scryfall id is unreadable is a card this cannot name. Left out
                // of the bridge, which is exactly the case the reading already reports.
            }
        }
    }

    /**
     * The sets a kind of pack draws its cards from, as the file names them.
     *
     * <p>Published per kind and authoritative: a set booster that reaches into The List says
     * so here, which is a better answer to "what is missing" than working it out backwards
     * from the ids that could not be bridged.
     */
    private static List<String> sourceSets(JsonObject published) {
        JsonElement element = published.get("sourceSetCodes");
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (JsonElement each : element.getAsJsonArray()) {
            if (each.isJsonPrimitive() && !each.getAsString().isBlank()) {
                codes.add(each.getAsString().trim());
            }
        }
        return codes;
    }

    private static long stated(JsonObject json, String field, String where)
            throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new BoosterCodecException(where + ": '" + field + "' is missing or not a number");
        }
        return element.getAsLong();
    }

    /** Absent means false, which is what leaves a plain sheet needing none of these lines. */
    private static boolean flag(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }
}
