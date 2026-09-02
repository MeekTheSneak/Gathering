package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.booster.BoosterCodecException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a set's real products.
 * <p>The files are made up and the shapes are not: a pack holding one arrangement, a box
 * holding twelve of that pack by its own id, and a bundle holding some packs, an exact foil
 * promo, a land deck and a spindown die are all straight out of what is published for a real
 * set.
 */
class MtgjsonProductsTest {

    private static final String PROMO = "11111111-1111-4111-8111-111111111111";

    @Test
    @DisplayName("a booster pack is one arrangement and nothing else")
    void aPackIsOneBooster() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "pack-1", "name": "Test Play Booster Pack", "setCode": "TST",
                  "category": "booster_pack", "subtype": "play", "cardCount": 14,
                  "contents": {"pack": [{"code": "play", "set": "tst"}]}}]
                """), Map.of());

        assertThat(reading.products()).hasSize(1);
        SealedProduct pack = reading.products().get(0);
        assertThat(pack.isOneBooster()).isTrue();
        assertThat(pack.asBooster()).isEqualTo(new SealedProduct.Booster("tst", "play"));
        assertThat(pack.cardCount()).isEqualTo(14);
        assertThat(reading.boosters()).containsExactly(pack);
    }

    @Test
    @DisplayName("a box names the pack it holds rather than copying it")
    void aBoxHoldsPacksByName() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "pack-1", "name": "Test Play Booster Pack", "setCode": "TST",
                  "category": "booster_pack", "subtype": "play", "cardCount": 14,
                  "contents": {"pack": [{"code": "play", "set": "tst"}]}},
                 {"uuid": "box-1", "name": "Test Play Booster Box", "setCode": "TST",
                  "category": "booster_box", "subtype": "play", "cardCount": 420,
                  "contents": {"sealed": [
                      {"count": 30, "name": "Test Play Booster Pack", "set": "tst",
                       "uuid": "pack-1"}]}}]
                """), Map.of());

        SealedProduct box = reading.byId("box-1");
        assertThat(box).isNotNull();
        assertThat(box.isOneBooster()).isFalse();
        assertThat(box.holdsOtherProducts()).isTrue();
        assertThat(box.piecesHeld()).isEqualTo(30);
        // What it holds is looked up rather than repeated, so a booster's odds live in one
        // place however many products point at it.
        SealedProduct held = reading.byId(box.contents().holds().get(0).productId());
        assertThat(held).isNotNull();
        assertThat(held.asBooster()).isEqualTo(new SealedProduct.Booster("tst", "play"));
        assertThat(reading.boosters()).hasSize(1);
    }

    @Test
    @DisplayName("a bundle holds some of everything, and its promo is a real printing")
    void aBundleHoldsSomeOfEverything() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "bundle-1", "name": "Test Bundle", "setCode": "TST",
                  "category": "bundle", "subtype": "default", "cardCount": 100,
                  "contents": {
                    "sealed": [{"count": 9, "name": "Test Play Booster Pack", "set": "tst",
                                "uuid": "pack-1"}],
                    "card": [{"name": "Promo Thing", "set": "tst", "number": "386",
                              "foil": true, "uuid": "promo-card"}],
                    "deck": [{"name": "Test Bundle Land Pack", "set": "tst"}],
                    "other": [{"name": "Spindown"}, {"name": "Card-storage box"}]}}]
                """), Map.of("promo-card", UUID.fromString(PROMO)));

        SealedProduct bundle = reading.products().get(0);
        assertThat(bundle.piecesHeld()).isEqualTo(9);
        assertThat(bundle.contents().cards()).hasSize(1);
        assertThat(bundle.contents().cards().get(0).scryfallId())
                .isEqualTo(UUID.fromString(PROMO));
        assertThat(bundle.contents().cards().get(0).foil()).isTrue();
        // Named and not listed, and the set it belongs to comes along with the name: a
        // starter kit names decks published beside it rather than in its own file.
        assertThat(bundle.contents().decks()).containsExactly(
                new SealedProduct.InDeck("Test Bundle Land Pack", "tst"));
        // Kept by name so a product can say what was in the box, and never handed to anybody.
        assertThat(bundle.contents().extras()).containsExactly("Spindown", "Card-storage box");
        assertThat(bundle.isOneBooster()).isFalse();
        assertThat(reading.boosters()).isEmpty();
    }

    @Test
    @DisplayName("a wrapper with a booster and something else in it is not one booster")
    void aBoosterPlusSomethingIsNotJustABooster() throws Exception {
        // A prerelease kit is the shape to think of: an arrangement to open and a promo you
        // are simply given. Calling that a booster would hand the opener a product it would
        // open the wrong contents out of.
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "kit-1", "name": "Test Kit", "setCode": "TST",
                  "category": "limited_aid_tool", "subtype": "prerelease_kit", "cardCount": 15,
                  "contents": {"pack": [{"code": "draft", "set": "tst"}],
                               "card": [{"name": "Promo", "set": "tst", "uuid": "promo-card"}]}}]
                """), Map.of("promo-card", UUID.fromString(PROMO)));

        SealedProduct kit = reading.products().get(0);
        assertThat(kit.contents().boosters()).hasSize(1);
        assertThat(kit.contents().cards()).hasSize(1);
        assertThat(kit.isOneBooster()).isFalse();
        assertThat(kit.asBooster()).isNull();
        assertThat(reading.boosters()).isEmpty();
    }

    @Test
    @DisplayName("a card printed in another set is left out and counted")
    void unbridgedCardsAreLeftOut() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "kit-1", "name": "Test Prerelease Kit", "setCode": "TST",
                  "category": "limited_aid_tool", "subtype": "prerelease_kit", "cardCount": 1,
                  "contents": {"card": [{"name": "Promo", "set": "ptst", "uuid": "elsewhere"}]}}]
                """), Map.of());

        assertThat(reading.products().get(0).contents().cards()).isEmpty();
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("1 card(s) in it are printed in another set"));
    }

    @Test
    @DisplayName("a set that sold nothing sealed says so rather than failing")
    void noProductsReadsEmpty() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(
                JsonParser.parseString("{\"data\": {\"code\": \"TST\"}}").getAsJsonObject(),
                Map.of());

        assertThat(reading.isEmpty()).isTrue();
        assertThat(reading.notes()).containsExactly("tst publishes no sealed product");
        assertThat(reading.byId("anything")).isNull();
    }

    @Test
    @DisplayName("a product list that is not one is refused")
    void aBadProductListIsRefused() {
        assertThatThrownBy(() -> MtgjsonProducts.read(
                JsonParser.parseString(
                        "{\"data\": {\"code\": \"TST\", \"sealedProduct\": \"nope\"}}")
                        .getAsJsonObject(), Map.of()))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("not a list");
        assertThatThrownBy(() -> MtgjsonProducts.read(file("""
                [{"uuid": "x", "name": "X", "contents": {"pack": "nope"}}]
                """), Map.of()))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("'pack' is not a list");
    }

    @Test
    @DisplayName("products keep the order they were published in")
    void productsKeepTheirOrder() throws Exception {
        MtgjsonProducts.Reading reading = MtgjsonProducts.read(file("""
                [{"uuid": "a", "name": "First"}, {"uuid": "b", "name": "Second"},
                 {"uuid": "c", "name": "Third"}]
                """), Map.of());

        assertThat(reading.products()).extracting(SealedProduct::name)
                .containsExactly("First", "Second", "Third");
        assertThat(reading.notes()).hasSize(3);
    }

    private static JsonObject file(String products) {
        return JsonParser.parseString(
                "{\"data\": {\"code\": \"TST\", \"sealedProduct\": " + products + "}}")
                .getAsJsonObject();
    }
}
