package dev.gathering.core.sealed;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an hour of play is worth, in packs and in coins.
 * <p>The numbers in {@link LootSource}, {@link CoinDrops} and {@link BoosterOdds} are each
 * defensible on their own and say nothing on their own about whether the game feels right. The
 * owner's report was about the feeling - "packs are much too rare to find" - so this is the
 * arithmetic that turns the numbers back into it, and the band is what the design brief and
 * the config file both promise a server owner.
 * <p>An hour of ordinary play, as the profile below: a village walked through, a mineshaft, a
 * shipwreck, two chests somebody made a trip for, and the mobs you fight on the way. No
 * fishing and no brushing, because neither is what an hour of exploring is.
 * <p>Deliberately expectations rather than a simulation. A simulation needs a generator and a
 * seed and would be asserting against its own sampling noise; the expected value is the thing
 * being decided, and it is exact.
 */
class LootYieldTest {

    /**
     * Ordinary loot chests opened in an hour of exploring.
     * <p>A plains village is eight or nine chests on its own, a mineshaft is a few more, and a
     * shipwreck is three. Ten is conservative for somebody who is out looking.
     */
    static final int PLAIN_CHESTS_AN_HOUR = 10;

    /** And the ones somebody made a trip for: a bastion, an end city, an ancient city. */
    static final int RICH_CHESTS_AN_HOUR = 2;

    /** Hostile mobs killed in a busy hour of caving, at a pace nobody sustains all evening. */
    static final int MOB_KILLS_AN_HOUR = 60;

    /** How many boosters a display box holds, which is what a player saves coins for. */
    private static final int BOOSTERS_IN_A_DISPLAY_BOX = 36;

    @Test
    @DisplayName("an hour of exploring is a handful of boosters, not one every other evening")
    void anHourIsAHandfulOfPacks() {
        double fromChests = PLAIN_CHESTS_AN_HOUR
                        / (double) LootSource.STRUCTURES.oneIn(LootRichness.PLAIN)
                + RICH_CHESTS_AN_HOUR
                        / (double) LootSource.STRUCTURES.oneIn(LootRichness.RICH);
        double fromMobs = MOB_KILLS_AN_HOUR / (double) LootSource.MOBS.oneIn();

        // The owner's report, as a number to hold onto. At the old one-chest-in-eight this
        // came to 1.5 packs an hour, which is an evening of exploring for a booster and a
        // half - and is why the shop, and therefore emeralds, was the only real way in.
        assertThat(fromChests + fromMobs).as("boosters an hour").isBetween(4.0, 12.0);

        // And the mob drop stays a garnish rather than the meal. It is the one source that
        // can be automated, so it must never be the one that pays best.
        assertThat(fromMobs).as("boosters an hour off mobs").isLessThan(fromChests / 5);
    }

    @Test
    @DisplayName("a rare pack is a few hours apart, and mostly out of somewhere you went")
    void aRarePackIsAnEveningApart() {
        double plain = PLAIN_CHESTS_AN_HOUR
                / (double) LootSource.STRUCTURES.oneIn(LootRichness.PLAIN)
                * shareOfRare(LootRichness.PLAIN);
        double earned = RICH_CHESTS_AN_HOUR
                / (double) LootSource.STRUCTURES.oneIn(LootRichness.RICH)
                * shareOfRare(LootRichness.RICH);

        assertThat(1 / (plain + earned)).as("hours between rare packs").isBetween(2.0, 12.0);
        // "Only found occasionally or on special circumstances": most of the collector
        // boosters a player ever sees came out of somewhere they made a trip to.
        assertThat(earned).as("out of a chest worth an expedition").isGreaterThan(plain * 3);
    }

    @Test
    @DisplayName("a display box is an expedition's worth of coins, and a booster is minutes")
    void whatTheCoinsBuy() {
        double coinsAnHour = PLAIN_CHESTS_AN_HOUR * CoinDrops.ORDINARY.perChest()
                + RICH_CHESTS_AN_HOUR * CoinDrops.EXPEDITION.perChest();

        // Priced in the shop's own unit, which a server owner sets: one coin a booster as it
        // ships. What matters here is the ratio between what an hour finds and what a box
        // costs, and that is what the owner asked for - a box should be an expedition.
        assertThat(BOOSTERS_IN_A_DISPLAY_BOX / coinsAnHour).as("hours for a display box")
                .isBetween(3.0, 6.0);
        // And one specific booster is a short walk, because that is the whole point of the
        // shop: you found packs while you were out, and you come home able to buy the one
        // thing you were actually missing.
        assertThat(60 / coinsAnHour).as("minutes for one booster").isLessThan(12.0);
    }

    @Property(tries = 300)
    @Label("the shop never outruns the world, however somebody explores")
    void buyingNeverOutrunsFinding(
            @ForAll @IntRange(min = 1, max = 60) int plainChests,
            @ForAll @IntRange(min = 0, max = 12) int richChests) {
        double packs = plainChests / (double) LootSource.STRUCTURES.oneIn(LootRichness.PLAIN)
                + richChests / (double) LootSource.STRUCTURES.oneIn(LootRichness.RICH);
        double coins = plainChests * CoinDrops.ORDINARY.perChest()
                + richChests * CoinDrops.EXPEDITION.perChest();

        // The band, and the whole shape of the economy in one line: a coin buys a booster, so
        // this is what a player can buy against what they found. Never less than about three
        // quarters, so the shop is worth walking into, and never more than double, so the
        // world stays the main way in. It holds at both extremes - nothing but village chests,
        // and nothing but end cities - which is the point of asserting it over the mix rather
        // than over the one profile an hour of play happens to be.
        assertThat(coins / packs).as("bought against found").isBetween(0.7, 2.0);
    }

    /** How much of a pack roll out of a chest this good lands on one of the rare kinds. */
    private static double shareOfRare(LootRichness richness) {
        var kinds = java.util.List.of("play", "collector", "collector-sample");
        var weights = BoosterOdds.weightsFor(kinds, richness);
        return weights.get("collector") / (double) BoosterOdds.totalOf(weights);
    }
}
