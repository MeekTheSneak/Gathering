package dev.gathering.core.ui;

/**
 * Where the rows of the settings panel go.
 * <p>In core, and unit-tested, because a settings screen is the one screen that has to work at
 * every size a player might have chosen - and the sizes it offers are the very thing it is
 * changing. A panel that stopped fitting once somebody turned their controls up would be a
 * panel that could not be used to turn them back down.
 * <p>So the row height it lays out with is the same one the rest of the interface is being
 * asked to use, and the panel shrinks its rows before it overflows: what it will never do is
 * put a row off the bottom of the window, because the way out is the last row.
 * <p>Pure.
 */
public final class SettingsLayout {

    /** How wide the panel sits, when there is room for it. */
    public static final int PANEL_WIDTH = 280;

    /** The gap around the outside of the panel, and between its rows. */
    public static final int MARGIN = 10;
    public static final int GAP = 4;

    /** A row at the size the interface shipped at. */
    public static final int ROW = 18;

    /** The shortest a row may be squeezed to and still hold a line of text. */
    public static final int SMALLEST_ROW = 12;

    private final Rect panel;
    private final int rowHeight;
    private final int rows;
    private final int gap;

    private SettingsLayout(Rect panel, int rowHeight, int rows, int gap) {
        this.panel = panel;
        this.rowHeight = rowHeight;
        this.rows = rows;
        this.gap = gap;
    }

    /**
     * A panel for this many rows, in a window this size, at this control scale.
     *
     * @param controlScale the player's control size as a percentage, as they set it
     */
    public static SettingsLayout of(int width, int height, int rows, int controlScale) {
        return of(width, height, rows, controlScale, 9, 1f);
    }

    /**
     * The same, with the text the rows have to hold.
     * <p>Asked of the one rule every other screen sizes its rows by. This screen alone sized them
     * from the control size, so small controls with large text gave rows shorter than the words
     * written on them, one line touching the next - on the screen somebody opens to fix exactly that.
     *
     * @param lineHeight the font's line height
     * @param textScale  the player's text size, as a multiplier
     */
    public static SettingsLayout of(int width, int height, int rows, int controlScale, int lineHeight,
            float textScale) {
        int wanted = Math.max(SMALLEST_ROW,
                InterfaceScale.rowHeightFor(ROW, lineHeight, controlScale, textScale, ROW - lineHeight));
        int room = Math.max(0, height - MARGIN * 2);
        int rowHeight = wanted;
        // The title line, then a row each, then the way out. Squeezed a step at a time rather
        // than scrolled: a settings panel that has to be scrolled to reach "close" is worse at
        // every size than one whose rows are a little shorter.
        while (rowHeight > SMALLEST_ROW && heightFor(rows, rowHeight, GAP) > room) {
            rowHeight--;
        }
        // And past the shortest a row may be, the gaps between rows go - rather than the rows
        // running on under the way out, which is what a panel capped at the window did with rows
        // that no longer fit in it.
        int gap = GAP;
        while (gap > 0 && heightFor(rows, rowHeight, gap) > room) {
            gap--;
        }
        int panelHeight = Math.min(heightFor(rows, rowHeight, gap), Math.max(room, SMALLEST_ROW));
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(SMALLEST_ROW, width - MARGIN * 2));
        return new SettingsLayout(new Rect(
                (width - panelWidth) / 2,
                Math.max(0, (height - panelHeight) / 2),
                panelWidth, panelHeight), rowHeight, rows, gap);
    }

    private static int heightFor(int rows, int rowHeight, int gap) {
        // A title, the rows, a gap, and the way out.
        return MARGIN * 2 + rowHeight + gap + rows * (rowHeight + gap) + rowHeight;
    }

    public Rect panel() {
        return panel;
    }

    public int rowHeight() {
        return rowHeight;
    }

    /** The {@code index}th setting row. */
    public Rect row(int index) {
        int top = panel.y() + MARGIN + rowHeight + gap + index * (rowHeight + gap);
        return new Rect(panel.x() + MARGIN, top, panel.width() - MARGIN * 2, rowHeight);
    }

    /** The way out, which is always the last row and always inside the panel. */
    public Rect wayOut() {
        return new Rect(panel.x() + MARGIN,
                panel.bottom() - MARGIN - rowHeight,
                panel.width() - MARGIN * 2, rowHeight);
    }

    /** Where the title sits. */
    public Rect title() {
        return new Rect(panel.x() + MARGIN, panel.y() + MARGIN,
                panel.width() - MARGIN * 2, rowHeight);
    }

    public int rows() {
        return rows;
    }
}
