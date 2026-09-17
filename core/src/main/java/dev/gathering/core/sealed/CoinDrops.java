package dev.gathering.core.sealed;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a Mana Coin is found, and how few.
 * <p>The shop used to price in emeralds, and emeralds are farmed rather than found: a player
 * with a trading hall could buy sealed product for ever without leaving the village, which made
 * the shop the cheapest path to a collection and exploring an optional flourish. A coin cannot
 * be farmed. It is in chests, in the world, at the far end of somewhere - so the way to afford
 * the box you actually want is to go and look for it, which is the same act that turns up the
 * packs.
 * <p><b>Chests only.</b> Not off mobs, not out of the sea, not brushed out of the ground. A
 * currency that drops from anything repeatable is a currency with a farm behind it, and the
 * three sources that are not a chest are all repeatable without moving.
 * <p>The rate, and what it buys: an ordinary chest pays one time in five and pays one to three
 * coins; a chest at the end of something pays one time in two and pays two to five. So an
 * ordinary chest is thinner in coins than it is in packs and an expedition chest is not, which
 * is the entire argument for going on one - an end city raid is a dozen of the second kind and
 * about twenty coins, and two of those buys a display box outright.
 * <p>Mixed through an ordinary hour of exploring it comes to seven or eight coins, against a
 * booster costing one as the shop ships. A display box of thirty-six is five hours or so; a
 * single booster of a set somebody specifically wants is minutes. That is the shape the owner
 * asked for: you find packs while you are out, and you come home able to buy the one thing you
 * were actually missing.
 * <p>Which chests are which is a rule rather than a setting, for the reason every other loot
 * rule here is - and it is {@link LootRichness}'s list rather than a second one beside it, so
 * a structure added to one is added to both.
 * <p>Pure.
 */
public enum CoinDrops {

    /** Most chests. A coin or two, one time in five - thinner than the pack beside it. */
    ORDINARY(5, 1, 3),

    /** The chests people build expeditions around. Worth the trip. */
    EXPEDITION(2, 2, 5);

    private final int oneIn;
    private final int fewest;
    private final int most;

    CoinDrops(int oneIn, int fewest, int most) {
        this.oneIn = oneIn;
        this.fewest = fewest;
        this.most = most;
    }

    /** One in this many of these chests pays out at all. */
    public int oneIn() {
        return oneIn;
    }

    /** The fewest coins a chest that pays out pays. */
    public int fewest() {
        return fewest;
    }

    /** And the most. */
    public int most() {
        return most;
    }

    /** How many different amounts a paying chest can give, which is what a roll is taken out of. */
    public int spread() {
        return most - fewest + 1;
    }

    /**
     * How many coins a roll means.
     *
     * @param roll a number from zero up to, but not including, {@link #spread()}
     */
    public int count(int roll) {
        return fewest + Math.floorMod(roll, spread());
    }

    /** What one chest is worth on average, in coins, counting the times it pays nothing. */
    public double perChest() {
        return (fewest + most) / 2.0 / oneIn;
    }

    /**
     * Whether coins can be found in this loot table, and at which rate.
     * <p>Minecraft's own chests only. The mod's own card-shop chest is not here: what is in a
     * shop's stock chest is that table's own business and is written in it, rather than being
     * a world drop that happens to land in a building.
     *
     * @param tableId a loot table's full id, namespace and all
     */
    public static Optional<CoinDrops> of(String tableId) {
        String id = tableId == null ? "" : tableId.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith("minecraft:")) {
            return Optional.empty();
        }
        if (!id.substring("minecraft:".length()).startsWith("chests/")) {
            return Optional.empty();
        }
        return Optional.of(LootRichness.of(id).isRich() ? EXPEDITION : ORDINARY);
    }
}
