package dev.gathering.core.ui;

/**
 * The verb search's panel, in numbers: what has been typed, perhaps a line saying what the verbs
 * will act on, and a list of rows - at the text and control sizes the player asked for.
 * <p>The panel used to space its rows eleven pixels apart whatever size the writing was. At text
 * 200% each label was drawn eighteen pixels tall on its eleven-pixel row, through both of its
 * neighbors, with the highlight lighting a band that no longer matched any label and a click on
 * the visible top of a label landing in the row above. The context menu had already been fixed
 * for exactly this; the palette beside it had not. So the panel's whole height, every row's
 * place, the band a click is read against and where each piece of writing goes are worked out
 * here, from the same three sizes, and the screen only draws what this says.
 * <p>Pure.
 *
 * @param lineHeight  the font's own line height
 * @param header      the typed line and the acting line, as lines of writing at the asked size
 * @param acting      whether there is a line saying what the verbs will act on
 * @param rowHeight   how tall one row is: room for its writing at the asked size, or the
 *                    control size's own row, whichever is taller
 * @param visibleRows how many rows are on screen at once
 */
public record PaletteLayout(int lineHeight, TextLines header, boolean acting, int rowHeight, int visibleRows) {

    /** Clear space inside the panel's edge. */
    public static final int PADDING = 6;

    /** Between the header and the first row. */
    public static final int GAP = 4;

    /** The row height the panel ships with, at full size. */
    public static final int BASE_ROW = 11;

    /** Clear space above and below a row's writing, shared between the two. */
    public static final int ROW_PADDING = 2;

    /**
     * The most rows on screen at once.
     * <p>Enough that an unsearched palette shows a useful slice of the catalogue, few enough that
     * the panel does not become the board. Past this it scrolls with the highlight. Fewer where
     * the window cannot hold this many at the size asked for: a list drawn past the foot of its
     * own panel is a list whose last rows cannot be read or reached.
     */
    public static final int MOST_ROWS = 9;

    /**
     * The panel for this many rows, in this much height.
     *
     * @param lineHeight     the font's own line height
     * @param textScale      the player's text size, as a multiplier
     * @param controlPercent the player's control size
     * @param acting         whether the acting line is shown
     * @param rows           how many rows the search is showing
     * @param roomDown       the most height the panel may have
     */
    public static PaletteLayout of(
            int lineHeight, float textScale, int controlPercent, boolean acting, int rows, int roomDown) {
        TextLines header = new TextLines(lineHeight, textScale, 0);
        int rowHeight = InterfaceScale.rowHeightFor(
                BASE_ROW, lineHeight, controlPercent, header.scale(), ROW_PADDING);
        int fits = Math.max(1, (roomDown - firstRowOffset(header, acting) - PADDING) / rowHeight);
        int wanted = Math.max(1, Math.min(MOST_ROWS, rows));
        return new PaletteLayout(lineHeight, header, acting, rowHeight, Math.min(wanted, fits));
    }

    /** From the panel's top to the top of the first row. */
    public int firstRowOffset() {
        return firstRowOffset(header, acting);
    }

    private static int firstRowOffset(TextLines header, boolean acting) {
        return PADDING + header.drawnHeight() * (acting ? 2 : 1) + GAP;
    }

    /** The whole panel's height. */
    public int height() {
        return firstRowOffset() + visibleRows * rowHeight + PADDING;
    }

    /** Where the typed line's drawing call goes, from the panel's top. */
    public int typedDrawOffset() {
        return header.drawY(PADDING);
    }

    /** Where the acting line's drawing call goes, from the panel's top. */
    public int actingDrawOffset() {
        return header.drawY(PADDING + header.drawnHeight());
    }

    /** From the panel's top to the top of one visible row, counted from the first shown. */
    public int rowOffset(int shown) {
        return firstRowOffset() + shown * rowHeight;
    }

    /**
     * Where a row's label's drawing call goes, from the top of its row.
     * <p>Down the middle, so the writing - which grows about its own middle - sits inside its own
     * band at every size, and a click anywhere on the label is read as a click on its row.
     */
    public int labelDrawOffset() {
        return (rowHeight - lineHeight) / 2;
    }

    /** Which visible row a point this far below the panel's top is in, or -1 for none. */
    public int rowAt(int offsetY) {
        int below = offsetY - firstRowOffset();
        if (below < 0) {
            return -1;
        }
        int shown = below / rowHeight;
        return shown < visibleRows ? shown : -1;
    }
}
