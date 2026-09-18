package dev.gathering.core.sealed;

/**
 * How fast the world gives one player things, and what happens when they go faster than that.
 * <p>Every rate in {@link LootSource}, {@link CoinDrops} and {@link ArchiveDrops} is set against an
 * hour of somebody playing: a village walked through, a couple of chests worth a trip, the mobs on
 * the way. None of it is set against somebody who has built a machine. And the machines exist - a
 * wither is three farmed skulls, a warden is a shrieker triggered again, the ender dragon is four
 * crystals, an ominous vault opens on a bottle, and a fishing rod can be held down by a weight. At
 * the odds those sources run at, any one of them is the rarest thing in the mod on tap.
 * <p>The answer is not thinner odds. Thinner odds punish the player who is out exploring - which is
 * the player the odds were raised for in the first place, after the owner's report that packs were
 * too rare to find - and a farm simply runs longer. What separates the two is not what they do but
 * how long they keep doing it, so that is what this measures.
 * <p>A leaky bucket per player per kind of find. Every find fills it by one and it drains at the
 * pace below. While it is under the brim nothing is thinned at all: an afternoon's exploring, an end
 * city raid, a first evening on a server all sit inside the burst and never notice this exists. Past
 * the brim each further find has to be rolled for again, against odds that get longer the further
 * past it the player is - so a machine's first few minutes pay, and then it settles onto the pace
 * and stays there however long it runs. Nothing is ever refused outright: a faucet that goes dry
 * reads as the mod being broken, and one that slows reads as the world having been picked over.
 * <p>Pure: the counts in, the odds out.
 */
public enum FindingPace {

    /**
     * Ordinary boosters. The pace is well above what an hour of exploring actually gives, which is
     * about seven - somebody having a very good hour should never meet this.
     */
    PACKS(12, 24),

    /** Mana Coins, at the same shape: found beside the packs and worth about the same. */
    COINS(12, 24),

    /**
     * Archive Packs, which are the one thing a player cannot buy at any price - so this is where a
     * machine would hurt most and where the pace is tightest.
     * <p>Half an hour's worth is still generous beside what exploring gives: two chests worth a trip
     * an hour, at one archive pack in thirty, is one about every fifteen hours. The brim lets a run
     * of luck straight through and the pace is what a machine settles onto.
     */
    ARCHIVE(0.5, 3);

    private final double anHour;
    private final int burst;

    FindingPace(double anHour, int burst) {
        this.anHour = anHour;
        this.burst = burst;
    }

    /** How many of these an hour of play is expected to hand one player before anything thins. */
    public double anHour() {
        return anHour;
    }

    /** How far ahead of that pace somebody may run with nothing thinned at all. */
    public int burst() {
        return burst;
    }

    /** How much of a full bucket drains away in this many milliseconds. */
    public double drainedIn(long millis) {
        return millis <= 0 ? 0.0 : anHour * (millis / 3_600_000.0);
    }

    /**
     * One in this many finds arrives, for a player who has already had this many.
     * <p>One - everything arrives - up to the brim, and then longer odds the further past it they
     * are, so the rate a machine settles at is the pace rather than the machine's own.
     *
     * @param had how full the bucket is: finds taken, less what has drained since
     */
    public int oneIn(double had) {
        double past = had - burst;
        if (past <= 0) {
            return 1;
        }
        // Quadrupling, not counting up. Counting up is far too gentle to be a limit: the odds only
        // match the drain once they are as long as the drain is slow, so at one-in-two-plus-n a
        // machine banks three hundred finds on the way to settling down, which is a fortnight of
        // play handed over in an afternoon. Steep matters because the climb itself is what a
        // machine is paid: every step of it hands over one more find, so the fewer steps there are
        // between everything and nothing, the less running all night is worth.
        return (int) Math.min(LONGEST_ODDS, Math.pow(4, Math.floor(past)));
    }

    /**
     * The longest these odds get, so the arithmetic stays in an int.
     * <p>Ten steps past the brim, which is longer than the slowest pace here needs: a bucket cannot
     * outrun its own drain, so this is a guard on the arithmetic rather than a rule anybody meets.
     */
    private static final int LONGEST_ODDS = 1 << 20;

    /**
     * The bucket after this long has passed, never below empty.
     *
     * @param had   how full it was when it was last looked at
     * @param since how long ago that was, in milliseconds
     */
    public double drained(double had, long since) {
        return Math.max(0.0, had - drainedIn(since));
    }
}
