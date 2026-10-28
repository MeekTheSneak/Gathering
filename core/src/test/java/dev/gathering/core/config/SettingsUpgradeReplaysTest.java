package dev.gathering.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A default that moved for a privacy reason is carried like every other default.
 *
 * <p>Between settings version one and version two, {@code modes.replays} stopped defaulting to
 * {@code "public"} and started defaulting to {@code "participants"} - because a replay shows every
 * hand and every library in order, none of which was public at the table. {@link SettingsUpgrade}
 * exists precisely so a default this project changes afterwards still reaches a server that has
 * already played a session, and its version-two list carries the six collection defaults that moved
 * in the same release. It does not carry this one.
 *
 * <p>So every server whose {@code gathering-server.toml} predates the change keeps
 * {@code replays = "public"} - the value version one wrote, not one anybody chose - and goes on
 * letting any player on the server watch any finished casual game back, hidden information and all.
 * The upgrade even rewrote and re-stamped that file, saying in the log that nothing had to move.
 */
@DisplayName("The settings upgrade")
class SettingsUpgradeReplaysTest {

    /** The lines version one's own {@code defaultFileText()} wrote for these two sections. */
    private static final String VERSION_ONE_FILE = """
            # Gathering server settings.

            [modes]
            import_enabled = true
            collection_enabled = true
            replays = "public"

            [collection]
            sealed_price_item = "minecraft:emerald"
            village_shop_weight = 8
            """;

    @Test
    @DisplayName("a default that moved for a privacy reason is carried like every other default")
    void replaysMovesWithTheRestOfTheDefaults() throws TomlException {
        assertThat(SettingsUpgrade.versionOf(VERSION_ONE_FILE)).isEqualTo(1);
        // The value in the file is exactly what version one wrote, so it is a default and not a choice.
        assertThat(SettingsUpgrade.valueOf(VERSION_ONE_FILE, "modes.replays")).isEqualTo("\"public\"");

        SettingsUpgrade.Upgraded upgraded = SettingsUpgrade.upgrade(VERSION_ONE_FILE, 1);

        // The collection defaults beside it do move, which is what makes the omission an omission
        // rather than a decision not to upgrade this file at all.
        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "collection.village_shop_weight"))
                .isEqualTo("20");

        assertThat(SettingsUpgrade.since(1))
                .as("version two's change list should name every default that moved")
                .anyMatch(change -> change.key().equals("modes.replays"));
        assertThat(SettingsUpgrade.valueOf(upgraded.text(), "modes.replays"))
                .as("an old file still sitting at the old default should reach this version's")
                .isEqualTo("\"participants\"");
        assertThat(GatheringConfig.read(Toml.read(upgraded.text())).modes().replays())
                .isEqualTo(GatheringConfig.Replays.PARTICIPANTS);
    }

    @Test
    @DisplayName("writes the version this code upgrades to, from the one place that says it")
    void thefreshFileSaysTheVersionThisCodeIsOn() {
        // Two places to keep in step is one place to forget. A fresh file stamped with an older
        // version than SettingsUpgrade knows would be upgraded on its very first read.
        assertThat(SettingsUpgrade.versionOf(GatheringConfig.defaultFileText()))
                .isEqualTo(SettingsUpgrade.VERSION);
    }
}
