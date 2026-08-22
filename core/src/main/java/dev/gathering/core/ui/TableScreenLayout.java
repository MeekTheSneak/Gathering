package dev.gathering.core.ui;

/**
 * The three things on screen that are not the table.
 *
 * <p>This used to describe four bands with a play surface panelled into the middle of them,
 * which is a menu with a game in it. The table is the screen now: the felt runs edge to edge
 * and the camera decides which part of it you are looking at, so all that is left here is the
 * furniture that cannot live on the felt.
 *
 * <p>Your hand is one of those. It is private, it is fanned rather than laid out, and it
 * belongs to you rather than to a place on the table - so it sits along the bottom edge, which
 * is where every digital card game and Tabletop Simulator itself put it.
 */
public record TableScreenLayout(Rect felt, Rect hand, Rect actions) {

    private static final int ACTION_HEIGHT = 22;

    /** A hand card small enough to fit a grip of fifteen and large enough to be a card. */
    private static final int HAND_HEIGHT_MIN = 54;
    private static final int HAND_HEIGHT_MAX = 116;
    private static final float HAND_HEIGHT_FRACTION = 0.20f;

    public static TableScreenLayout of(int screenWidth, int screenHeight) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        int handHeight = clamp(Math.round(height * HAND_HEIGHT_FRACTION),
                HAND_HEIGHT_MIN, HAND_HEIGHT_MAX);
        handHeight = Math.min(handHeight, height / 3);

        Rect actions = new Rect(0, height - ACTION_HEIGHT, width, ACTION_HEIGHT);
        Rect hand = new Rect(0, actions.y() - handHeight, width, handHeight);

        // The felt goes under the hand rather than stopping at it. A table that ended where
        // your cards begin would have a strip you could see across but never put anything on,
        // and panning would keep sliding cards under a lip.
        return new TableScreenLayout(new Rect(0, 0, width, height), hand, actions);
    }

    /** Whether a point is on the table rather than on the hand or the bar under it. */
    public boolean isOnFelt(int x, int y) {
        return felt.contains(x, y) && !hand.contains(x, y) && !actions.contains(x, y);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
