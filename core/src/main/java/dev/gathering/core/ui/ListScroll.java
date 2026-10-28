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
     * The scroll a list can actually be at, for a window that may have changed size.
     * <p>How many rows fit is worked out from the window, so growing the window - or dropping the
     * GUI scale - raises it while the scroll stays where the player left it. A row loop then reads
     * past the end of the list, which throws out of a screen's layout, which nothing catches.
     * <p>Here rather than in each screen because there were three of them and only two clamped. The
     * one that did not took the client down when a scrolled sharing screen was resized.
     */
    public static int within(int scroll, int total, int showing) {
        return Math.max(0, Math.min(scroll, Math.max(0, total) - Math.max(0, showing)));
    }

    /**
     * Whether a list is long enough to scroll at all.
     * <p>A list that fits in its window has nothing to scroll to, and a wheel over it should be
     * left for whatever is underneath rather than swallowed.
     */
    public static boolean scrolls(int total, int showing) {
        return total > showing;
    }

    /** The shortest a scrollbar's thumb is drawn, so a long list still has one to see. */
    public static final int SHORTEST_THUMB = 8;

    /**
     * Where a scrollbar's thumb sits in its track, as {top, height}: as tall as the share of the list
     * showing, and as far down the track as the list is scrolled. The whole track when nothing is hidden.
     */
    public static int[] thumb(int trackTop, int trackHeight, int first, int showing, int total) {
        int track = Math.max(1, trackHeight);
        if (!scrolls(total, showing)) {
            return new int[] {trackTop, track};
        }
        int tall = Math.max(Math.min(SHORTEST_THUMB, track), Math.round((float) track * showing / total));
        int deepest = Math.max(1, total - showing);
        int down = Math.round((float) (track - tall) * Math.clamp(first, 0, deepest) / deepest);
        return new int[] {trackTop + down, tall};
    }

    /**
     * A page of a list, kept to a page the list has.
     * <p>For a list that can shrink under whoever is reading it: an event list refreshed while on
     * its fourth page, with two pages' worth left, drew nothing and read "page 4 of 2".
     */
    public static int pageWithin(int page, int total, int perPage) {
        int pages = perPage <= 0 ? 1 : Math.max(1, (Math.max(0, total) + perPage - 1) / perPage);
        return Math.max(0, Math.min(page, pages - 1));
    }
}
