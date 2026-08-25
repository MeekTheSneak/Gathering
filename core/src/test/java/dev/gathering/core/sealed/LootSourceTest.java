package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which of Minecraft's loot tables a pack may turn up in. */
class LootSourceTest {

    @Test
    @DisplayName("the three sources are the tables they are about")
    void tablesMapToSources() {
        assertThat(LootSource.of("minecraft:gameplay/fishing/treasure"))
                .contains(LootSource.FISHING);
        assertThat(LootSource.of("minecraft:chests/simple_dungeon"))
                .contains(LootSource.STRUCTURES);
        assertThat(LootSource.of("minecraft:chests/village/village_weaponsmith"))
                .contains(LootSource.STRUCTURES);
        assertThat(LootSource.of("minecraft:archaeology/trail_ruins_rare"))
                .contains(LootSource.DIGGING);
    }

    @Test
    @DisplayName("fishing up a fish is not fishing up treasure")
    void onlyTreasureCounts() {
        // The fish and junk tables are most of what fishing rolls, and a pack in them would
        // be a pack every few casts rather than a pack you remember.
        assertThat(LootSource.of("minecraft:gameplay/fishing/fish")).isEmpty();
        assertThat(LootSource.of("minecraft:gameplay/fishing/junk")).isEmpty();
        assertThat(LootSource.of("minecraft:gameplay/fishing")).isEmpty();
    }

    @Test
    @DisplayName("somebody else's loot table is somebody else's")
    void otherModsAreLeftAlone() {
        // A pack falling out of another mod's dungeon is a surprise nobody asked for.
        assertThat(LootSource.of("somemod:chests/dungeon")).isEmpty();
        assertThat(LootSource.of("gathering:chests/anything")).isEmpty();
        assertThat(LootSource.of("")).isEmpty();
        assertThat(LootSource.of(null)).isEmpty();
    }

    @Test
    @DisplayName("entity and block drops are not chests")
    void ordinaryDropsAreNotLoot() {
        assertThat(LootSource.of("minecraft:entities/zombie")).isEmpty();
        assertThat(LootSource.of("minecraft:blocks/stone")).isEmpty();
    }

    @Test
    @DisplayName("a config names a source by the name the config file uses")
    void configNamesResolve() {
        assertThat(LootSource.named("fishing")).contains(LootSource.FISHING);
        assertThat(LootSource.named(" STRUCTURES ")).contains(LootSource.STRUCTURES);
        assertThat(LootSource.named("archaeology")).contains(LootSource.DIGGING);
        assertThat(LootSource.named("trading")).isEmpty();
        assertThat(LootSource.named("")).isEmpty();
        assertThat(LootSource.named(null)).isEmpty();
    }

    @Test
    @DisplayName("every source is long odds, and none of them is impossible")
    void theOddsAreLong() {
        for (LootSource source : LootSource.values()) {
            assertThat(source.oneIn()).as(source.configName()).isGreaterThan(1);
            assertThat(source.configName()).isNotBlank();
        }
    }
}
