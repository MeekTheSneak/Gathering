package dev.gathering.core.ui;

/**
 * When a label shrunk to fit is still worth writing.
 * <p>A label written on the felt is drawn smaller than its natural size when the space is
 * tight, and past some point it stops being a word and becomes a smudge - "Mulligan" arrives
 * as "Mulli_a" and costs the reader more than the blank box would have. The point is not a
 * fraction of the natural size on its own: it is where the font's own pixels stop getting a
 * screen pixel each. A word at three-quarter size is crisp on an interface drawn at two
 * screen pixels per interface pixel and mush on one drawn at one, because in the second case
 * three font pixels are being asked to share two.
 * <p>So the rule is one screen pixel per font pixel, with a floor: however sharp the screen,
 * a word written at less than half size is too small to bother anybody with.
 */
public final class Legibility {

    /** However many screen pixels are on offer, smaller than this is not worth writing. */
    private static final double SMALLEST_WORTH_WRITING = 0.5;

    private Legibility() {
    }

    /**
     * The smallest fraction of its natural size a label may be written at.
     *
     * @param guiScale screen pixels per interface pixel, as the window reports it
     */
    public static double smallestWorthWriting(double guiScale) {
        return Math.max(SMALLEST_WORTH_WRITING, 1.0 / Math.max(1.0, guiScale));
    }

    /**
     * How much room a label of this natural width needs before writing it is worth doing.
     * <p>Callers with a set of labels to write should ask this of the longest of them and
     * write all or none, because a column in which only the short names appear reads as
     * those zones being special rather than as a board too small to write on.
     */
    public static int roomToWrite(int naturalWidth, double guiScale) {
        if (naturalWidth <= 0) {
            return 0;
        }
        return (int) Math.ceil(naturalWidth * smallestWorthWriting(guiScale));
    }
}
