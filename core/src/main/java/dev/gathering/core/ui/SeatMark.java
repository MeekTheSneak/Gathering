package dev.gathering.core.ui;

/**
 * A short mark for a seat that is not a color.
 * <p>{@link SeatColor} gives every seat a color, which is the cheapest thing a four-player
 * table can be given and the most useful. It is also the only thing, and that is the problem:
 * a player who cannot tell two of those colors apart has nothing else to go on, and the
 * palette being chosen carefully only narrows the odds rather than removing them. A mark can
 * be read by anybody, printed beside the color rather than instead of it, so the two together
 * answer "whose is this" for everyone.
 * <p>Numbers rather than letters or shapes. A seat already has a number everywhere else in the
 * mod - the seat-number message, the log, the command-zone slots - so this is the name it
 * already had rather than a second naming scheme to learn. Shapes would need artwork, and the
 * artwork is the owner's.
 * <p>One-based, because "seat 1" is what a person sitting at a table would say and
 * {@code SeatId} being zero-based is an implementation detail nobody playing should meet.
 * <p>Pure.
 */
public final class SeatMark {

    private SeatMark() {
    }

    /**
     * The mark for a seat, by its index.
     * <p>Wraps the way {@link SeatColor} wraps, and for the same reason: a cluster with more
     * seats than the palette has entries must still name every one of them rather than throw
     * in the middle of drawing a board.
     */
    public static String of(int seat) {
        return Integer.toString(Math.floorMod(seat, SeatColor.count()) + 1);
    }
}
