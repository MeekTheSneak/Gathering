package dev.gathering.core.ui;

/**
 * Where the parts of a tournament's screen go, at the text and control sizes the player chose.
 * <p>The screen used to be laid out in fixed numbers - an 18-unit button, an 11-unit line - so
 * turning control size up changed nothing on it, and turning text size up made its lines and rows
 * draw through one another. An audit measured every widget at 100% and 200% control size in the
 * same rectangle. Here a button is as tall as the control size asks and never shorter than its
 * writing, a line as tall as its text, and the panel grows with both until the window stops it.
 * <p>When the window is too small for everything at the sizes asked, targets stay the size asked
 * and less is shown at once: lists page, and a match's result buttons move to a view of their own.
 * <p>Pure, and tested at every size the settings offer.
 */
public final class EventScreenLayout {

    /** The panel, at the size the interface shipped at. */
    public static final int PANEL_WIDTH = 420;
    public static final int PANEL_HEIGHT = 236;
    public static final int MARGIN = 8;

    private static final int BASE_ROW = 18;
    private static final int BASE_TAB = 16;
    private static final int BASE_LINE = 11;

    private final Rect panel;
    private final int row;
    private final int tabRow;
    private final int line;
    private final int gap;

    private EventScreenLayout(Rect panel, int row, int tabRow, int line, int gap) {
        this.panel = panel;
        this.row = row;
        this.tabRow = tabRow;
        this.line = line;
        this.gap = gap;
    }

    /**
     * @param controlPercent the player's control size, as a percentage
     * @param textScale      the player's text size, as a multiplier
     * @param fontLine       the font's own line height
     */
    public static EventScreenLayout of(int width, int height, int controlPercent, float textScale, int fontLine) {
        float controls = InterfaceScale.asFraction(controlPercent);
        int row = InterfaceScale.rowHeightFor(BASE_ROW, fontLine, controlPercent, textScale, BASE_ROW - fontLine);
        int tabRow = InterfaceScale.rowHeightFor(BASE_TAB, fontLine, controlPercent, textScale, BASE_TAB - fontLine);
        int line = Math.max(BASE_LINE, Math.round(fontLine * Math.max(1f, textScale)) + (BASE_LINE - fontLine));
        int gap = Math.max(3, Math.round(3 * controls));
        float grow = Math.max(1f, Math.max(controls, textScale));
        int panelWidth = Math.max(1, Math.min(Math.round(PANEL_WIDTH * grow), width - 8));
        int panelHeight = Math.max(1, Math.min(Math.round(PANEL_HEIGHT * grow), height - 4));
        Rect panel = new Rect((width - panelWidth) / 2, Math.max(2, (height - panelHeight) / 2), panelWidth, panelHeight);
        return new EventScreenLayout(panel, row, tabRow, line, gap);
    }

    public Rect panel() {
        return panel;
    }

    /** A button's height. */
    public int row() {
        return row;
    }

    /** A line of writing's height. */
    public int line() {
        return line;
    }

    public int gap() {
        return gap;
    }

    /**
     * Where the event's name is written.
     * <p>Lower by half of what the writing has grown: enlarged text is drawn centered on the
     * line it is placed on, and at twice the size the name's top sat on the panel's edge.
     */
    public int titleY() {
        return panel.y() + 3 + (line - BASE_LINE + 1) / 2;
    }

    /** Where the line under the name - kind, phase, round, clock - is written. */
    public int headerY() {
        return titleY() + line;
    }

    /** The {@code index}th of {@code tabs} tabs across the top. */
    public Rect tab(int index, int tabs) {
        int each = (panel.width() - MARGIN * 2 - gap * (tabs - 1)) / tabs;
        return new Rect(panel.x() + MARGIN + index * (each + gap), headerY() + line + 1, each, tabRow);
    }

    /** Where a tab's content starts. */
    public int bodyTop() {
        return headerY() + line + 1 + tabRow + gap * 2;
    }

    /** The row along the bottom - Done, and a tab's own buttons beside it. */
    public int bottomRow() {
        return panel.bottom() - MARGIN - row;
    }

    /** The lowest a tab's content may reach and keep clear of the bottom row. */
    public int bodyBottom() {
        return bottomRow() - gap;
    }

    /** The width a tab's content has. */
    public int bodyWidth() {
        return panel.width() - MARGIN * 2;
    }

    public int left() {
        return panel.x() + MARGIN;
    }

    /**
     * How many of {@code count} buttons, each at least {@code narrowest} wide, go on a row: every one
     * when they fit, else as few rows as fit, evened out so the last row is not a straggler.
     */
    public int perRow(int count, int narrowest) {
        if (count <= 0) {
            return 1;
        }
        int fits = Math.max(1, (bodyWidth() + gap) / (Math.max(1, narrowest) + gap));
        if (fits >= count) {
            return count;
        }
        int rows = (count + fits - 1) / fits;
        return (count + rows - 1) / rows;
    }

    /** How many rows {@code count} buttons take, at {@link #perRow}. */
    public int rowsFor(int count, int narrowest) {
        int perRow = perRow(count, narrowest);
        return (count + perRow - 1) / perRow;
    }

    /** How tall {@code rows} rows of buttons stand, with the gaps between them. */
    public int heightOfRows(int rows) {
        return rows <= 0 ? 0 : rows * row + (rows - 1) * gap;
    }

    /** How many lines of {@code lineHeight} fit between {@code top} and the body's bottom, at least one. */
    public int linesFrom(int top, int lineHeight) {
        return Math.max(1, (bodyBottom() - top) / Math.max(1, lineHeight));
    }
}
