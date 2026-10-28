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
 * <p><b>Only what can be left running.</b> Mobs and fishing, and the bosses an Archive Pack comes
 * off. A chest is never thinned and neither is a brushed block, however many somebody opens: going
 * and looking is the game, and a player who spends a weekend raiding end cities has earned every
 * pack in them. The owner's line, and the whole of the rule: no farms, and no limit on exploring.
 * {@code LootSource.canBeFarmed} and {@code ArchiveDrops.canBeFarmed} are where the two are told
 * apart, so a source added later has to answer the question rather than default into a limit.
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
     * Boosters off something that can be left running: a mob farm, or a rod under a weight.
     * <p>Two an hour, against the seven an hour that exploring gives - so a night of a machine
     * running is worth about two hours of going out and looking, and building one is not the
     * shortcut it looks like. Somebody actually fishing or actually caving is inside the brim.
     * <p>Chests are not here and never will be: a chest has to be walked to.
     */
    FARMED_PACKS(2, 8),

    /**
     * Archive Packs off something that can be left running: a wither, a warden, a respawned dragon,
     * a rod under a weight.
     * <p>The one thing a player cannot buy at any price, so this is where a machine would hurt most
     * and where the pace is tightest - one every eight hours, against the one every fifteen hours
     * that exploring gives. An Archive Pack out of a chest somebody went to is never thinned.
     */
    FARMED_ARCHIVE(0.125, 1);

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
