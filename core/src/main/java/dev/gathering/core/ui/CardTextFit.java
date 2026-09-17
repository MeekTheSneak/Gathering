package dev.gathering.core.ui;

/**
 * How everything a card says fits the box it is read in, whole.
 * <p>Reported by the owner: "the alt screen that opens when hovering over a card should have
 * the entire card's text. It needs to not be cut off. No matter what screen it is in." The
 * panel used to stop drawing at the bottom of its box, so a wordy card lost its last ability,
 * or a double-faced card its whole back, without a mark to say anything was missing.
 * <p>So the words are fitted rather than cut, in this order:
 * <ol>
 *   <li><b>The asked size, with the card's history.</b>
 *   <li><b>The asked size without the history.</b> Where a card has been is worth less than
 *       what it does, so it goes before a single rule shrinks.
 *   <li><b>Smaller, only as far as it has to.</b> Rewrapped at every step, because smaller
 *       letters fit more to a line and the text gets shorter as well as smaller.
 *   <li><b>More columns</b>, once the floor is reached, from the asked size down again.
 * </ol>
 * <p>The Scryfall credit is kept in every one of them, at the same size as the rest, pinned
 * under the text. It is a licensing credit rather than decoration.
 * <p>The caller gives the box as much room as it can first: this decides what goes in it, not
 * how big it is.
 * <p>Pure. Wrapping belongs to the font, so the caller hands in a {@link Measure} and this
 * does the arithmetic on the heights that come back.
 *
 * @param scale        what the text is drawn at
 * @param columns      how many columns it flows across
 * @param columnWrap   the width each column is wrapped to, in unscaled font units
 * @param creditWrap   the width the credit is wrapped to, in the same units
 * @param capacity     how much text a column holds, in the same units
 * @param withStory    whether the card's history is included
 * @param height       how much of the box it takes, in screen units, credit included
 * @param fits         whether all of it is inside the box; false only when no arrangement is
 */
public record CardTextFit(
        float scale, int columns, int columnWrap, int creditWrap, int capacity,
        boolean withStory, int height, boolean fits) {

    /** What the font says about the text, which is the half of this that is not arithmetic. */
    public interface Measure {

        /** Each line's height at full size, top to bottom, wrapped to this width. */
        int[] lines(int wrapWidth, boolean withStory);

        /** The same for the credit. */
        int[] credit(int wrapWidth);
    }

    /** How far the text shrinks per try. */
    public static final float STEP = 0.05f;

    /** The smallest the text is drawn, which is as small as any text in the mod is. */
    public static final float FLOOR = TextScale.SMALLEST;

    /** Between the text and the credit under it, in screen units. */
    public static final int CREDIT_GAP = 8;

    /** Between two columns, in screen units. */
    public static final int COLUMN_GAP = 10;

    /** A column narrower than this, in font units, holds a word a line. */
    public static final int NARROWEST_COLUMN = 110;

    /** Past this many columns the text is a table of fragments. */
    public static final int MOST_COLUMNS = 4;

    /** Whether this is all of the text at the size the player asked for, in one column. */
    public boolean isWhole(float asked) {
        return fits && columns == 1 && scale >= TextScale.sane(asked);
    }

    /**
     * The fit for this text in a box this size.
     *
     * @param width  the box across, in screen units
     * @param height the box down, credit included
     * @param asked  the player's text size
     */
    public static CardTextFit of(int width, int height, float asked, Measure measure) {
        float start = Math.max(FLOOR, TextScale.sane(asked));
        CardTextFit tried = attempt(width, height, start, 1, true, measure);
        if (tried.fits) {
            return tried;
        }
        for (int columns = 1; columns <= MOST_COLUMNS; columns++) {
            for (float scale = start; ; scale = Math.max(FLOOR, scale - STEP)) {
                tried = attempt(width, height, scale, columns, false, measure);
                if (tried.fits) {
                    return tried;
                }
                if (scale <= FLOOR) {
                    break;
                }
            }
        }
        // Nothing holds all of it. The most there is: the floor, and as many columns as are
        // still columns.
        int columns = 1;
        while (columns < MOST_COLUMNS && columnWrap(width, FLOOR, columns + 1) >= NARROWEST_COLUMN) {
            columns++;
        }
        CardTextFit most = attempt(width, height, FLOOR, columns, false, measure);
        return new CardTextFit(most.scale, most.columns, Math.max(1, most.columnWrap),
                most.creditWrap, Math.max(0, most.capacity), false, Math.max(0, height), false);
    }

    private static CardTextFit attempt(
            int width, int height, float scale, int columns, boolean withStory, Measure measure) {
        int wrap = columnWrap(width, scale, columns);
        int creditWrap = Math.max(1, (int) Math.floor(width / (double) scale));
        int creditHeight = scaled(sum(measure.credit(creditWrap)), scale);
        int room = height - CREDIT_GAP - creditHeight;
        int capacity = room < 0 ? 0 : (int) Math.floor(room / (double) scale + 1e-6);
        boolean usable = wrap >= 1 && room >= 0 && (columns == 1 || wrap >= NARROWEST_COLUMN);
        if (!usable) {
            return new CardTextFit(scale, columns, wrap, creditWrap, capacity, withStory, height, false);
        }
        int[] lines = measure.lines(wrap, withStory);
        int[] starts = flow(lines, capacity, columns);
        if (starts.length == 0) {
            return new CardTextFit(scale, columns, wrap, creditWrap, capacity, withStory, height, false);
        }
        int tallest = 0;
        for (int column = 0; column < starts.length; column++) {
            int end = column + 1 < starts.length ? starts[column + 1] : lines.length;
            int sum = 0;
            for (int line = starts[column]; line < end; line++) {
                sum += lines[line];
            }
            tallest = Math.max(tallest, sum);
        }
        int used = scaled(tallest, scale) + CREDIT_GAP + creditHeight;
        return new CardTextFit(scale, starts.length, wrap, creditWrap, capacity, withStory, used,
                used <= height);
    }

    /**
     * Where each column starts, filling one before the next.
     * <p>The one flow there is, used to decide the fit and again to draw it, so the drawing
     * cannot break a column somewhere the measuring did not.
     *
     * @return the index of each column's first line, or nothing if the lines do not go in
     */
    public static int[] flow(int[] lines, int capacity, int columns) {
        int[] starts = new int[Math.max(1, columns)];
        int used = 1;
        int filled = 0;
        for (int index = 0; index < lines.length; index++) {
            int line = lines[index];
            if (line > capacity) {
                return new int[0];
            }
            if (filled + line > capacity) {
                if (used == columns) {
                    return new int[0];
                }
                starts[used++] = index;
                filled = 0;
            }
            filled += line;
        }
        return java.util.Arrays.copyOf(starts, used);
    }

    private static int columnWrap(int width, float scale, int columns) {
        int across = (width - COLUMN_GAP * (columns - 1)) / columns;
        return (int) Math.floor(across / (double) scale);
    }

    /** Font units to screen units, rounded out, so what is drawn is never taller than this. */
    public static int scaled(int units, float scale) {
        return (int) Math.ceil(units * (double) scale - 1e-6);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }
}
