package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where the Archive Pack may come from.
 *
 * <p>Worth pinning because both halves are wrong in a way nothing else would catch: a table
 * that stops matching drops nothing and says nothing, and a table that starts matching too
 * widely turns the one path to a server's long tail into a zombie drop.
 */
class ArchiveDropsTest {

    @Test
    @DisplayName("the bosses drop it, and ordinary mobs do not")
    void onlyTheBosses() {
        assertThat(ArchiveDrops.of("minecraft:entities/ender_dragon"))
                .contains(ArchiveDrops.BOSS);
        assertThat(ArchiveDrops.of("minecraft:entities/warden")).contains(ArchiveDrops.BOSS);
        assertThat(ArchiveDrops.of("minecraft:entities/zombie")).isEmpty();
        assertThat(ArchiveDrops.of("minecraft:entities/villager")).isEmpty();
    }

    @Test
    @DisplayName("treasure out of the sea, but not the junk and not the fish")
    void onlyTreasureFishing() {
        assertThat(ArchiveDrops.of("minecraft:gameplay/fishing/treasure"))
                .contains(ArchiveDrops.TREASURE);
        assertThat(ArchiveDrops.of("minecraft:gameplay/fishing/junk")).isEmpty();
        assertThat(ArchiveDrops.of("minecraft:gameplay/fishing/fish")).isEmpty();
    }

    @Test
    @DisplayName("the chests worth an expedition, and not the ones on the way")
    void onlyTheGoodChests() {
        assertThat(ArchiveDrops.of("minecraft:chests/ancient_city"))
                .contains(ArchiveDrops.EXPEDITION);
        assertThat(ArchiveDrops.of("minecraft:chests/end_city_treasure"))
                .contains(ArchiveDrops.EXPEDITION);
        assertThat(ArchiveDrops.of("minecraft:chests/village/village_plains_house")).isEmpty();
        assertThat(ArchiveDrops.of("minecraft:chests/abandoned_mineshaft")).isEmpty();
    }

    @Test
    @DisplayName("nothing outside Minecraft's own tables")
    void onlyVanillaTables() {
        assertThat(ArchiveDrops.of("othermod:chests/ancient_city")).isEmpty();
        assertThat(ArchiveDrops.of("gathering:chests/card_shop")).isEmpty();
        assertThat(ArchiveDrops.of(null)).isEmpty();
        assertThat(ArchiveDrops.of("")).isEmpty();
    }

    @Test
    @DisplayName("a boss is the generous one and the sea is the slow one")
    void theOddsAreOrdered() {
        assertThat(ArchiveDrops.BOSS.oneIn()).isLessThan(ArchiveDrops.EXPEDITION.oneIn());
        assertThat(ArchiveDrops.EXPEDITION.oneIn()).isLessThan(ArchiveDrops.TREASURE.oneIn());
    }
}
