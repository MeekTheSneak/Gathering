package dev.gathering.core.ui;

/**
 * A full-screen list with a heading over it and a footer under it.
 * <p>Two screens have this shape - the set progress list and the missing cards list - and they
 * had it twice, in constants that happened to agree. The footer is where they went wrong: a
 * hint on the left and a count of what is out of sight, each given half the screen, which run
 * into each other on any window narrow enough. That is every window at GUI scale 4.
 * <p>So the footer is laid out right to left. The way out is anchored to the right edge, the
 * count of hidden rows sits beside it, and the hint gets whatever is left - which can be
 * nothing, on a window narrow enough that the hint is the thing worth losing.
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record ListScreenLayout(
        Rect rows, Rect done, Rect hint, Rect more, int rowsThatFit, int rowHeight) {

    private static final int MARGIN = 16;
    private static final int TOP_BAR = 34;
    private static final int BOTTOM_BAR = 30;
    private static final int GAP = 8;

    /** The way out, bottom right, at the size every other footer button here is. */
    private static final int DONE_WIDTH = 56;
    private static final int DONE_HEIGHT = 18;

    public static int margin() {
        return MARGIN;
    }

    public static int topBar() {
        return TOP_BAR;
    }

    public static int bottomBar() {
        return BOTTOM_BAR;
    }

    public static int doneWidth() {
        return DONE_WIDTH;
    }

    /**
     * @param rowHeight how tall one row of this particular list is
     * @param moreWidth how wide the "N more" line will be drawn, or 0 when nothing is hidden -
     *                  measured by the caller, because only it has the font
     */
    public static ListScreenLayout of(
            int screenWidth, int screenHeight, int rowHeight, int moreWidth) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);
        int row = Math.max(1, rowHeight);

        int listTop = TOP_BAR;
        int listHeight = Math.max(row, height - TOP_BAR - BOTTOM_BAR);
        Rect rows = new Rect(MARGIN, listTop, Math.max(1, width - MARGIN * 2), listHeight);

        Rect done = new Rect(width - MARGIN - DONE_WIDTH, height - BOTTOM_BAR + 6,
                DONE_WIDTH, DONE_HEIGHT);

        // Right to left from the button, so nothing is laid out into space something else
        // already has. A hint with no room left is drawn as nothing rather than over the count.
        int footY = height - BOTTOM_BAR + 10;
        int rightEdge = done.x() - GAP;
        int wanted = Math.max(0, moreWidth);
        Rect more = wanted == 0
                ? Rect.NONE
                : new Rect(Math.max(MARGIN, rightEdge - wanted), footY, wanted, DONE_HEIGHT - 8);
        int hintRight = more.isEmpty() ? rightEdge : more.x() - GAP;
        Rect hint = new Rect(MARGIN, footY, Math.max(0, hintRight - MARGIN), DONE_HEIGHT - 8);

        return new ListScreenLayout(rows, done, hint, more, Math.max(1, listHeight / row), row);
    }

    /** Where the nth row on screen sits. Rows past the window come back empty. */
    public Rect rowAt(int index) {
        return index < 0 || index >= rowsThatFit
                ? Rect.NONE
                : new Rect(rows.x(), rows.y() + index * rowHeight, rows.width(), rowHeight);
    }
}
