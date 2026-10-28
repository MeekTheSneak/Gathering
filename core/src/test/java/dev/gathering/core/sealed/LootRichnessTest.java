package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which chests are the ones worth finding. */
class LootRichnessTest {

    @Test
    @DisplayName("the chests at the end of something")
    void theGoodOnes() {
        // Behind a boss, a dimension, a raid or a dive - somewhere somebody went on purpose.
        assertThat(LootRichness.of("minecraft:chests/end_city_treasure")).isEqualTo(LootRichness.RICH);
        assertThat(LootRichness.of("minecraft:chests/bastion_treasure")).isEqualTo(LootRichness.RICH);
        assertThat(LootRichness.of("minecraft:chests/ancient_city")).isEqualTo(LootRichness.RICH);
        assertThat(LootRichness.of("minecraft:chests/woodland_mansion")).isEqualTo(LootRichness.RICH);
        assertThat(LootRichness.of("minecraft:chests/buried_treasure")).isEqualTo(LootRichness.RICH);
        assertThat(LootRichness.of("minecraft:archaeology/trail_ruins_rare")).isEqualTo(LootRichness.RICH);
    }

    @Test
    @DisplayName("and everything else is an ordinary find")
    void theOrdinaryOnes() {
        assertThat(LootRichness.of("minecraft:chests/village/village_weaponsmith"))
                .isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of("minecraft:chests/simple_dungeon")).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of("minecraft:gameplay/fishing/treasure")).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of("minecraft:archaeology/desert_pyramid")).isEqualTo(LootRichness.PLAIN);
    }

    @Test
    @DisplayName("a chest this does not know is ordinary rather than a failure")
    void unknownTablesAreOrdinary() {
        // The safe direction to be wrong in: a chest a later Minecraft adds is an ordinary
        // find until somebody says otherwise.
        assertThat(LootRichness.of("minecraft:chests/something_new")).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of("somemod:chests/end_city_treasure")).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of("")).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.of(null)).isEqualTo(LootRichness.PLAIN);
        assertThat(LootRichness.PLAIN.isRich()).isFalse();
        assertThat(LootRichness.RICH.isRich()).isTrue();
    }

    @Test
    @DisplayName("a good chest is still a chest, so it is still a structure source")
    void richChestsAreStillStructures() {
        // The two rules are about different things, and a table that answers one must still
        // answer the other - otherwise an end city is rich and drops nothing.
        for (String table : new String[] {
                "minecraft:chests/end_city_treasure", "minecraft:chests/ancient_city",
                "minecraft:chests/buried_treasure"}) {
            assertThat(LootSource.of(table)).as(table).contains(LootSource.STRUCTURES);
        }
        assertThat(LootSource.of("minecraft:archaeology/trail_ruins_rare"))
                .contains(LootSource.DIGGING);
    }
}
