package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Where a Mana Coin is found, and how few. */
class CoinDropsTest {

    @Test
    @DisplayName("coins are in chests and in nothing else")
    void onlyChests() {
        assertThat(CoinDrops.of("minecraft:chests/village/village_weaponsmith"))
                .contains(CoinDrops.ORDINARY);
        assertThat(CoinDrops.of("minecraft:chests/simple_dungeon"))
                .contains(CoinDrops.ORDINARY);

        // The three sources that are not a chest are all repeatable without moving, and a
        // currency with a farm behind it is not a currency.
        assertThat(CoinDrops.of("minecraft:gameplay/fishing/treasure")).isEmpty();
        assertThat(CoinDrops.of("minecraft:archaeology/trail_ruins_rare")).isEmpty();
        assertThat(CoinDrops.of("minecraft:entities/zombie")).isEmpty();
        assertThat(CoinDrops.of("minecraft:blocks/stone")).isEmpty();

        // Somebody else's chest is somebody else's, and the mod's own shop chest says what
        // is in it in its own loot table.
        assertThat(CoinDrops.of("somemod:chests/dungeon")).isEmpty();
        assertThat(CoinDrops.of("gathering:chests/card_shop")).isEmpty();
        assertThat(CoinDrops.of("")).isEmpty();
        assertThat(CoinDrops.of(null)).isEmpty();
    }

    @Test
    @DisplayName("a chest worth an expedition is worth more than an ordinary one")
    void expeditionChestsPayBetter() {
        assertThat(CoinDrops.of("minecraft:chests/ancient_city"))
                .contains(CoinDrops.EXPEDITION);
        assertThat(CoinDrops.of("minecraft:chests/end_city_treasure"))
                .contains(CoinDrops.EXPEDITION);

        // It used to pay more often as well as more. Both chests now pay at the odds of the pack
        // beside them, which is what makes a coin as common as a pack and no commoner, so what is
        // left to defend is the part that was always the point: going somewhere is worth more.
        assertThat(CoinDrops.EXPEDITION.oneIn())
                .as("an expedition chest must never pay less often than an ordinary one")
                .isLessThanOrEqualTo(CoinDrops.ORDINARY.oneIn());
        assertThat(CoinDrops.EXPEDITION.most()).isGreaterThan(CoinDrops.ORDINARY.most());
        assertThat(CoinDrops.EXPEDITION.perChest()).isGreaterThan(CoinDrops.ORDINARY.perChest());
    }

    @Test
    @DisplayName("pays a coin at a time rather than a handful")
    void acoinAtATime() {
        // What the owner asked for: a chest handing over one pack and four coins reads as coins
        // being the ordinary thing and the pack the rare one, when the coin is meant to be the find.
        assertThat(CoinDrops.ORDINARY.most()).isEqualTo(1);
        assertThat(CoinDrops.EXPEDITION.most()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a booster box is a real expedition's worth of looking")
    void aBoxIsAnAfternoon() {
        // The sentence the config file and the design brief both make. An hour of exploring
        // is about a dozen loot chests, two of them worth going to; a display box is
        // thirty-six boosters and a booster is one coin.
        double anHour = LootYieldTest.PLAIN_CHESTS_AN_HOUR * CoinDrops.ORDINARY.perChest()
                + LootYieldTest.RICH_CHESTS_AN_HOUR * CoinDrops.EXPEDITION.perChest();

        assertThat(anHour).as("coins an hour").isBetween(6.0, 12.0);
        assertThat(36 / anHour).as("hours for a display box").isBetween(3.0, 6.0);
    }

    @Property(tries = 200)
    @Label("however a roll lands, a paying chest pays between the fewest and the most")
    void everyRollIsInsideTheBand(@ForAll @IntRange(min = -50, max = 50) int roll) {
        for (CoinDrops where : CoinDrops.values()) {
            assertThat(where.count(roll)).as(where.name())
                    .isBetween(where.fewest(), where.most());
            assertThat(where.spread()).isEqualTo(where.most() - where.fewest() + 1);
        }
    }
}
