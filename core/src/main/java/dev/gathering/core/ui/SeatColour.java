package dev.gathering.core.ui;

/**
 * A colour per seat, so a board can be told from the board next to it.
 *
 * <p>The thing a four-player table needs most and the cheapest to give it. Four playmats laid
 * out on one surface are four identical rectangles; the moment each has a colour, "whose
 * creature is that" and "which life total is mine" stop being questions you have to work out
 * from where things are sitting. Every table simulator does this and so does every paper
 * playgroup, with sleeves.
 *
 * <p>Ordered rather than chosen, so a seat's colour is the same for everybody looking at the
 * table - a colour one player picked and another did not see would be worse than none.
 *
 * <p>Chosen to stay apart for the commonest kinds of colour blindness: no red next to green,
 * and the pairs that could be confused differ in brightness as well as in hue. Card games are
 * played by people who have spent years being told which of two similar greens is theirs.
 */
public final class SeatColour {

    /**
     * Eight, because eight is the most seats a cluster can have.
     *
     * <p>Yellow, blue, red and white first, which is the order the four-player tables people
     * already play on use - so somebody coming from one finds their own colour where they
     * expect it.
     */
    private static final int[] PALETTE = {
        0xFFD54A, // yellow
        0x4FA4CF, // blue
        0xE05A4A, // red
        0xE8E4DA, // white
        0x7FCF6A, // green
        0xC97FE0, // violet
        0xE0913F, // orange
        0x5FD8C4, // teal
    };

    private SeatColour() {
    }

    /** The colour of a seat, as red-green-blue with no alpha. */
    public static int of(int seat) {
        return PALETTE[Math.floorMod(seat, PALETTE.length)];
    }

    /** The same colour at a given opacity, for a border or a wash over the felt. */
    public static int at(int seat, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | of(seat);
    }

    public static int count() {
        return PALETTE.length;
    }
}
