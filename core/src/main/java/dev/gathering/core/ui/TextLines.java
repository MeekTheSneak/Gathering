package dev.gathering.core.ui;

/**
 * Lines of writing at one size, one under another, in numbers.
 * <p>The mod's text grows with the size a player asks for, and the writing is drawn centered on
 * the middle of where a full-size line would have gone - so a line drawn twice as big grows half
 * its extra height upward and half downward. A panel that went on stepping from line to line by
 * the font's own height drew every enlarged line through its neighbors: at 200% the board's game
 * log, its table talk and the lesson's heading each printed one line across the next. Every
 * panel that steps through lines of enlarged text takes its spacing and its starting point from
 * here, so the step and the size cannot disagree.
 * <p>Pure: worked out from the font's line height, the scale the lines are drawn at, and the gap
 * wanted between them.
 *
 * @param lineHeight the font's own line height
 * @param scale      the size the lines are drawn at, one being the font's own
 * @param gap        clear space under each line
 */
public record TextLines(int lineHeight, float scale, int gap) {

    public TextLines {
        lineHeight = Math.max(1, lineHeight);
        // A size a font can honestly be drawn at: see TextScale. A NaN or a width passed where a
        // scale goes would otherwise space the lines a screen apart.
        scale = TextScale.sane(scale);
        gap = Math.max(0, gap);
    }

    /** How tall one line comes out, rounded up to whole pixels. */
    public int drawnHeight() {
        return (int) Math.ceil(lineHeight * scale - 1e-4f);
    }

    /** From the top of one line to the top of the next. */
    public int pitch() {
        return drawnHeight() + gap;
    }

    /**
     * Where to hand a drawing call so the line's writing starts at {@code top}.
     * <p>The mod's drawing centers enlarged writing on the middle of a full-size line, so a line
     * asked for at {@code top} would start half its growth above it - over whatever is there.
     * This moves the call down by that half, so the line sits inside the space it was given.
     */
    public int drawY(int top) {
        return top + Math.round((lineHeight * scale - lineHeight) / 2f);
    }
}
