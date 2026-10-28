package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * What the builder and the reader of the local card index have to agree on, in one place.
 * <p>Names on disk, the version, how a card is packed, and the rules that decide which lists a
 * printing belongs in. Two copies of any of these is an index built one way and read another,
 * which does not fail - it answers wrongly.
 */
final class BulkIndexFiles {

    /** Bumped whenever anything written changes shape, so an old index is rebuilt, never misread. */
    static final int VERSION = 1;

    static final int MAGIC = 0x47424958;
    static final int END = 0x454E4421;

    static final String CURRENT = "current";
    static final String DATA_FILE = "cards.dat";
    static final String INDEX_FILE = "cards.idx";
    static final String INDEX_PREFIX = "index-";
    static final String BUILDING_PREFIX = "building-";
    static final String DOWNLOAD_PREFIX = "download-";

    /** What an index directory may be called, so a pointer file cannot name a path elsewhere. */
    static final Pattern INDEX_NAME = Pattern.compile("index-[0-9A-Za-z-]{1,80}");

    /** Far past every printing there has ever been, and short of a file that never ends. */
    static final int MOST_CARDS = 2_000_000;

    /** A name longer than this is not a card's, and is left out of the name lists. */
    static final int LONGEST_KEY = 512;

    /** Scryfall's search pages at this many, and the printing chooser read the first page. */
    static final int SEARCH_PAGE = 175;

    static final byte TOKEN = 1;
    static final byte HIDDEN = 2;
    static final byte ENGLISH = 4;

    /** The layouts Scryfall's search leaves out unless asked, by what it calls extras. */
    private static final Set<String> EXTRA_LAYOUTS = Set.of("token", "double_faced_token", "emblem", "art_series");

    /**
     * Keys and address prefixes every card's JSON repeats, handed to the compressor up front.
     * <p>One card compresses badly on its own - half of it is field names and URLs the compressor
     * has not seen yet. Primed with these, a card packs to about a sixth of its size rather than a
     * third, which on a hundred and eighteen thousand printings is a hundred megabytes rather than
     * two hundred. Field names and address shapes only: no card's own words are in it.
     */
    static final byte[] DICTIONARY = (
            "\"legalities\":{\"standard\":\"not_legal\",\"future\":\"not_legal\",\"historic\":\"not_legal\","
            + "\"timeless\":\"not_legal\",\"gladiator\":\"not_legal\",\"pioneer\":\"not_legal\",\"modern\":\"not_legal\","
            + "\"legacy\":\"legal\",\"pauper\":\"not_legal\",\"vintage\":\"legal\",\"penny\":\"not_legal\","
            + "\"commander\":\"legal\",\"oathbreaker\":\"legal\",\"standardbrawl\":\"not_legal\",\"brawl\":\"not_legal\","
            + "\"alchemy\":\"not_legal\",\"paupercommander\":\"not_legal\",\"duel\":\"legal\",\"oldschool\":\"not_legal\","
            + "\"premodern\":\"not_legal\",\"predh\":\"not_legal\"},"
            + "\"games\":[\"paper\",\"mtgo\"],\"reserved\":false,\"game_changer\":false,\"foil\":true,\"nonfoil\":true,"
            + "\"finishes\":[\"nonfoil\",\"foil\"],\"oversized\":false,\"promo\":false,\"reprint\":true,\"variation\":false,"
            + "\"set_id\":\"\",\"set\":\"\",\"set_name\":\"\",\"set_type\":\"expansion\",\"set_uri\":\"https://api.scryfall.com/sets/\","
            + "\"set_search_uri\":\"https://api.scryfall.com/cards/search?order=set&q=e%3A&unique=prints\","
            + "\"scryfall_set_uri\":\"https://scryfall.com/sets/?utm_source=api\","
            + "\"rulings_uri\":\"https://api.scryfall.com/cards//rulings\","
            + "\"prints_search_uri\":\"https://api.scryfall.com/cards/search?order=released&q=oracleid%3A&unique=prints\","
            + "\"collector_number\":\"\",\"digital\":false,\"rarity\":\"common\",\"card_back_id\":\"\",\"artist\":\"\","
            + "\"artist_ids\":[\"\"],\"illustration_id\":\"\",\"border_color\":\"black\",\"frame\":\"2015\",\"full_art\":false,"
            + "\"textless\":false,\"booster\":true,\"story_spotlight\":false,\"edhrec_rank\":,\"penny_rank\":,"
            + "\"prices\":{\"usd\":null,\"usd_foil\":null,\"usd_etched\":null,\"eur\":null,\"eur_foil\":null,\"tix\":null},"
            + "\"related_uris\":{\"gatherer\":\"https://gatherer.wizards.com/Pages/Card/Details.aspx?multiverseid=\","
            + "\"tcgplayer_infinite_articles\":\"https://partner.tcgplayer.com/c/4931599/1830156/21018?subId1=api"
            + "&trafcat=tcgplayer.com%2Fsearch%2Farticles&u=https%3A%2F%2Ftcgplayer.com%2Fsearch%2Farticles%3FproductLineName%3Dmagic%26q%3D\","
            + "\"tcgplayer_infinite_decks\":\"https://partner.tcgplayer.com/c/4931599/1830156/21018?subId1=api"
            + "&trafcat=tcgplayer.com%2Fsearch%2Fdecks&u=https%3A%2F%2Ftcgplayer.com%2Fsearch%2Fdecks%3FproductLineName%3Dmagic%26q%3D\","
            + "\"edhrec\":\"https://edhrec.com/route/?cc=\"},"
            + "\"purchase_uris\":{\"tcgplayer\":\"https://partner.tcgplayer.com/c/4931599/1830156/21018?subId1=api"
            + "&u=https%3A%2F%2Fwww.tcgplayer.com%2Fproduct%2F%3Fpage%3D1\","
            + "\"cardmarket\":\"https://www.cardmarket.com/en/Magic/Products/Search?referrer=scryfall&searchString="
            + "&utm_campaign=card_prices&utm_medium=text&utm_source=scryfall\","
            + "\"cardhoarder\":\"https://www.cardhoarder.com/cards/?affiliate_id=scryfall&ref=card-profile"
            + "&utm_campaign=affiliate&utm_medium=card&utm_source=scryfall\"}"
            + "{\"object\":\"card\",\"id\":\"\",\"oracle_id\":\"\",\"multiverse_ids\":[],\"mtgo_id\":,\"arena_id\":,"
            + "\"tcgplayer_id\":,\"cardmarket_id\":,\"name\":\"\",\"lang\":\"en\",\"released_at\":\"\","
            + "\"uri\":\"https://api.scryfall.com/cards/\",\"scryfall_uri\":\"https://scryfall.com/card/?utm_source=api\","
            + "\"layout\":\"normal\",\"highres_image\":true,\"image_status\":\"highres_scan\","
            + "\"image_uris\":{\"small\":\"https://cards.scryfall.io/small/front/.jpg?\","
            + "\"normal\":\"https://cards.scryfall.io/normal/front/.jpg?\",\"large\":\"https://cards.scryfall.io/large/front/.jpg?\","
            + "\"png\":\"https://cards.scryfall.io/png/front/.png?\",\"art_crop\":\"https://cards.scryfall.io/art_crop/front/.jpg?\","
            + "\"border_crop\":\"https://cards.scryfall.io/border_crop/front/.jpg?\"},"
            + "\"mana_cost\":\"\",\"cmc\":,\"type_line\":\"\",\"oracle_text\":\"\",\"colors\":[],\"color_identity\":[],"
            + "\"keywords\":[],\"all_parts\":[{\"object\":\"related_card\",\"id\":\"\",\"component\":\"token\",\"name\":\"\","
            + "\"type_line\":\"Token Creature — \",\"uri\":\"https://api.scryfall.com/cards/\"}],"
            + "\"card_faces\":[{\"object\":\"card_face\",\"name\":\"\",\"mana_cost\":\"\",\"type_line\":\"\",\"oracle_text\":\"\","
            + "\"power\":\"\",\"toughness\":\"\",\"flavor_text\":\"\",\"artist\":\"\",\"artist_id\":\"\",\"illustration_id\":\"\"}],"
            + "\"produced_mana\":[]").getBytes(StandardCharsets.UTF_8);

    private BulkIndexFiles() {
    }

    /** One card's JSON, packed against the dictionary. The deflater is reused and reset here. */
    static byte[] pack(Deflater deflater, byte[] json) {
        deflater.reset();
        deflater.setDictionary(DICTIONARY);
        deflater.setInput(json);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, json.length / 4));
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            out.write(buffer, 0, written);
        }
        return out.toByteArray();
    }

    /** The JSON a packed card was made from. */
    static String unpack(byte[] packed) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 6);
            byte[] buffer = new byte[16384];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && !inflater.finished()) {
                    if (!inflater.needsDictionary()) {
                        // Out of input with the card not finished, which is a card cut short.
                        throw new IOException("A packed card ended early");
                    }
                    inflater.setDictionary(DICTIONARY);
                    continue;
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException | IllegalArgumentException corrupt) {
            throw new IOException("A packed card could not be read", corrupt);
        } finally {
            inflater.end();
        }
    }

    /**
     * Which lists a printing belongs in, from its own JSON.
     * <p>{@link #HIDDEN} is what an exact-name search on Scryfall leaves out, worked out by asking it
     * and comparing: tokens, emblems and art cards, the gold-bordered and anniversary reprints it
     * files as memorabilia, and playtest cards. Nobody means one of those by a card's name. The set
     * lists keep them, because a search by set does.
     */
    static byte flags(JsonObject card) {
        byte flags = 0;
        String layout = lower(text(card, "layout"));
        if (isToken(card, layout)) {
            flags |= TOKEN;
        }
        if (EXTRA_LAYOUTS.contains(layout)
                || "memorabilia".equals(lower(text(card, "set_type")))
                || contains(card.get("promo_types"), "playtest")) {
            flags |= HIDDEN;
        }
        String language = lower(text(card, "lang"));
        if (language.isEmpty() || "en".equals(language)) {
            flags |= ENGLISH;
        }
        return flags;
    }

    /** A token by its layout, or by the word on its type line or on either face's. */
    private static boolean isToken(JsonObject card, String layout) {
        if ("token".equals(layout) || "double_faced_token".equals(layout)) {
            return true;
        }
        if (hasTokenWord(text(card, "type_line"))) {
            return true;
        }
        JsonElement faces = card.get("card_faces");
        if (faces != null && faces.isJsonArray()) {
            for (JsonElement face : faces.getAsJsonArray()) {
                if (face.isJsonObject() && hasTokenWord(text(face.getAsJsonObject(), "type_line"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTokenWord(String typeLine) {
        if (typeLine == null) {
            return false;
        }
        for (String word : typeLine.split("[^A-Za-z]+")) {
            if (word.equalsIgnoreCase("token")) {
                return true;
            }
        }
        return false;
    }

    /** Every name a printing answers to: its own and each face's, as a name lookup keys them. */
    static Set<String> nameKeys(JsonObject card) {
        Set<String> keys = new LinkedHashSet<>();
        addKey(keys, text(card, "name"));
        JsonElement faces = card.get("card_faces");
        if (faces != null && faces.isJsonArray()) {
            for (JsonElement face : faces.getAsJsonArray()) {
                if (face.isJsonObject()) {
                    addKey(keys, text(face.getAsJsonObject(), "name"));
                }
            }
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String name) {
        String key = nameKey(name);
        if (!key.isEmpty() && key.length() <= LONGEST_KEY) {
            keys.add(key);
        }
    }

    /** A name as the index keys it. The same fold the in-memory store uses. */
    static String nameKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).strip();
    }

    /**
     * The price in dollars, or not a number when there is none.
     * <p>The ordinary printing's, else the foil's, else the etched foil's - the order
     * {@link dev.gathering.core.card.CardMetadata#usdPrice} reads them in, and the order Scryfall's
     * price sort evidently does too: a printing only sold in foil sits among the others at its foil
     * price rather than with the printings that have no price at all.
     */
    static float usd(JsonObject card) {
        JsonElement prices = card.get("prices");
        if (prices == null || !prices.isJsonObject()) {
            return Float.NaN;
        }
        for (String key : new String[] {"usd", "usd_foil", "usd_etched"}) {
            String usd = text(prices.getAsJsonObject(), key);
            if (usd == null) {
                continue;
            }
            try {
                float value = Float.parseFloat(usd);
                if (Float.isFinite(value)) {
                    return value;
                }
            } catch (NumberFormatException notAPrice) {
                // The next one, then.
            }
        }
        return Float.NaN;
    }

    /** A release date as yyyymmdd, so newer is larger; nought when there is none. */
    static int released(JsonObject card) {
        String date = text(card, "released_at");
        if (date == null || date.length() != 10 || date.charAt(4) != '-' || date.charAt(7) != '-') {
            return 0;
        }
        try {
            return Integer.parseInt(date.substring(0, 4)) * 10000
                    + Integer.parseInt(date.substring(5, 7)) * 100
                    + Integer.parseInt(date.substring(8, 10));
        } catch (NumberFormatException notADate) {
            return 0;
        }
    }

    /**
     * Collector numbers in the order a set lists them: by the first number in them, then as text.
     * <p>"9" before "10", which plain text gets backwards, "10a" after "10", and an Alchemy
     * rebalancing's "A-138" straight after the "138" it rebalances, which is where Scryfall puts it.
     */
    static final Comparator<String> COLLECTOR_ORDER = (a, b) -> {
        long left = leadingNumber(a);
        long right = leadingNumber(b);
        if (left != right) {
            return Long.compare(left, right);
        }
        return a.compareTo(b);
    };

    private static long leadingNumber(String number) {
        long value = 0;
        int digits = 0;
        int i = 0;
        while (i < number.length() && (number.charAt(i) < '0' || number.charAt(i) > '9')) {
            i++;
        }
        for (; i < number.length() && digits < 12; i++) {
            char c = number.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
            digits++;
        }
        return digits == 0 ? Long.MAX_VALUE : value;
    }

    static String text(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(JsonElement array, String wanted) {
        if (array == null || !array.isJsonArray()) {
            return false;
        }
        JsonArray values = array.getAsJsonArray();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive() && wanted.equals(value.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** Deletes a file or a directory and everything in it, as far as it can. */
    static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(each);
                } catch (IOException stillInUse) {
                    // Left for the next sweep. On some systems a file another reader still has
                    // open cannot be deleted, and the reader is about to let go of it.
                }
            }
        } catch (IOException | java.io.UncheckedIOException unreadable) {
            // The same.
        }
    }
}
