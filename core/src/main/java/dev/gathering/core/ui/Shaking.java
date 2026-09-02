package dev.gathering.core.ui;

/**
 * How hard something is shaking, and which way, at a moment.
 * <p>A shuffle is the one thing a player does that changes nothing anybody can see. No card
 * changes zones, no count moves, and the order it changes is the order nobody is allowed to
 * know - so a library that has just been shuffled looks exactly like one that has not, and the
 * only account of it is a line in the log. A stack of cards briefly rattling is what a shuffle
 * looks like at a real table, and it is enough.
 * <p>Pure, and a function of the time since it started rather than of a counter somebody has
 * to remember to advance: two clients shake the same pile for the same length, and a frame
 * dropped in the middle is a frame missed rather than a shake that runs long.
 */
public final class Shaking {

    /**
     * How long a shake lasts, in milliseconds.
     * <p>About as long as a hand takes to riffle a deck once. Long enough to catch out of the
     * corner of an eye across the table, short enough that four people shuffling at the start
     * of a game is not a board that vibrates.
     */
    public static final long LASTS = 380L;

    /** How fast it rattles. Fast enough to read as a rattle rather than as a wobble. */
    private static final double SPEED = 0.055;

    private Shaking() {
    }

    /**
     * How much of the shake is left: one as it starts, nought when it is over.
     * <p>Squared, so it dies away rather than stopping. A shake that ends at full strength
     * looks like the card was dropped.
     */
    public static float strength(long since) {
        if (since < 0 || since >= LASTS) {
            return 0f;
        }
        float left = 1f - since / (float) LASTS;
        return left * left;
    }

    /** How far a shuffled pile rattles, as a fraction of its own width. */
    private static final int SHAKE_OF_A_SLOT = 8;

    /**
     * The slot, rattled - or itself once the shaking is over.
     * <p>The one rule for both boards. The seated screen and the miniature on the block each
     * carried their own copy of the seed and reach arithmetic, and the miniature's javadoc
     * promised "the same shake the seated board draws" - a promise only kept by hand. Seeded
     * by seat and zone so two libraries shuffled at once are two hands shuffling rather than
     * one board vibrating.
     */
    public static Rect shaken(
            Rect slot, dev.gathering.core.game.SeatId seat, dev.gathering.core.game.Zone zone,
            long since) {
        if (since < 0 || slot.isEmpty()) {
            return slot;
        }
        int reach = Math.max(1, slot.width() / SHAKE_OF_A_SLOT);
        int seed = seat.index() * dev.gathering.core.game.Zone.values().length + zone.ordinal();
        return new Rect(
                slot.x() + wobble(seed, since, reach),
                slot.y() + wobble(seed + 7, since, reach),
                slot.width(), slot.height());
    }

    /**
     * How far to move something along one axis, in whatever units the caller measures in.
     * <p>The seed is what stops two piles shaken at once from moving as one object: they are
     * separate stacks in separate hands. It is the caller's to choose and only has to differ.
     */
    public static int wobble(int seed, long since, int reach) {
        float strength = strength(since);
        if (strength <= 0f || reach <= 0) {
            return 0;
        }
        double phase = since * SPEED + seed;
        return (int) Math.round(Math.sin(phase) * reach * strength);
    }
}
