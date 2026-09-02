package dev.gathering.core.ui;

/**
 * Where every part of the counters panel goes, at whatever size the window happens to be.
 * <p>The panel stacks four things that all vary: the counters already on the subject, a grid
 * of buttons for the ones this table has named, a grid of commander damage or commander tax,
 * and a footer - a field to name a new counter, and the way out. Laid out top down from the
 * counts, a crowded Commander table on a small window pushed the footer off the bottom of the
 * screen, which is a panel with no way to add a counter and no way to close.
 * <p>So the footer is placed first, from the panel's bottom edge upward, and everything else
 * shares what is left. When that is not enough, sections give way in a fixed order - the
 * button grid first, because every one of its buttons is a shortcut for something the text
 * field can still do; then the lists, which window and say how many are out of sight.
 * <p>Both the widget building and the drawing read this, rather than each deriving the same
 * offsets from the same counts. They did, and they disagreed: the drawing asked how many rows
 * fit at the moment it drew, the buttons were built from how many fit when the panel was
 * built, and a counter arriving between the two put a row's minus button on its neighbour.
 * <p>Coordinates are GUI-scaled screen units, origin top left.
 */
public record CountersLayout(
        Rect panel,
        int counterRows,
        int commonRows,
        int damageRows,
        int taxRows,
        Rect firstCounter,
        Rect common,
        Rect damage,
        Rect tax,
        Rect custom,
        Rect done) {

    private static final int PANEL_WIDTH = 216;
    private static final int MARGIN = 10;
    private static final int ROW = 18;
    private static final int GAP = 4;

    /** How many counters the panel shows at once. The rest are a wheel away. */
    public static final int MAX_ROWS = 6;

    /**
     * How few counter rows the panel will go down to before it starts dropping other things.
     * <p>Three, because the list is what the panel is for and one row of it is not a list -
     * but the buttons under it are worth more than the fourth row, and a window that had to
     * choose used to keep all six rows and no buttons at all.
     */
    public static final int KEEP_ROWS = 3;

    /** The buttons are three to a line. */
    public static final int BUTTON_COLUMNS = 3;

    /** How tall one row of anything is, gap included. */
    public static int step() {
        return ROW + GAP;
    }

    public static int rowHeight() {
        return ROW;
    }

    public static int gap() {
        return GAP;
    }

    public static int margin() {
        return MARGIN;
    }

    public static int panelWidth() {
        return PANEL_WIDTH;
    }

    /**
     * @param counters how many named counters the subject has
     * @param buttons  how many counter names this table has used, one button each
     * @param opponents how many enemy commanders there are to record damage from
     * @param taxed     how many commanders here have a tax to show
     */
    public static CountersLayout of(
            int screenWidth, int screenHeight, int counters, int buttons, int opponents,
            int taxed) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);
        int wantCounters = Math.clamp(counters, 0, MAX_ROWS);
        int wantCommon = (Math.max(0, buttons) + BUTTON_COLUMNS - 1) / BUTTON_COLUMNS;
        int wantDamage = Math.max(0, opponents);
        int wantTax = Math.max(0, taxed);

        // The panel is as tall as its content wants, up to the screen. Beyond that the
        // sections below give way rather than the panel growing past the edge.
        int natural = MARGIN * 2 + ROW
                + body(wantCounters, wantCommon, wantDamage, wantTax)
                + GAP * 2 + ROW + GAP + ROW;
        int tall = Math.min(natural, height - MARGIN * 2);
        Rect panel = new Rect((width - PANEL_WIDTH) / 2, Math.max(MARGIN, (height - tall) / 2),
                PANEL_WIDTH, Math.max(ROW, tall));

        // Placed first, from the bottom up, because these two are the panel's only way to add
        // a counter and its only way out.
        Rect done = new Rect(panel.x() + MARGIN, panel.bottom() - MARGIN - ROW,
                PANEL_WIDTH - MARGIN * 2, ROW);
        Rect custom = new Rect(panel.x() + MARGIN, done.y() - GAP - ROW,
                PANEL_WIDTH - MARGIN * 2, ROW);

        int top = panel.y() + MARGIN + ROW;
        int room = Math.max(0, custom.y() - GAP * 2 - top);

        int counterRows = wantCounters;
        int commonRows = wantCommon;
        int damageRows = wantDamage;
        int taxRows = wantTax;
        // In this order. The counter list gives up its tail first, down to three rows, because
        // it has a wheel and a line saying how many are out of sight - a shortened list is one
        // you can still reach the rest of, and three of them are still a list. Then the
        // buttons, every one of which is a shortcut for a name the text field below still
        // takes, so losing them costs nothing but keystrokes. Then the rest of the list. The
        // commander grids last, because they have neither a wheel nor a shortcut, and only in
        // the corner where a window at the smallest size Minecraft allows meets a table
        // fielding five or more enemy commanders.
        while (body(counterRows, commonRows, damageRows, taxRows) > room
                && counterRows > KEEP_ROWS) {
            counterRows--;
        }
        while (body(counterRows, commonRows, damageRows, taxRows) > room && commonRows > 0) {
            commonRows--;
        }
        while (body(counterRows, commonRows, damageRows, taxRows) > room && counterRows > 0) {
            counterRows--;
        }
        while (body(counterRows, commonRows, damageRows, taxRows) > room && damageRows > 1) {
            damageRows--;
        }
        while (body(counterRows, commonRows, damageRows, taxRows) > room && taxRows > 1) {
            taxRows--;
        }

        int content = PANEL_WIDTH - MARGIN * 2;
        Rect firstCounter = new Rect(panel.x() + MARGIN, top, content, ROW);
        // A blank line under the counters, which is where "3 more" goes when the list is
        // longer than the window - and where "no counters" goes when there are none at all.
        int commonTop = top + counterRows * step() + ROW;
        Rect common = commonRows == 0
                ? Rect.NONE
                : new Rect(panel.x() + MARGIN, commonTop, content, commonRows * step() - GAP);
        int gridTop = commonTop + commonRows * step() + GAP;
        // Damage and tax never both appear - one is asked of a seat and the other of cards -
        // but the arithmetic does not need to know that, and would be wrong if it changed.
        Rect damage = damageRows == 0
                ? Rect.NONE
                : new Rect(panel.x() + MARGIN, gridTop, content, ROW + damageRows * step() - GAP);
        int taxTop = gridTop + (damageRows == 0 ? 0 : (damageRows + 1) * step());
        Rect tax = taxRows == 0
                ? Rect.NONE
                : new Rect(panel.x() + MARGIN, taxTop, content, ROW + taxRows * step() - GAP);

        return new CountersLayout(panel, counterRows, commonRows, damageRows, taxRows,
                firstCounter, common, damage, tax, custom, done);
    }

    /** How tall everything between the title and the footer is, at these row counts. */
    private static int body(int counterRows, int commonRows, int damageRows, int taxRows) {
        return counterRows * step() + ROW
                + commonRows * step()
                + (damageRows == 0 ? 0 : (damageRows + 1) * step())
                + (taxRows == 0 ? 0 : (taxRows + 1) * step());
    }

    /** Where the nth counter row sits. */
    public Rect counterRow(int index) {
        return index < 0 || index >= counterRows
                ? Rect.NONE
                : new Rect(firstCounter.x(), firstCounter.y() + index * step(),
                        firstCounter.width(), ROW);
    }

    /** Where the line under the counters goes - "3 more", or "no counters yet". */
    public Rect counterFooter() {
        return new Rect(firstCounter.x(), firstCounter.y() + counterRows * step(),
                firstCounter.width(), ROW);
    }

    /** Where the nth counter button goes, three to a line. */
    public Rect commonButton(int index) {
        if (common.isEmpty() || index < 0 || index >= commonRows * BUTTON_COLUMNS) {
            return Rect.NONE;
        }
        int each = (common.width() - GAP * (BUTTON_COLUMNS - 1)) / BUTTON_COLUMNS;
        return new Rect(common.x() + (index % BUTTON_COLUMNS) * (each + GAP),
                common.y() + (index / BUTTON_COLUMNS) * step(), each, ROW);
    }

    /** Where the nth commander-damage row goes. The heading is on the line above the first. */
    public Rect damageRow(int index) {
        return gridRow(damage, damageRows, index);
    }

    /** Where the nth commander-tax row goes. */
    public Rect taxRow(int index) {
        return gridRow(tax, taxRows, index);
    }

    private Rect gridRow(Rect grid, int rows, int index) {
        return grid.isEmpty() || index < 0 || index >= rows
                ? Rect.NONE
                : new Rect(grid.x(), grid.y() + ROW + index * step(), grid.width(), ROW);
    }
}
