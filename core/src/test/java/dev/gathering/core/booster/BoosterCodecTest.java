package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Booster collation a server wrote down by hand.
 *
 * <p>Half of these are about what happens when the file is wrong, which is the half that
 * matters: a typo in a hand-edited file is ordinary, and the failure to avoid is not a crash
 * but a config that quietly loaded as something else and made packs nobody can tell are
 * wrong.
 */
class BoosterCodecTest {

    private static final String ONE = "5805f64c-dd88-4e94-8f0a-a01dae67e3ba";
    private static final String TWO = "11bf83bb-c95b-4b4f-9a56-ce7a1816307a";
    private static final String THREE = "b7833c56-eb62-4c14-9db6-b9c1c92cb4ba";

    private static final String GOOD = """
            {
              "set": "ABC",
              "kind": "Draft",
              "sheets": {
                "common": { "duplicates": false,
                            "cards": { "%s": 4, "%s": 1 } },
                "shiny":  { "foil": true,
                            "cards": { "%s": 1 } }
              },
              "variants": [
                { "name": "plain",    "weight": 7, "slots": { "common": 2 } },
                { "name": "upgraded", "weight": 1, "slots": { "common": 1, "shiny": 1 } }
              ]
            }
            """.formatted(ONE, TWO, THREE);

    @Test
    void aFileAServerWroteOpensPacks() throws Exception {
        BoosterConfig config = BoosterCodec.read(parse(GOOD));

        assertThat(config.id()).isEqualTo("abc:draft");
        assertThat(config.isUsable()).isTrue();
        assertThat(config.sheets().keySet()).containsExactly("common", "shiny");
        assertThat(config.totalWeight()).isEqualTo(8);

        OpenedPack pack = BoosterOpener.open(
                config, "seed".getBytes(StandardCharsets.UTF_8), "1");
        assertThat(pack.size()).isEqualTo(2);
    }

    /** Weights, foils and duplicates all come off the file rather than being assumed. */
    @Test
    void everySettingComesOffTheFile() throws Exception {
        BoosterConfig config = BoosterCodec.read(parse(GOOD));

        BoosterSheet common = config.sheets().get("common");
        assertThat(common.foil()).isFalse();
        assertThat(common.duplicates()).isFalse();
        assertThat(common.total()).describedAs("four plus one").isEqualTo(5);
        assertThat(common.weights().get(UUID.fromString(ONE))).isEqualTo(4);

        assertThat(config.sheets().get("shiny").foil()).isTrue();
    }

    /**
     * The order the file was written in is the order it is walked in.
     *
     * <p>The opener draws by walking weights until it passes the roll, so a reader that
     * handed back its sheets or its cards in some other order would open different packs
     * from the same file - and a file is exactly the thing somebody expects to be able to
     * read and predict.
     */
    @Test
    @DisplayName("a file is walked in the order it was written")
    void theOrderInTheFileIsTheOrderItIsWalked() throws Exception {
        BoosterConfig config = BoosterCodec.read(parse(GOOD));

        assertThat(config.sheets().get("common").printings())
                .containsExactly(UUID.fromString(ONE), UUID.fromString(TWO));
        assertThat(config.variants())
                .extracting(BoosterVariant::name)
                .containsExactly("plain", "upgraded");
    }

    /** Written back and read again is the same config, so a file can be generated. */
    @Test
    void whatIsWrittenReadsBackTheSame() throws Exception {
        BoosterConfig config = BoosterCodec.read(parse(GOOD));

        assertThat(BoosterCodec.read(BoosterCodec.write(config))).isEqualTo(config);
    }

    /**
     * A slot naming a sheet that is not there is refused when the file is read.
     *
     * <p>The mistake somebody actually makes, and the one that must not pass: a mistyped
     * sheet name loads as a pack quietly missing a card, which nobody can see. Found at load
     * rather than at opening, so the admin who typed it is the one who hears about it.
     */
    @Test
    void aSlotNamingASheetThatIsNotThereIsRefused() {
        String typo = GOOD.replace("\"common\": 2", "\"comon\": 2");

        assertThatThrownBy(() -> BoosterCodec.read(parse(typo)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("comon");
    }

    /** A card id that is not an id says which one, on which sheet. */
    @Test
    void aCardIdThatIsNotAnIdSaysWhichAndWhere() {
        String broken = GOOD.replace(ONE, "not-a-uuid");

        assertThatThrownBy(() -> BoosterCodec.read(parse(broken)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("not-a-uuid")
                .hasMessageContaining("common");
    }

    /** As does a weight that is not a number. */
    @Test
    void aWeightThatIsNotANumberSaysWhere() {
        String broken = GOOD.replace("\"%s\": 4".formatted(ONE), "\"%s\": \"lots\"".formatted(ONE));

        assertThatThrownBy(() -> BoosterCodec.read(parse(broken)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("common")
                .hasMessageContaining(ONE);
    }

    /** And a negative one, which would otherwise be silently dropped as a card off the sheet. */
    @Test
    void aNegativeWeightIsRefusedRatherThanDropped() {
        String broken = GOOD.replace("\"%s\": 1,".formatted(TWO), "\"%s\": -1,".formatted(TWO))
                .replace("\"%s\": 1 }".formatted(TWO), "\"%s\": -1 }".formatted(TWO));

        assertThatThrownBy(() -> BoosterCodec.read(parse(broken)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("negative");
    }

    /** A sheet with nothing on it is a mistake rather than an empty sheet. */
    @Test
    void aSheetWithNoCardsIsRefused() {
        String empty = """
                { "set": "abc", "sheets": { "common": { "cards": {} } },
                  "variants": [ { "weight": 1, "slots": { "common": 1 } } ] }
                """;

        assertThatThrownBy(() -> BoosterCodec.read(parse(empty)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("no cards");
    }

    /** So is a file with no arrangement anybody could ever get. */
    @Test
    void aFileWhereNoVariantCanBeOpenedIsRefused() {
        String none = """
                { "set": "abc", "sheets": { "common": { "cards": { "%s": 1 } } },
                  "variants": [ { "name": "never", "weight": 0, "slots": { "common": 1 } } ] }
                """.formatted(ONE);

        assertThatThrownBy(() -> BoosterCodec.read(parse(none)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("can ever be opened");
    }

    /** A missing set, sheets block or variants list each say which is missing. */
    @Test
    void whatIsMissingIsNamed() {
        assertThatThrownBy(() -> BoosterCodec.read(parse("{ \"sheets\": {}, \"variants\": [] }")))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("set");
        assertThatThrownBy(() -> BoosterCodec.read(parse("{ \"set\": \"abc\", \"variants\": [] }")))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("sheets");
        assertThatThrownBy(() -> BoosterCodec.read(parse("{ \"set\": \"abc\", \"sheets\": {} }")))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("variants");
        assertThatThrownBy(() -> BoosterCodec.read(null))
                .isInstanceOf(BoosterCodecException.class);
    }

    /** A plain sheet needs neither flag written down. */
    @Test
    void aPlainSheetNeedsNoFlags() throws Exception {
        String plain = """
                { "set": "abc", "sheets": { "common": { "cards": { "%s": 1 } } },
                  "variants": [ { "weight": 1, "slots": { "common": 1 } } ] }
                """.formatted(ONE);

        BoosterConfig config = BoosterCodec.read(parse(plain));

        assertThat(config.sheets().get("common").foil()).isFalse();
        assertThat(config.sheets().get("common").duplicates()).isFalse();
        assertThat(config.variants().get(0).name())
                .describedAs("an unnamed variant is named by its place")
                .isEqualTo("0");
    }

    /** A flag that is not true or false is a mistake, not a truthy string. */
    @Test
    void aFlagThatIsNotTrueOrFalseIsRefused() {
        String broken = GOOD.replace("\"foil\": true", "\"foil\": \"yes\"");

        assertThatThrownBy(() -> BoosterCodec.read(parse(broken)))
                .isInstanceOf(BoosterCodecException.class)
                .hasMessageContaining("foil");
    }

    /** What the fallback builds can be written down and read back, so a server can start there. */
    @Test
    void aFallbackConfigCanBeWrittenOutAndReadBack() throws Exception {
        java.util.Map<dev.gathering.core.card.Rarity, List<UUID>> pool =
                new java.util.EnumMap<>(dev.gathering.core.card.Rarity.class);
        pool.put(dev.gathering.core.card.Rarity.COMMON,
                List.of(UUID.fromString(ONE), UUID.fromString(TWO)));
        pool.put(dev.gathering.core.card.Rarity.RARE, List.of(UUID.fromString(THREE)));
        BoosterConfig built = BoosterFallback.configFor("abc", "draft", pool, RaritySlots.usual());

        assertThat(BoosterCodec.read(BoosterCodec.write(built))).isEqualTo(built);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
