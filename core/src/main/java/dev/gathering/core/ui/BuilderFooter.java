package dev.gathering.core.ui;

/**
 * The row of buttons along the bottom of the deck builder.
 *
 * <p>Four buttons, in two groups that answer different questions. On the left, ways of
 * filling the deck in - from a decklist, or by picking sleeves for it. On the right, the
 * two ways out - cancel, or finish. Laid out from constants they read correctly at the
 * window the author happened to be running and overlap below about 346 units wide, which
 * is inside the range Minecraft allows: the smallest window is 320 GUI units, and every
 * GUI scale from 1 to 4 lands somewhere different.
 *
 * <p>So when both groups will not fit on one line, the left group takes a line of its own
 * above rather than sliding underneath the right one. Hiding it was the other option and
 * is the wrong one here: the sleeve picker has no other way in while a deck is being built,
 * so a narrow window would mean a deck nobody can sleeve.
 *
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record BuilderFooter(Rect fromList, Rect sleeves, Rect cancel, Rect finish, int rows) {

    private static final int MARGIN = 12;
    private static final int GAP = 6;
    private static final int ROW = 18;

    private static final int FROM_LIST = 84;
    private static final int SLEEVES = 78;
    private static final int CANCEL = 70;
    private static final int FINISH = 78;

    /** How tall one button is, so the screen reserving space agrees with this. */
    public static int rowHeight() {
        return ROW;
    }

    /** The space between rows and around the outside, same reason. */
    public static int gap() {
        return GAP;
    }

    /**
     * @param withLeftGroup false when the builder was opened out of the player's own pockets,
     *                      where there is no decklist to import from and no deck yet to sleeve
     */
    public static BuilderFooter of(int screenWidth, int screenHeight, boolean withLeftGroup) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        int lastRow = height - ROW - GAP;
        Rect cancel = new Rect(width - MARGIN - CANCEL - GAP - FINISH, lastRow, CANCEL, ROW);
        Rect finish = new Rect(width - MARGIN - FINISH, lastRow, FINISH, ROW);
        if (!withLeftGroup) {
            return new BuilderFooter(Rect.NONE, Rect.NONE, cancel, finish, 1);
        }

        // Both groups on one line only while the left one ends before the right one starts.
        int leftEnd = MARGIN + FROM_LIST + GAP + SLEEVES;
        boolean together = leftEnd + GAP <= cancel.x();
        int leftRow = together ? lastRow : lastRow - ROW - GAP;
        return new BuilderFooter(
                new Rect(MARGIN, leftRow, FROM_LIST, ROW),
                new Rect(MARGIN + FROM_LIST + GAP, leftRow, SLEEVES, ROW),
                cancel,
                finish,
                together ? 1 : 2);
    }

    /** How tall the block of buttons is, top of the first row to bottom of the last. */
    public int height() {
        return rows * ROW + (rows - 1) * GAP;
    }
}
