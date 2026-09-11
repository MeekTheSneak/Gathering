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

    private SettingsLayout(Rect panel, int rowHeight, int rows) {
        this.panel = panel;
        this.rowHeight = rowHeight;
        this.rows = rows;
    }

    /**
     * A panel for this many rows, in a window this size, at this control scale.
     *
     * @param controlScale the player's control size as a percentage, as they set it
     */
    public static SettingsLayout of(int width, int height, int rows, int controlScale) {
        int wanted = Math.max(SMALLEST_ROW,
                Math.round(ROW * InterfaceScale.asFraction(controlScale)));
        int room = Math.max(0, height - MARGIN * 2);
        int rowHeight = wanted;
        // The title line, then a row each, then the way out. Squeezed a step at a time rather
        // than scrolled: a settings panel that has to be scrolled to reach "close" is worse at
        // every size than one whose rows are a little shorter.
        while (rowHeight > SMALLEST_ROW && heightFor(rows, rowHeight) > room) {
            rowHeight--;
        }
        int panelHeight = Math.min(heightFor(rows, rowHeight), Math.max(room, SMALLEST_ROW));
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(SMALLEST_ROW, width - MARGIN * 2));
        return new SettingsLayout(new Rect(
                (width - panelWidth) / 2,
                Math.max(0, (height - panelHeight) / 2),
                panelWidth, panelHeight), rowHeight, rows);
    }

    private static int heightFor(int rows, int rowHeight) {
        // A title, the rows, a gap, and the way out.
        return MARGIN * 2 + rowHeight + GAP + rows * (rowHeight + GAP) + rowHeight;
    }

    public Rect panel() {
        return panel;
    }

    public int rowHeight() {
        return rowHeight;
    }

    /** The {@code index}th setting row. */
    public Rect row(int index) {
        int top = panel.y() + MARGIN + rowHeight + GAP + index * (rowHeight + GAP);
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
