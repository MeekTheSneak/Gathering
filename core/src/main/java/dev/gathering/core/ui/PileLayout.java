package dev.gathering.core.ui;

/**
 * Where the cards sit in a box that shows a pile, and which one a click landed on.
 * <p>The second question is the reason this is a class rather than two lines in a screen. A
 * pile holding more cards than fit keeps laying the rest out below the fold - the slots are
 * still computed, they are simply scrolled past the bottom of the grid - and a screen that
 * asked "is the pointer inside any slot" got yes for a card nobody can see. The slots below
 * the fold are eighty-four pixels tall and the box's Done button is forty pixels under the
 * grid, so one of them was always lying across it: the click was answered by an invisible
 * card, the button never heard it, and the only way out of the box was Escape.
 * <p>So the grid comes first here and the slots second. A point outside the grid is not on a
 * card, whatever the arithmetic says about where the card would have been.
 */
public record PileLayout(
        Rect grid, int columns, int cardWidth, int cardHeight, int gap, int scroll) {

    public PileLayout {
        columns = Math.max(1, columns);
        cardWidth = Math.max(1, cardWidth);
        cardHeight = Math.max(1, cardHeight);
        gap = Math.max(0, gap);
        grid = grid == null ? Rect.NONE : grid;
    }

    /** Where this card is drawn, which may be above or below the grid when it scrolls. */
    public Rect slot(int index) {
        return new Rect(
                grid.x() + (index % columns) * (cardWidth + gap),
                grid.y() + (index / columns) * (cardHeight + gap) - scroll,
                cardWidth,
                cardHeight);
    }

    /**
     * The card under this point, or -1 for none.
     * <p>The grid is asked before any slot is. A card scrolled out of the box is out of the
     * box: it is not drawn, so it cannot be clicked, and everything laid out beneath the grid
     * - the hint line, the Done button - belongs to whatever is drawn there.
     */
    public int slotAt(int count, int x, int y) {
        if (!grid.contains(x, y)) {
            return -1;
        }
        for (int index = 0; index < count; index++) {
            if (slot(index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Which slot a point falls in, clamped into the grid rather than answering -1.
     * <p>What a drag wants, where {@link #slotAt} is what a click wants. A drag is always
     * going somewhere - the card is in the air and has to come down - so a point past the last
     * column lands in the last column rather than nowhere.
     */
    public int nearestSlot(int x, int y) {
        int column = Math.clamp((x - grid.x()) / (cardWidth + gap), 0, columns - 1);
        int row = Math.max(0, (y + scroll - grid.y()) / (cardHeight + gap));
        return row * columns + column;
    }

    /**
     * The gap a drag at this point is aimed at, for a row of {@code count} cards.
     * <p>Gap <i>g</i> is the space before slot <i>g</i>; {@code count} means past the end of
     * the row. One rule, asked by the bar drawn during the drag and by the release that
     * follows it - the bar is a promise about the release, and two copies of a promise drift.
     * <p>The last card counts as two gaps, split down its middle. Without that the far end of
     * the row cannot be reached at all: every point over the last card would mean "before it",
     * and there would be no way to say "after everything".
     */
    public int gapAt(int count, int x, int y) {
        if (count <= 0) {
            return 0;
        }
        int landing = Math.clamp(nearestSlot(x, y), 0, count - 1);
        Rect slot = slot(landing);
        boolean past = landing == count - 1 && !slot.isEmpty() && x > slot.centerX();
        return past ? count : landing;
    }

    /** How far past the bottom of the grid the last row reaches, at this scroll. */
    public int hiddenBelow(int count) {
        int rows = (count + columns - 1) / columns;
        int tall = rows * (cardHeight + gap) - gap;
        return Math.max(0, tall - grid.height());
    }
}
