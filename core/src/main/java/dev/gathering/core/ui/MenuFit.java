package dev.gathering.core.ui;

/**
 * How big a menu can be and still fit on the screen it is opened on.
 * <p>A menu has three sizes that pull against each other: the text size a player asked for,
 * the control size they asked for, and the window they are playing in. Solving any two of
 * those and ignoring the third is how a menu ends up 792 pixels wide in a 427-pixel viewport,
 * which is what an audit photographed - every action past the halfway point simply off the
 * screen, unreachable by mouse or key.
 *
 * <p>So all three are solved together, in that order of preference:
 *
 * <ol>
 *   <li><b>Honour the asked text size if it fits.</b> Somebody who turned text up did so for a
 *       reason, and quietly turning it back down is not a fix.
 *   <li><b>Wrap into columns</b> when the menu is too tall, which is what a long menu does
 *       everywhere and costs no entry.
 *   <li><b>Shrink, uniformly and only as far as needed</b>, when the window genuinely cannot
 *       hold the whole menu at the asked size. Uniformly matters: fitting each line to its own
 *       column produces one menu in three sizes, which the key list learned to avoid two
 *       phases ago and which the same audit photographed here.
 * </ol>
 *
 * <p>The floor is {@link TextScale#SMALLEST}. Below that the font stops being letters, and a
 * menu nobody can read is not better than one that scrolls - but scrolling a context menu is a
 * control of its own, and this is the answer that needs none.
 *
 * <p>Pure. Given the numbers a screen knows, it returns the numbers a menu needs.
 */
public record MenuFit(float scale, int columnWidth, int columns, int rowHeight) {

    /** Everything a menu of this many rows would occupy, across. */
    public int width() {
        return columnWidth * columns;
    }

    /** And down, for this many entries. */
    public int height(int entries, int padding) {
        return Math.min(entries, perColumn(entries)) * rowHeight + padding * 2;
    }

    /** How many rows go in one column, given how many there are altogether. */
    public int perColumn(int entries) {
        return Math.max(1, (entries + columns - 1) / columns);
    }

    /**
     * A fit for this menu on this screen.
     *
     * @param entries       how many rows, rules included
     * @param widest        the widest row's text at one to one, shortcut and all
     * @param lineHeight    the font's own line height
     * @param roomAcross    how much width the menu may occupy
     * @param roomDown      how much height it may occupy, inside its padding
     * @param baseRow       the row height the interface ships with
     * @param controls      the player's control size, as a percentage
     * @param askedText     the player's text size, as a multiplier
     * @param padding       clear space inside a row
     * @param leastWidth    the narrowest a column is allowed to be
     */
    public static MenuFit of(
            int entries, int widest, int lineHeight, int roomAcross, int roomDown,
            int baseRow, int controls, float askedText, int padding, int leastWidth) {
        int rows = Math.max(1, entries);
        int across = Math.max(leastWidth, roomAcross);
        int down = Math.max(1, roomDown);

        // Kept to sizes a font can be before anything is worked out from it. Math.max with a NaN is
        // NaN, and a NaN never compares as small enough to stop the loop below, which then ran for
        // ever; a size in the millions was no better, since taking a tenth off it changed nothing.
        float scale = Float.isFinite(askedText)
                ? Math.max(TextScale.SMALLEST, Math.min(TextScale.LARGEST, askedText))
                : 1f;
        // Step the size down until the whole menu fits, or until the font stops being letters.
        // A tenth at a time, so a menu that only just overflows loses only a little - and counted,
        // so it ends however the sizes compare.
        int steps = Math.round((scale - TextScale.SMALLEST) / 0.1f) + 1;
        MenuFit fit = shapedAt(scale, rows, widest, lineHeight, across, down, baseRow, controls, padding, leastWidth);
        for (int step = 0; step < steps && !fit.fits(rows, across, down, padding); step++) {
            scale = Math.max(TextScale.SMALLEST, scale - 0.1f);
            fit = shapedAt(scale, rows, widest, lineHeight, across, down, baseRow, controls, padding, leastWidth);
        }
        return fit;
    }

    private static MenuFit shapedAt(
            float scale, int entries, int widest, int lineHeight, int across, int down,
            int baseRow, int controls, int padding, int leastWidth) {
        int rowHeight = InterfaceScale.rowHeightFor(baseRow, lineHeight, controls, scale, padding);
        int columnWidth = Math.max(leastWidth, Math.round(widest * scale) + padding * 2);
        // Never wider than the screen even as one column: a single column that overflows is
        // still a menu with its right-hand side off the edge.
        columnWidth = Math.min(columnWidth, across);

        int perColumn = Math.max(1, down / rowHeight);
        int wanted = Math.max(1, (entries + perColumn - 1) / perColumn);
        // And never more columns than fit across, which is the constraint that was missing.
        int columns = Math.max(1, Math.min(wanted, across / Math.max(1, columnWidth)));
        return new MenuFit(scale, columnWidth, columns, rowHeight);
    }

    /**
     * Whether a menu of this many entries is wholly on the screen at this fit.
     * <p>Across and down, which is all of it: every entry has a column to go in by the way the
     * columns are counted, so a third test here that said so could never be false.
     */
    public boolean fits(int entries, int roomAcross, int roomDown, int padding) {
        return width() <= roomAcross
                && height(entries, padding) <= roomDown + padding * 2;
    }
}
