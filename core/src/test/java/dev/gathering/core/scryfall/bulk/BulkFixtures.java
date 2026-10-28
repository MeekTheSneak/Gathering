package dev.gathering.core.scryfall.bulk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.gathering.core.testing.Fixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * A bulk card file of a handful of real cards, and a few printings made from them.
 * <p>The real ones are the saved Scryfall replies; the made ones are copies with the fields a test
 * is about changed, because the questions worth asking - which printing is cheapest, which is
 * somebody's foreign copy, which is a token - need more than one printing of a card.
 */
final class BulkFixtures {

    static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
    static final UUID CHEAP_SOL_RING = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID JAPANESE_SOL_RING = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID GOLD_BORDER_SOL_RING = UUID.fromString("00000000-0000-4000-8000-000000000003");
    static final UUID THRULL_TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000004");
    static final UUID THRULL_CREATURE = UUID.fromString("00000000-0000-4000-8000-000000000005");
    static final UUID OLD_THRULL_TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000006");
    static final UUID UNPRICED_SOL_RING = UUID.fromString("00000000-0000-4000-8000-000000000007");
    static final UUID FOIL_ONLY_SOL_RING = UUID.fromString("00000000-0000-4000-8000-000000000008");
    static final UUID FIRE_ICE = UUID.fromString("18303862-4726-4136-814f-157aa7006579");
    static final UUID DELVER = UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a");

    private BulkFixtures() {
    }

    /** Every card in the file, in file order. */
    static List<JsonObject> cards() {
        JsonObject cheap = Fixtures.json("sol_ring");
        cheap.addProperty("id", CHEAP_SOL_RING.toString());
        cheap.addProperty("set", "c21");
        cheap.addProperty("collector_number", "263");
        cheap.addProperty("illustration_id", "11111111-1111-4111-8111-111111111111");
        price(cheap, "1.50");

        // Another language's copy of the cheap printing's picture, cheaper still. Nobody asking
        // for Sol Ring means this one.
        JsonObject japanese = cheap.deepCopy();
        japanese.addProperty("id", JAPANESE_SOL_RING.toString());
        japanese.addProperty("lang", "ja");
        japanese.addProperty("collector_number", "263ja");
        price(japanese, "0.50");

        // A gold-bordered reprint, which a search by name on Scryfall does not list.
        JsonObject gold = Fixtures.json("sol_ring");
        gold.addProperty("id", GOLD_BORDER_SOL_RING.toString());
        gold.addProperty("set", "30a");
        gold.addProperty("set_type", "memorabilia");
        gold.addProperty("collector_number", "7");
        price(gold, "0.10");

        // An online-only promo nobody sells, so with no price at all, and a printing only sold in foil.
        JsonObject unpriced = Fixtures.json("sol_ring");
        unpriced.addProperty("id", UNPRICED_SOL_RING.toString());
        unpriced.addProperty("set", "prm");
        unpriced.addProperty("collector_number", "12345");
        unpriced.add("prices", new JsonObject());
        JsonObject foilOnly = Fixtures.json("sol_ring");
        foilOnly.addProperty("id", FOIL_ONLY_SOL_RING.toString());
        foilOnly.addProperty("set", "sld");
        foilOnly.addProperty("collector_number", "7");
        JsonObject foilPrices = new JsonObject();
        foilPrices.addProperty("usd_foil", "2.00");
        foilOnly.add("prices", foilPrices);

        JsonObject token = Fixtures.json("black_lotus");
        token.addProperty("id", THRULL_TOKEN.toString());
        token.addProperty("oracle_id", "22222222-2222-4222-8222-222222222222");
        token.addProperty("name", "Thrull");
        token.addProperty("type_line", "Token Creature — Thrull");
        token.addProperty("layout", "token");
        token.addProperty("set", "tcmr");
        token.addProperty("collector_number", "9");
        token.addProperty("released_at", "2020-11-20");

        JsonObject oldToken = token.deepCopy();
        oldToken.addProperty("id", OLD_THRULL_TOKEN.toString());
        oldToken.addProperty("set", "tfem");
        oldToken.addProperty("collector_number", "1");
        oldToken.addProperty("released_at", "1995-01-01");

        JsonObject creature = Fixtures.json("black_lotus");
        creature.addProperty("id", THRULL_CREATURE.toString());
        creature.addProperty("oracle_id", "33333333-3333-4333-8333-333333333333");
        creature.addProperty("name", "Thrull");
        creature.addProperty("type_line", "Creature — Thrull");
        creature.addProperty("set", "fem");
        creature.addProperty("collector_number", "10");

        return List.of(
                Fixtures.json("sol_ring"), cheap, japanese, gold, unpriced, foilOnly, token, oldToken, creature,
                Fixtures.json("fire_ice"), Fixtures.json("delver_of_secrets"), Fixtures.json("forest"),
                Fixtures.json("black_lotus"), Fixtures.json("thrasios"));
    }

    private static void price(JsonObject card, String usd) {
        JsonObject prices = card.has("prices") ? card.getAsJsonObject("prices") : new JsonObject();
        prices.addProperty("usd", usd);
        card.add("prices", prices);
    }

    /** The file as a JSON array, the shape {@code download_uri} served. */
    static byte[] asArray(List<JsonObject> cards) {
        JsonArray array = new JsonArray();
        cards.forEach(array::add);
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The file as gzipped JSON lines, the shape {@code jsonl_download_uri} serves. */
    static byte[] asGzippedLines(List<JsonObject> cards) {
        StringBuilder lines = new StringBuilder();
        for (JsonObject card : cards) {
            lines.append(card).append('\n');
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(lines.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Scryfall's list of bulk files, naming a default cards file at this stamp and address. */
    static String catalog(String updatedAt, String uri) {
        return """
                {"object":"list","has_more":false,"data":[
                  {"object":"bulk_data","type":"oracle_cards","updated_at":"%1$s",
                   "jsonl_download_uri":"https://data.scryfall.io/oracle-cards/oracle.jsonl.gz"},
                  {"object":"bulk_data","type":"default_cards","updated_at":"%1$s",
                   "jsonl_download_uri":"%2$s"}
                ]}
                """.formatted(updatedAt, uri);
    }
}
