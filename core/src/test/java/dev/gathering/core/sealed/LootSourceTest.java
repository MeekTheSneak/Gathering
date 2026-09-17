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
    @DisplayName("a mob worth fighting drops packs and nothing else does")
    void mobsAreNamedRatherThanMatched() {
        assertThat(LootSource.of("minecraft:entities/zombie")).contains(LootSource.MOBS);
        assertThat(LootSource.of("minecraft:entities/piglin_brute")).contains(LootSource.MOBS);
        // A wool farm is not a booster faucet.
        assertThat(LootSource.of("minecraft:entities/sheep")).isEmpty();
        assertThat(LootSource.of("minecraft:entities/cow")).isEmpty();
        assertThat(LootSource.of("minecraft:entities/villager")).isEmpty();
        // The four the world is built around drop the archive pack instead, which is rarer
        // and better; an ordinary booster out of the ender dragon would be an anticlimax.
        assertThat(LootSource.of("minecraft:entities/ender_dragon")).isEmpty();
        assertThat(LootSource.of("minecraft:entities/wither")).isEmpty();
        assertThat(LootSource.of("minecraft:entities/warden")).isEmpty();
        assertThat(LootSource.of("minecraft:entities/elder_guardian")).isEmpty();
        assertThat(LootSource.of("minecraft:blocks/stone")).isEmpty();
    }

    @Test
    @DisplayName("only mobs need a player to have done the killing")
    void onlyMobsNeedAPlayer() {
        assertThat(LootSource.MOBS.needsAPlayer()).isTrue();
        assertThat(LootSource.STRUCTURES.needsAPlayer()).isFalse();
        assertThat(LootSource.FISHING.needsAPlayer()).isFalse();
        assertThat(LootSource.DIGGING.needsAPlayer()).isFalse();
    }

    @Test
    @DisplayName("a chest worth an expedition is never worse odds than an ordinary one")
    void betterChestsAreNeverWorse() {
        for (LootSource source : LootSource.values()) {
            assertThat(source.oneIn(LootRichness.RICH)).as(source.configName())
                    .isLessThanOrEqualTo(source.oneIn(LootRichness.PLAIN));
            assertThat(source.oneIn(null)).as(source.configName())
                    .isEqualTo(source.oneIn());
        }
    }

    @Test
    @DisplayName("an ordinary booster is common in chests, and a mob drop is not")
    void chestsAreCommonAndMobsAreNot() {
        // The owner's report, as numbers: a pack in one chest in eight was a pack somebody
        // explored a whole evening without seeing. About half of ordinary chests now, and
        // every chest at the end of something.
        assertThat(LootSource.STRUCTURES.oneIn(LootRichness.PLAIN)).isEqualTo(2);
        assertThat(LootSource.STRUCTURES.oneIn(LootRichness.RICH)).isEqualTo(1);
        // And a mob is the one source that can be automated, so it is the long one.
        assertThat(LootSource.MOBS.oneIn()).isGreaterThan(100);
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
