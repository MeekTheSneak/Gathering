package dev.gathering.core.ui;

/**
 * The two things on screen that are not the table.
 *
 * <p>This used to describe four bands with a play surface panelled into the middle of them,
 * which is a menu with a game in it. The table is the screen now - or the table in the world
 * is - and the camera decides which part of it you are looking at, so all that is left here is
 * the furniture that cannot live on the felt.
 *
 * <p>Your hand is the only real piece of it. It is private, it is fanned rather than laid out,
 * and it belongs to you rather than to a place on the table, so it sits along the bottom edge
 * where every digital card game and Tabletop Simulator itself put it.
 *
 * <p>There is no bar of key hints any more. It cost a strip of screen on every frame of every
 * game to tell people something they learn in the first minute and then read past for ever;
 * the full list is a key away, which is where a list you want twice belongs.
 */
public record TableScreenLayout(Rect felt, Rect hand, Rect status) {

    /** The strip along the top carrying names, life, and whose turn it is. */
    private static final int STATUS_HEIGHT = 16;

    /**
     * How much of the screen the hand gets.
     *
     * <p>Enough for a card and the room it needs to rise into when the cursor is on it - the
     * fan reserves part of its own strip for exactly that, so this is deeper than a card.
     */
    private static final int HAND_HEIGHT_MIN = 76;
    private static final int HAND_HEIGHT_MAX = 168;
    private static final float HAND_HEIGHT_FRACTION = 0.28f;

    /** The layout for somebody with a hand to hold, which is anybody in a chair. */
    public static TableScreenLayout of(int screenWidth, int screenHeight) {
        return of(screenWidth, screenHeight, true);
    }

    /**
     * The layout, with or without a strip along the bottom for a hand.
     *
     * <p>A spectator has no hand and never will while they are watching, and a strip reserved
     * for one costs them twice: the board is fitted into a shorter window and pushed up into
     * the status row, and the fifth of the felt under the strip stops answering the mouse -
     * so a graveyard that happens to lie there cannot be opened by the one person at the
     * table whose whole job is reading it.
     *
     * <p>Asked of the seat rather than of the cards in it. Somebody who has played their last
     * card still has a hand; it is empty, and the strip is where the next one arrives.
     */
    public static TableScreenLayout of(int screenWidth, int screenHeight, boolean holdingAHand) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        int handHeight = holdingAHand
                ? Math.min(clamp(Math.round(height * HAND_HEIGHT_FRACTION),
                        HAND_HEIGHT_MIN, HAND_HEIGHT_MAX), height / 2)
                : 0;

        // The felt goes under the hand rather than stopping at it. A table that ended where
        // your cards begin would have a strip you could see across but never put anything on,
        // and panning would keep sliding cards under a lip.
        return new TableScreenLayout(
                new Rect(0, 0, width, height),
                handHeight <= 0 ? Rect.NONE : new Rect(0, height - handHeight, width, handHeight),
                new Rect(0, 0, width, Math.min(STATUS_HEIGHT, height / 4)));
    }

    /** Whether a point is on the table rather than on the hand or the strip above it. */
    public boolean isOnFelt(int x, int y) {
        return felt.contains(x, y) && !hand.contains(x, y) && !status.contains(x, y);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
