package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Which color a themed pack is, counted off its sheets rather than read off its name. */
class BoosterColorsTest {

    private static BoosterSheet sheetOf(String name, Map<UUID, String> colors) {
        Map<UUID, Long> weights = new LinkedHashMap<>();
        colors.keySet().forEach(printing -> weights.put(printing, 1L));
        return new BoosterSheet(name, false, false, false, weights, false, colors);
    }

    @Nested
    @DisplayName("counting a pack's color")
    class Counting {

        @Test
        @DisplayName("calls a pack the color most of its colored cards are")
        void mostlyRed() {
            Map<UUID, String> cards = new LinkedHashMap<>();
            for (int at = 0; at < 8; at++) {
                cards.put(UUID.randomUUID(), "R");
            }
            cards.put(UUID.randomUUID(), "G");
            BoosterConfig config = new BoosterConfig("j25", "jumpstart",
                    Map.of("goblins1", sheetOf("goblins1", cards)),
                    List.of(new BoosterVariant("goblins1", 1, Map.of("goblins1", 20))));
            assertThat(BoosterColors.colorOf(config, config.variants().get(0)))
                    .isEqualTo('R');
        }

        @Test
        @DisplayName("does not count lands, so a pack is not less red for its Mountains")
        void landsDoNotCount() {
            Map<UUID, String> cards = new LinkedHashMap<>();
            cards.put(UUID.randomUUID(), "R");
            // Seven colorless cards, which in a real pack are seven Mountains. A rule that
            // counted them would call every pack in every set colorless.
            for (int at = 0; at < 7; at++) {
                cards.put(UUID.randomUUID(), "");
            }
            BoosterConfig config = new BoosterConfig("j25", "jumpstart",
                    Map.of("burning1", sheetOf("burning1", cards)),
                    List.of(new BoosterVariant("burning1", 1, Map.of("burning1", 20))));
            assertThat(BoosterColors.colorOf(config, config.variants().get(0)))
                    .isEqualTo('R');
        }

        @Test
        @DisplayName("answers nothing for a pack with no colored card in it")
        void colorless() {
            Map<UUID, String> cards = new LinkedHashMap<>();
            cards.put(UUID.randomUUID(), "");
            BoosterConfig config = new BoosterConfig("x", "y",
                    Map.of("s", sheetOf("s", cards)),
                    List.of(new BoosterVariant("s", 1, Map.of("s", 1))));
            assertThat(BoosterColors.colorOf(config, config.variants().get(0))).isNull();
        }

        @Test
        @DisplayName("breaks a tie by the order WUBRG is written in, not by map order")
        void tiesAreStable() {
            Map<UUID, String> cards = new LinkedHashMap<>();
            cards.put(UUID.randomUUID(), "G");
            cards.put(UUID.randomUUID(), "U");
            BoosterConfig config = new BoosterConfig("x", "y",
                    Map.of("s", sheetOf("s", cards)),
                    List.of(new BoosterVariant("s", 1, Map.of("s", 2))));
            // Blue comes before green in WUBRG, whichever order the map happened to hold.
            assertThat(BoosterColors.colorOf(config, config.variants().get(0))).isEqualTo('U');
        }

        @Test
        @DisplayName("takes nothing at all without falling over")
        void nothing() {
            assertThat(BoosterColors.colorOf(null, null)).isNull();
            assertThat(BoosterColors.inColor(null, 'R')).isEmpty();
            assertThat(BoosterColors.coversEveryColor(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("picking out one color's packs")
    class PickingOut {

        private BoosterConfig fiveColors() {
            Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
            java.util.List<BoosterVariant> variants = new java.util.ArrayList<>();
            for (char color : "WUBRG".toCharArray()) {
                String name = "sheet" + color;
                Map<UUID, String> cards = new LinkedHashMap<>();
                cards.put(UUID.randomUUID(), String.valueOf(color));
                sheets.put(name, sheetOf(name, cards));
                variants.add(new BoosterVariant(name, 1, Map.of(name, 1)));
            }
            return new BoosterConfig("x", "jumpstart", sheets, variants);
        }

        @Test
        @DisplayName("keeps only that color's arrangements")
        void onlyThatColor() {
            BoosterConfig config = fiveColors();
            assertThat(BoosterColors.inColor(config, 'B')).hasSize(1);
            assertThat(BoosterColors.inColor(config, 'B').get(0).name()).isEqualTo("sheetB");
        }

        @Test
        @DisplayName("takes a lower case letter too, because a config file is written by hand")
        void eitherCase() {
            assertThat(BoosterColors.inColor(fiveColors(), 'b'))
                    .isEqualTo(BoosterColors.inColor(fiveColors(), 'B'));
        }

        @Test
        @DisplayName("says whether a product covers all five, so a screen can refuse to offer it")
        void coverage() {
            assertThat(BoosterColors.coversEveryColor(fiveColors())).isTrue();
            Map<Character, Integer> counted = BoosterColors.countByColor(fiveColors());
            assertThat(counted).containsOnlyKeys('W', 'U', 'B', 'R', 'G');
            assertThat(counted.values()).allMatch(many -> many == 1);
        }
    }

    /**
     * The claim this feature actually rests on, against the file it rests on.
     * <p>Foundations Jumpstart is sold one color at a time, and the whole "pick two colors"
     * screen assumes that every color has packs to pick from. Nothing in the collation says
     * so, so it is counted - and counting is only worth trusting if it has been run against the
     * real thing. Skipped unless the file is there, exactly as {@link MtgjsonRealSetTest} is,
     * and for the same reason: four megabytes of somebody else's data has no business here.
     *
     * <pre>
     * curl -O https://mtgjson.com/api/v5/J25.json
     * GATHERING_MTGJSON_DIR=$PWD ./gradlew :core:test --tests '*BoosterColorsTest'
     * </pre>
     */
    @Test
    @DisplayName("Foundations Jumpstart has packs in every color, counted off the real file")
    void foundationsJumpstartCoversEveryColor() throws Exception {
        String where = System.getenv("GATHERING_MTGJSON_DIR");
        Assumptions.assumeTrue(where != null && !where.isBlank(),
                "set GATHERING_MTGJSON_DIR to a directory holding J25.json to run this");
        Path file = Path.of(where).resolve("J25.json");
        Assumptions.assumeTrue(Files.isRegularFile(file), file + " is not there");

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        MtgjsonCollation.Reading reading = MtgjsonCollation.read(json);
        BoosterConfig jumpstart = reading.pack("jumpstart");
        assertThat(jumpstart).as("J25 publishes a jumpstart product").isNotNull();

        Map<Character, Integer> counted = BoosterColors.countByColor(jumpstart);
        System.out.println("J25 jumpstart packs by color: " + counted);
        assertThat(BoosterColors.coversEveryColor(jumpstart))
                .as("every color has at least one Jumpstart pack: %s", counted)
                .isTrue();
        // Nine or ten of each, which is what the product is. Asserted as a floor rather than
        // an exact count: a later printing that adds a theme should not fail this.
        assertThat(counted.values()).allMatch(many -> many >= 5);
    }
}
