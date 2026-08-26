package dev.gathering.core.ui;

/**
 * How far text may be shrunk, and what counts as somebody having made a mistake.
 *
 * <p>Text on this board is drawn one of two ways: fitted into a width, or drawn at a scale
 * worked out beforehand so a set of labels all come out the same size. The two take the same
 * number of arguments and differ in one position - an {@code int} width against a
 * {@code float} scale - and Java widens an int to a float without a word. Pass the width
 * where the scale goes and the text is drawn twenty times too big, silently, in whatever
 * colour it was already using.
 *
 * <p>That happened: a power and toughness meant for the corner of a card was drawn at
 * eighteen times its size, covering the whole board in letterforms so large they did not read
 * as letters at all. Nothing failed. The build was green, the game tests passed, and the only
 * evidence was forty thousand cream pixels in a screenshot.
 *
 * <p>So a scale outside the range anything here honestly uses is not clamped quietly - it is
 * counted, and the scripted run fails on a count above zero. Drawing carries on at a sane
 * size, because a crash in a render loop is worse than a label an eighth too small.
 *
 * <p>Pure: no Minecraft, so the rule is tested rather than looked at.
 */
public final class TextScale {

    /**
     * The smallest text is ever drawn.
     *
     * <p>Below this a label stops being read and starts being a smudge that says something is
     * there. Shared with the callers that shrink to fit, so "as small as it goes" means one
     * thing across the mod.
     */
    public static final float SMALLEST = 0.6f;

    /** Full size. Nothing here draws text larger than the font: it shrinks, it never grows. */
    public static final float FULL = 1.0f;

    private TextScale() {
    }

    /**
     * Whether this is a scale anything in the mod meant to ask for.
     *
     * <p>Larger than full size is the mistake this exists to catch, because no caller wants
     * it and an int width arriving here is always larger. Zero, negative and not-a-number are
     * the other ways a scale goes wrong - a degenerate transform draws geometry nobody can
     * predict.
     */
    public static boolean isSane(float scale) {
        return !Float.isNaN(scale) && scale > 0f && scale <= FULL;
    }

    /** The nearest scale worth drawing at, for carrying on after a mistake. */
    public static float sane(float scale) {
        if (Float.isNaN(scale) || scale <= 0f) {
            return SMALLEST;
        }
        return Math.min(FULL, Math.max(SMALLEST, scale));
    }
}
