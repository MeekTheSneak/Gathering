package dev.gathering.core.ui;

/**
 * How long a notice stands over an open screen, and where on it.
 * <p>The mod answers a click straight away - "hold a deck", "not yours to add to" - and until
 * now it answered over the hotbar, which is underneath whatever screen asked the question.
 * The answer was there and nobody could read it. So a notice shown while a screen is open is
 * drawn on the screen instead, and this is the part of that with numbers in it.
 * <p>Across the top, because the bottom of every screen here is its row of buttons and the
 * one thing a notice must never do is cover the control the player is about to press. The top
 * edge is the other place nothing lives: a screen's own panel starts below it.
 * <p>It fades rather than vanishes. A line that disappears between frames reads as a glitch;
 * one that goes out over half a second reads as having been answered and moved on from.
 */
public final class NoticeLine {

    /** How long a notice is up for altogether, in milliseconds. */
    public static final long STANDS_MILLIS = 3_500L;

    /** How much of that is spent going out. */
    public static final long FADES_MILLIS = 750L;

    /**
     * The faintest a line is ever drawn, out of 255.
     * <p>Below this it is simply gone. Partly because eight of two hundred and fifty-five is
     * already nothing, and partly because the game's own text drawing reads an alpha under
     * four as "no alpha given" and draws the line fully opaque instead - so a fade that ran
     * all the way to zero would end with the notice flashing back to full brightness.
     */
    public static final int FAINTEST = 8;

    /** The gap between the writing and the edge of the box it sits in. */
    public static final int PADDING = 4;

    /** How far down from the top of the window the box sits. */
    public static final int FROM_THE_TOP = 6;

    /** And how much window is left either side of it, so it never runs to the edge. */
    public static final int SIDE_MARGIN = 8;

    private NoticeLine() {
    }

    /**
     * How bright the notice is now, out of 255, or zero once it is over.
     *
     * @param shownAtMillis when it was said
     * @param nowMillis the reading of the same clock this frame
     */
    public static int alphaAt(long shownAtMillis, long nowMillis) {
        long left = STANDS_MILLIS - (nowMillis - shownAtMillis);
        if (left <= 0) {
            return 0;
        }
        if (left >= STANDS_MILLIS) {
            // The clock has gone backwards, which a client can do across a world change. Full
            // brightness rather than nothing: a notice nobody has read yet is still news.
            return 255;
        }
        if (left >= FADES_MILLIS) {
            return 255;
        }
        int alpha = (int) (255L * left / FADES_MILLIS);
        return alpha < FAINTEST ? 0 : alpha;
    }

    /** Whether there is anything left to draw. */
    public static boolean isOver(long shownAtMillis, long nowMillis) {
        return alphaAt(shownAtMillis, nowMillis) == 0;
    }

    /**
     * The box the notice is drawn in, for a window this size and writing this wide.
     * <p>Centered across, pinned near the top, and never wider than the window it is on: the
     * smallest window the game produces is three hundred and twenty wide and the longest of
     * these lines is longer than that, so the writing is fitted to whatever room is left
     * rather than the box being grown to the writing.
     */
    public static Rect placeIn(int screenWidth, int screenHeight, int textWidth, int lineHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return Rect.NONE;
        }
        int widest = Math.max(1, screenWidth - SIDE_MARGIN * 2);
        int width = Math.min(widest, Math.max(1, textWidth) + PADDING * 2);
        int height = Math.min(screenHeight, Math.max(1, lineHeight) + PADDING * 2);
        int x = (screenWidth - width) / 2;
        // Pushed back up if the window is too short to have a top margin at all, which is
        // what a window gets at 200% interface scale on a small screen.
        int y = Math.max(0, Math.min(FROM_THE_TOP, screenHeight - height));
        return new Rect(x, y, width, height);
    }

    /** How much room the writing itself has inside that box. */
    public static int roomForWriting(int screenWidth) {
        return Math.max(1, screenWidth - SIDE_MARGIN * 2 - PADDING * 2);
    }
}
