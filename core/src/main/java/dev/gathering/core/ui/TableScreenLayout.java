package dev.gathering.core.ui;

/**
 * The two things on screen that are not the table.
 * <p>This used to describe four bands with a play surface panelled into the middle of them,
 * which is a menu with a game in it. The table is the screen now - or the table in the world
 * is - and the camera decides which part of it you are looking at, so all that is left here is
 * the furniture that cannot live on the felt.
 * <p>Your hand is the only real piece of it. It is private, it is fanned rather than laid out,
 * and it belongs to you rather than to a place on the table, so it sits along the bottom edge
 * where every digital card game and Tabletop Simulator itself put it.
 * <p>There is no bar of key hints any more. It cost a strip of screen on every frame of every
 * game to tell people something they learn in the first minute and then read past for ever;
 * the full list is a key away, which is where a list you want twice belongs.
 */
public record TableScreenLayout(Rect felt, Rect hand, Rect status) {

    /**
     * The strip along the top carrying names, life, and whose turn it is.
     * <p>It was sixteen, which is exactly twice the panel border most looks draw - so the
     * panel's top edge met its bottom edge with no middle between them, and on the looks
     * whose border is a heavy band that came out as a squashed strip rather than a bar.
     * Reported as "the top bar on in the table when using the future sight theme".
     * <p>How thick that border really is belongs to the art rather than to this file, and it
     * differs between looks, so the caller passes in what the look somebody is wearing needs
     * and this is the floor under it: room for the writing, whatever the frame wants.
     */
    private static final int STATUS_HEIGHT = SpriteFrames.ROOMY_ENOUGH;

    /**
     * How much of the screen the hand gets.
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
     * The same, with the strip along the top made tall enough for the frame it is drawn in.
     * <p>The frame is a picture in a resource pack and its border is whatever the artist drew
     * it at. A box shorter than a border's two edges has no middle for the middle of the
     * picture to tile into, so the game runs the corners together and what comes out is not
     * the bar anybody painted. The client reads the number off the sprite it is about to draw
     * and passes it here; nothing else has to know a number at all.
     *
     * @param needsForItsFrame the shortest the strip may be for this look's panel, or zero
     */
    public static TableScreenLayout of(
            int screenWidth, int screenHeight, boolean holdingAHand, int needsForItsFrame) {
        TableScreenLayout fitted = of(screenWidth, screenHeight, holdingAHand);
        return fitted.withStatusAtLeast(needsForItsFrame, screenHeight);
    }

    /**
     * The layout, with or without a strip along the bottom for a hand.
     * <p>A spectator has no hand and never will while they are watching, and a strip reserved
     * for one costs them twice: the board is fitted into a shorter window and pushed up into
     * the status row, and the fifth of the felt under the strip stops answering the mouse -
     * so a graveyard that happens to lie there cannot be opened by the one person at the
     * table whose whole job is reading it.
     * <p>Asked of the seat rather than of the cards in it. Somebody who has played their last
     * card still has a hand; it is empty, and the strip is where the next one arrives.
     */
    public static TableScreenLayout of(int screenWidth, int screenHeight, boolean holdingAHand) {
        int height = Math.max(1, screenHeight);
        int handHeight = holdingAHand
                ? Math.min(clamp(Math.round(height * HAND_HEIGHT_FRACTION),
                        HAND_HEIGHT_MIN, HAND_HEIGHT_MAX), height / 2)
                : 0;
        return withStrip(screenWidth, screenHeight, handHeight);
    }

    /**
     * How deep the strip along the bottom of a replay is.
     * <p>A row of three buttons and a bar. Deliberately nothing like a hand: somebody watching
     * a game back has no cards, and giving the scrubber a hand's worth of screen would take a
     * fifth of the table away to hold four controls.
     */
    public static final int SCRUBBER_HEIGHT = 26;

    /**
     * The layout for somebody watching a finished game: no hand, but a scrubber.
     * <p>The strip goes in the same slot the hand would, so everything already fitted around
     * a hand - the camera, the chat lines, the felt's hit-testing - fits around this without
     * knowing what is in it.
     */
    public static TableScreenLayout watching(int screenWidth, int screenHeight) {
        return withStrip(screenWidth, screenHeight,
                Math.min(SCRUBBER_HEIGHT, Math.max(1, screenHeight) / 2));
    }

    /** The same, for a look whose panel needs more room than the default. */
    public static TableScreenLayout watching(
            int screenWidth, int screenHeight, int needsForItsFrame) {
        return watching(screenWidth, screenHeight)
                .withStatusAtLeast(needsForItsFrame, screenHeight);
    }

    /**
     * This layout with the top strip grown to fit a frame, if it can be grown.
     * <p>Never past a quarter of the window: a look with a heavy border on a very short
     * window would otherwise spend a third of the table on a name bar, and a squashed frame
     * is the lesser of those two. The cap is the same one the strip is fitted under to begin
     * with, so on any window somebody would really play at nothing is capped at all.
     */
    private TableScreenLayout withStatusAtLeast(int needsForItsFrame, int screenHeight) {
        int room = Math.max(1, screenHeight) / 4;
        int wanted = Math.min(Math.max(status.height(), needsForItsFrame), room);
        return wanted == status.height()
                ? this
                : new TableScreenLayout(felt, hand, new Rect(status.x(), status.y(),
                        status.width(), wanted));
    }

    private static TableScreenLayout withStrip(int screenWidth, int screenHeight, int stripHeight) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        // The felt goes under the strip rather than stopping at it. A table that ended where
        // your cards begin would have a strip you could see across but never put anything on,
        // and panning would keep sliding cards under a lip.
        return new TableScreenLayout(
                new Rect(0, 0, width, height),
                stripHeight <= 0 ? Rect.NONE : new Rect(0, height - stripHeight, width, stripHeight),
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
