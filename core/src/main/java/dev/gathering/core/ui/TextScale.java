package dev.gathering.core.ui;

/**
 * How far text may be shrunk, and what counts as somebody having made a mistake.
 * <p>Text on this board is drawn one of two ways: fitted into a width, or drawn at a scale
 * worked out beforehand so a set of labels all come out the same size. The two take the same
 * number of arguments and differ in one position - an {@code int} width against a
 * {@code float} scale - and Java widens an int to a float without a word. Pass the width
 * where the scale goes and the text is drawn twenty times too big, silently, in whatever
 * color it was already using.
 * <p>That happened: a power and toughness meant for the corner of a card was drawn at
 * eighteen times its size, covering the whole board in letterforms so large they did not read
 * as letters at all. Nothing failed. The build was green, the game tests passed, and the only
 * evidence was forty thousand cream pixels in a screenshot.
 * <p>So a scale outside the range anything here honestly uses is not clamped quietly - it is
 * counted, and the scripted run fails on a count above zero. Drawing carries on at a sane
 * size, because a crash in a render loop is worse than a label an eighth too small.
 * <p>Pure: no Minecraft, so the rule is tested rather than looked at.
 */
public final class TextScale {

    /**
     * The smallest text is ever drawn.
     * <p>Below this a label stops being read and starts being a smudge that says something is
     * there. Shared with the callers that shrink to fit, so "as small as it goes" means one
     * thing across the mod.
     */
    public static final float SMALLEST = 0.6f;

    /** Full size. */
    public static final float FULL = 1.0f;

    /**
     * The largest anything is drawn at.
     * <p>It used to be full size: text shrank and never grew, so anything over one was the
     * mistake. Writing on a card grows now - it is drawn against the card rather than against
     * the screen, so zooming in makes it bigger, which is what was asked for - and the
     * ceiling moved up to let it.
     * <p>Still well under any width. The mistake this catches is an int width arriving where
     * a float scale goes, and a width is a number of pixels: the narrowest thing in the mod
     * anybody fits text into is a good deal wider than this. So the guard still fires on
     * every one of them and no longer fires on honest growth.
     */
    public static final float LARGEST = 2.0f;

    private TextScale() {
    }

    /**
     * Whether this is a scale anything in the mod meant to ask for.
     * <p>Much larger than full size is the mistake this exists to catch, because an int width
     * arriving here is always a good deal larger than {@link #LARGEST}. Zero, negative and
     * not-a-number are the other ways a scale goes wrong - a degenerate transform draws
     * geometry nobody can predict.
     */
    public static boolean isSane(float scale) {
        return !Float.isNaN(scale) && scale > 0f && scale <= LARGEST;
    }

    /** The nearest scale worth drawing at, for carrying on after a mistake. */
    public static float sane(float scale) {
        if (Float.isNaN(scale) || scale <= 0f) {
            return SMALLEST;
        }
        return Math.min(LARGEST, Math.max(SMALLEST, scale));
    }
}
