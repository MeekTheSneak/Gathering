package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.core.card.CardIdentity;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading real collation out of the shape MTGJSON publishes it in.
 *
 * <p>The files here are small and made up, but their shape is not: it was read off MTGJSON's
 * published data model and off real set files, and the field names, the nesting and the two
 * weight totals are theirs exactly. Made up rather than real because the mod redistributes
 * nobody's card data, and because a set file is four megabytes of which three lines matter to
 * this.
 *
 * <p>The card ids are readable words rather than uuids on purpose: what MTGJSON keys a card by
 * is opaque to this code, which only ever looks it up in the bridge.
 */
class MtgjsonCollationTest {

    private static final String ONE = "11111111-1111-4111-8111-111111111111";
    private static final String TWO = "22222222-2222-4222-8222-222222222222";
    private static final String THREE = "33333333-3333-4333-8333-333333333333";
    private static final String FOUR = "44444444-4444-4444-8444-444444444444";

    @Test
    @DisplayName("a set file's draft booster comes out as something that can be opened")
    void aDraftBoosterComesOutOfASetFile() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(file("""
                {"data": {
                  "code": "TST",
                  "cards": [
                    {"uuid": "a", "identifiers": {"scryfallId": "%s"}},
                    {"uuid": "b", "identifiers": {"scryfallId": "%s"}},
                    {"uuid": "c", "identifiers": {"scryfallId": "%s"}}
                  ],
                  "booster": {
                    "draft": {
                      "sourceSetCodes": ["TST"],
                      "sheets": {
                        "common": {"foil": false, "totalWeight": 3, "cards": {"a": 2, "b": 1}},
                        "rare":   {"foil": true,  "allowDuplicates": true,
                                   "totalWeight": 1, "cards": {"c": 1}}
                      },
                      "boosters": [
                        {"contents": {"common": 2, "rare": 1}, "weight": 7},
                        {"contents": {"common": 3},            "weight": 1}
                      ],
                      "boostersTotalWeight": 8
                    }
                  }
                }}
                """.formatted(ONE, TWO, THREE)));

        assertThat(reading.setCode()).isEqualTo("tst");
        assertThat(reading.notes()).isEmpty();
        assertThat(reading.alsoNeeds()).isEmpty();
        assertThat(reading.packs()).containsOnlyKeys("draft");

        BoosterConfig draft = reading.pack("draft");
        assertThat(draft.id()).isEqualTo("tst:draft");
        assertThat(draft.isUsable()).isTrue();
        assertThat(draft.totalWeight()).isEqualTo(8);
        assertThat(draft.variants()).hasSize(2);
        assertThat(draft.variantAt(0).slots()).containsExactly(
                Map.entry("common", 2), Map.entry("rare", 1));
        assertThat(draft.sheets().get("common").weights())
                .containsExactly(Map.entry(UUID.fromString(ONE), 2),
                        Map.entry(UUID.fromString(TWO), 1));
        assertThat(draft.sheets().get("rare").foil()).isTrue();
        assertThat(draft.sheets().get("rare").duplicates()).isTrue();
        assertThat(draft.sheets().get("common").foil()).isFalse();
        assertThat(draft.sheets().get("common").duplicates()).isFalse();
    }

    @Test
    @DisplayName("a card printed in another set is left off, and the reading says so")
    void cardsFromAnotherSetAreLeftOffAndSaidSo() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(oneSheetOf("""
                {"a": 1, "elsewhere": 1}
                """, 2, "TST", "PLST"));

        assertThat(reading.packs()).containsOnlyKeys("set");
        assertThat(reading.pack("set").sheets().get("common").weights())
                .containsOnlyKeys(UUID.fromString(ONE));
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("1 of 2 cards are printed in another set"));
        assertThat(reading.alsoNeeds()).containsExactly("PLST");
    }

    @Test
    @DisplayName("a sheet nothing on it can be identified takes its kind of pack with it")
    void aSheetOfNothingIdentifiableDropsTheKind() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(oneSheetOf("""
                {"elsewhere": 1, "alsoElsewhere": 1}
                """, 2, "TST", "PLST"));

        assertThat(reading.packs()).isEmpty();
        assertThat(reading.isEmpty()).isTrue();
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("none of its cards could be identified"));
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("dropped").contains("[common]"));
        assertThat(reading.alsoNeeds()).containsExactly("PLST");
    }

    @Test
    @DisplayName("the printings of a second set file complete a pack that reaches into it")
    void printingsFromASecondFileCompleteIt() throws Exception {
        JsonObject reaching = oneSheetOf("""
                {"a": 1, "elsewhere": 1}
                """, 2, "TST", "PLST");
        JsonObject other = file("""
                {"data": {"code": "PLST", "cards": [
                  {"uuid": "elsewhere", "identifiers": {"scryfallId": "%s"}}
                ]}}
                """.formatted(FOUR));

        Map<String, UUID> both = new LinkedHashMap<>(MtgjsonCollation.printings(reaching));
        both.putAll(MtgjsonCollation.printings(other));

        MtgjsonCollation.Reading reading = MtgjsonCollation.read(reaching, both);
        assertThat(reading.notes()).isEmpty();
        assertThat(reading.alsoNeeds()).isEmpty();
        assertThat(reading.pack("set").sheets().get("common").weights())
                .containsOnlyKeys(UUID.fromString(ONE), UUID.fromString(FOUR));
    }

    @Test
    @DisplayName("tokens are in the bridge too, because sheets can name them")
    void tokensAreBridgedAsWell() throws Exception {
        Map<String, UUID> bridge = MtgjsonCollation.printings(file("""
                {"data": {"code": "TST",
                  "cards":  [{"uuid": "a", "identifiers": {"scryfallId": "%s"}}],
                  "tokens": [{"uuid": "t", "identifiers": {"scryfallId": "%s"}}]}}
                """.formatted(ONE, TWO)));
        assertThat(bridge).containsOnlyKeys("a", "t");
    }

    @Test
    @DisplayName("a sheet whose card weights do not come to what it claims is refused")
    void aSheetThatDoesNotAddUpIsRefused() {
        assertThatThrownBy(() -> MtgjsonCollation.read(oneSheetOf("""
                {"a": 1}
                """, 9, "TST", "TST")))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("sheet 'common'")
                .hasMessageContaining("come to 1")
                .hasMessageContaining("says 9");
    }

    @Test
    @DisplayName("packs whose weights do not come to what the set claims are refused")
    void packsThatDoNotAddUpAreRefused() {
        assertThatThrownBy(() -> MtgjsonCollation.read(file("""
                {"data": {
                  "code": "TST",
                  "cards": [{"uuid": "a", "identifiers": {"scryfallId": "%s"}}],
                  "booster": {"draft": {
                    "sheets": {"common": {"foil": false, "totalWeight": 1, "cards": {"a": 1}}},
                    "boosters": [{"contents": {"common": 1}, "weight": 3}],
                    "boostersTotalWeight": 4
                  }}
                }}
                """.formatted(ONE))))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("come to 3")
                .hasMessageContaining("says 4");
    }

    @Test
    @DisplayName("colour balancing is reported rather than pretended")
    void colourBalancingIsReported() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(file("""
                {"data": {
                  "code": "TST",
                  "cards": [{"uuid": "a", "identifiers": {"scryfallId": "%s"}}],
                  "booster": {"draft": {
                    "sheets": {"common": {"foil": false, "balanceColors": true,
                                          "totalWeight": 1, "cards": {"a": 1}}},
                    "boosters": [{"contents": {"common": 1}, "weight": 1}],
                    "boostersTotalWeight": 1
                  }}
                }}
                """.formatted(ONE)));

        assertThat(reading.packs()).containsOnlyKeys("draft");
        assertThat(reading.notes()).anySatisfy(note ->
                assertThat(note).contains("colours are balanced in the real sheet"));
    }

    @Test
    @DisplayName("a set that publishes no collation reads empty rather than failing")
    void noCollationReadsEmpty() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(file("""
                {"data": {"code": "TST", "cards": []}}
                """));
        assertThat(reading.isEmpty()).isTrue();
        assertThat(reading.notes()).containsExactly("tst publishes no booster collation");
        assertThat(reading.pack("draft")).isNull();
    }

    @Test
    @DisplayName("kinds of pack keep the order the file wrote them in")
    void kindsKeepTheirOrder() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(file("""
                {"data": {
                  "code": "TST",
                  "cards": [{"uuid": "a", "identifiers": {"scryfallId": "%s"}}],
                  "booster": {
                    "set":       %s,
                    "draft":     %s,
                    "collector": %s
                  }
                }}
                """.formatted(ONE, plainKind(), plainKind(), plainKind())));
        assertThat(reading.packs().keySet()).containsExactly("set", "draft", "collector");
    }

    @Test
    @DisplayName("a fixed sheet opens as itself, every copy, every time")
    void aFixedSheetOpensAsItself() throws Exception {
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(file("""
                {"data": {
                  "code": "TST",
                  "cards": [
                    {"uuid": "a", "identifiers": {"scryfallId": "%s"}},
                    {"uuid": "b", "identifiers": {"scryfallId": "%s"}}
                  ],
                  "booster": {"jumpstart": {
                    "sheets": {"theme": {"foil": false, "fixed": true,
                                         "totalWeight": 3, "cards": {"a": 2, "b": 1}}},
                    "boosters": [{"contents": {"theme": 3}, "weight": 1}],
                    "boostersTotalWeight": 1
                  }}
                }}
                """.formatted(ONE, TWO)));

        BoosterConfig jumpstart = reading.pack("jumpstart");
        assertThat(jumpstart.sheets().get("theme").fixed()).isTrue();

        for (String label : new String[] {"first", "second", "third"}) {
            OpenedPack pack = BoosterOpener.open(jumpstart, seed(), label);
            assertThat(pack.cards()).containsExactly(
                    CardIdentity.ofPrinting(UUID.fromString(ONE), false),
                    CardIdentity.ofPrinting(UUID.fromString(ONE), false),
                    CardIdentity.ofPrinting(UUID.fromString(TWO), false));
        }
    }

    // ------------------------------------------------------------------- bits

    private static JsonObject file(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** One "set" booster of one sheet, so a test can say only what it is about. */
    private static JsonObject oneSheetOf(String cards, int totalWeight, String own, String source) {
        return file("""
                {"data": {
                  "code": "%s",
                  "cards": [
                    {"uuid": "a", "identifiers": {"scryfallId": "%s"}},
                    {"uuid": "b", "identifiers": {"scryfallId": "%s"}}
                  ],
                  "booster": {"set": {
                    "sourceSetCodes": ["%s", "%s"],
                    "sheets": {"common": {"foil": false, "totalWeight": %d, "cards": %s}},
                    "boosters": [{"contents": {"common": 1}, "weight": 1}],
                    "boostersTotalWeight": 1
                  }}
                }}
                """.formatted(own, ONE, TWO, own, source, totalWeight, cards.trim()));
    }

    private static String plainKind() {
        return """
                {"sheets": {"common": {"foil": false, "totalWeight": 1, "cards": {"a": 1}}},
                 "boosters": [{"contents": {"common": 1}, "weight": 1}],
                 "boostersTotalWeight": 1}
                """;
    }

    private static byte[] seed() {
        return "a seed that is not a secret".getBytes(StandardCharsets.UTF_8);
    }
}
