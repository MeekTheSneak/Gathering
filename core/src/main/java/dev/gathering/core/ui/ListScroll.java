package dev.gathering.core.ui;

/**
 * Where a list is scrolled to after one turn of the wheel.
 * <p>The loaner shelf and the replay list each worked this out for themselves, in the same
 * seven lines - found by an exact-sequence scan. It is the kind of arithmetic that is easy to
 * get subtly wrong in one copy and not the other: an off-by-one at the bottom that lets the
 * last row scroll away, or a list shorter than the window that can be scrolled at all.
 * <p>Pure, and tested, so both lists answer the same way.
 */
public final class ListScroll {

    private ListScroll() {
    }

    /**
     * The first row showing after the wheel turns once.
     * <p>One row per notch, whichever way and however hard it was turned: a fast wheel that
     * jumped ten rows would skip past the one row somebody was scrolling towards.
     * <p>Clamped at both ends. Never above the top, and never so far down that the last row is
     * no longer at the bottom of the window - a list scrolled past its own end is a window of
     * nothing.
     *
     * @param scroll   the first row showing now
     * @param total    how many rows there are
     * @param showing  how many fit in the window
     * @param wheel    the wheel's movement; positive is towards the top
     */
    public static int after(int scroll, int total, int showing, double wheel) {
        int deepest = Math.max(0, total - Math.max(0, showing));
        int moved = scroll - (int) Math.signum(wheel);
        return Math.max(0, Math.min(moved, deepest));
    }

    /**
     * Whether a list is long enough to scroll at all.
     * <p>A list that fits in its window has nothing to scroll to, and a wheel over it should be
     * left for whatever is underneath rather than swallowed.
     */
    public static boolean scrolls(int total, int showing) {
        return total > showing;
    }
}
