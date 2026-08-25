package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.booster.BoosterConfig;
import dev.gathering.core.booster.MtgjsonCollation;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one join the whole of collection mode rests on.
 *
 * <p>A pack found in a chest or bought off a shelf is a product, read by one adapter; opening
 * it is collation, read by another. What ties them is a single string - the name a product's
 * pack entry gives its arrangement - and if the two readers ever disagree about that string,
 * every pack in the world becomes a pack that cannot be opened, with nothing anywhere saying
 * why. So it is asserted, on a file holding both halves.
 */
class ProductsMatchCollationTest {

    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String B = "22222222-2222-4222-8222-222222222222";

    /**
     * One file with both halves, in the shape MTGJSON publishes them.
     *
     * <p>Made up, and its shape is not: the two kinds here are the two a modern set really
     * sells, and the pack entry naming its arrangement by {@code code} and its set by
     * {@code set} is exactly how a real file writes it.
     *
     * <p>The collector pack's {@code subtype} deliberately says something its pack entry does
     * not. A product's subtype is a label and its pack entry's code is the key; they agree in
     * most files and not in all of them, and a reader that quietly used the label instead
     * would pass every test where they happen to match.
     */
    private static final String FILE = """
            {"data": {
              "code": "TST",
              "cards": [
                {"uuid": "a", "identifiers": {"scryfallId": "%s"}},
                {"uuid": "b", "identifiers": {"scryfallId": "%s"}}
              ],
              "booster": {
                "play": {
                  "sheets": {"common": {"foil": false, "totalWeight": 2, "cards": {"a": 1, "b": 1}}},
                  "boosters": [{"contents": {"common": 2}, "weight": 1}],
                  "boostersTotalWeight": 1
                },
                "collector": {
                  "sheets": {"rare": {"foil": true, "allowDuplicates": true,
                                      "totalWeight": 1, "cards": {"b": 1}}},
                  "boosters": [{"contents": {"rare": 1}, "weight": 1}],
                  "boostersTotalWeight": 1
                }
              },
              "sealedProduct": [
                {"uuid": "pack-play", "name": "Test Play Booster Pack", "setCode": "TST",
                 "category": "booster_pack", "subtype": "play", "cardCount": 14,
                 "contents": {"pack": [{"code": "play", "set": "tst"}]}},
                {"uuid": "pack-collector", "name": "Test Collector Booster Pack",
                 "setCode": "TST", "category": "booster_pack", "subtype": "default",
                 "cardCount": 15,
                 "contents": {"pack": [{"code": "collector", "set": "tst"}]}},
                {"uuid": "box-play", "name": "Test Play Booster Box", "setCode": "TST",
                 "category": "booster_box", "subtype": "play", "cardCount": 420,
                 "contents": {"sealed": [{"count": 30, "name": "Test Play Booster Pack",
                                          "set": "tst", "uuid": "pack-play"}]}}
              ]
            }}""".formatted(A, B);

    @Test
    @DisplayName("every booster a set sells names an arrangement the set publishes")
    void everySoldBoosterCanBeOpened() throws Exception {
        JsonObject file = parse();
        Map<String, UUID> bridge = MtgjsonCollation.printings(file);
        MtgjsonCollation.Reading collation = MtgjsonCollation.read(file, bridge);
        MtgjsonProducts.Reading products = MtgjsonProducts.read(file, bridge);

        assertThat(products.boosters()).isNotEmpty();
        for (SealedProduct booster : products.boosters()) {
            SealedProduct.Booster names = booster.asBooster();
            assertThat(names.setCode())
                    .as(booster.name() + " names a set the collation is not for")
                    .isEqualTo(collation.setCode());
            BoosterConfig config = collation.pack(names.kind());
            assertThat(config)
                    .as(booster.name() + " names \"" + names.kind()
                            + "\", which this set publishes no arrangement for. It publishes: "
                            + collation.packs().keySet())
                    .isNotNull();
            assertThat(config.isUsable()).isTrue();
        }
    }

    @Test
    @DisplayName("a box is not a booster, so nothing tries to open one")
    void aBoxIsNotOfferedAsSomethingToOpen() {
        // The join only holds for products that are a single arrangement. A box holds packs
        // and a bundle holds a bit of everything; neither has a "code" to look up, and
        // offering one as a pack would be a pack whose kind is the empty string.
        MtgjsonProducts.Reading products = readProducts();

        assertThat(products.boosters()).extracting(SealedProduct::productId)
                .containsExactlyInAnyOrder("pack-play", "pack-collector");
        assertThat(products.byId("box-play").asBooster()).isNull();
    }

    private static MtgjsonProducts.Reading readProducts() {
        try {
            JsonObject file = parse();
            return MtgjsonProducts.read(file, MtgjsonCollation.printings(file));
        } catch (Exception unreadable) {
            throw new AssertionError(unreadable);
        }
    }

    private static JsonObject parse() {
        return JsonParser.parseString(FILE).getAsJsonObject();
    }
}
