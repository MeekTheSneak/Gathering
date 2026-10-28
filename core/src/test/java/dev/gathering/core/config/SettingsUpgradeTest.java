package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsUpgradeTest {

    private static final String OLD_FILE = """
            # A settings file from before the coin.

            [collection]
            sealed_price_item = "minecraft:emerald"
            sealed_price_block = "minecraft:emerald_block"
            sealed_price_block_worth = 9
            village_shop_weight = 8
            """;

    @Test
    @DisplayName("a file from before a default changed is brought up to date")
    void oldDefaultsMove() {
        assertThat(SettingsUpgrade.versionOf(OLD_FILE)).isEqualTo(1);

        SettingsUpgrade.Upgraded upgraded = SettingsUpgrade.upgrade(OLD_FILE, 1);

        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "collection.sealed_price_item"))
                .isEqualTo("\"gathering:mana_coin\"");
        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "collection.village_shop_weight")).isEqualTo("20");
        assertThat(upgraded.changed()).hasSize(4);
        assertThat(upgraded.changed()).anyMatch(said -> said.startsWith("collection.sealed_price_item"));
        assertThat(SettingsUpgrade.versionOf(upgraded.text())).isEqualTo(SettingsUpgrade.VERSION);
    }

    @Test
    @DisplayName("a value somebody chose is left exactly as they wrote it")
    void chosenValuesStay() {
        String chosen = OLD_FILE.replace("village_shop_weight = 8", "village_shop_weight = 3")
                .replace("sealed_price_item = \"minecraft:emerald\"", "sealed_price_item = \"minecraft:diamond\"");

        SettingsUpgrade.Upgraded upgraded = SettingsUpgrade.upgrade(chosen, 1);

        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "collection.village_shop_weight")).isEqualTo("3");
        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "collection.sealed_price_item"))
                .isEqualTo("\"minecraft:diamond\"");
        assertThat(upgraded.changed()).noneMatch(said -> said.startsWith("collection.village_shop_weight"));
    }

    @Test
    @DisplayName("a file already at this version is not touched")
    void currentFilesAreLeftAlone() {
        String current = SettingsUpgrade.upgrade(OLD_FILE, 1).text();

        assertThat(SettingsUpgrade.versionOf(current)).isEqualTo(SettingsUpgrade.VERSION);
        assertThat(SettingsUpgrade.upgrade(current, SettingsUpgrade.VERSION).changed()).isEmpty();
    }

    @Test
    @DisplayName("the file this version writes says it is this version")
    void theShippedFileSaysSo() {
        assertThat(SettingsUpgrade.versionOf(GatheringConfig.defaultFileText()))
                .isEqualTo(SettingsUpgrade.VERSION);
    }
}
