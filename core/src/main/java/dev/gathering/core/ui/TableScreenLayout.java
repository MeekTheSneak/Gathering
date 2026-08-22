package dev.gathering.core.ui;

import dev.gathering.core.game.TablePosition;

/**
 * Where everything goes on the seated view, at whatever size the window happens to be.
 *
 * <p>Four bands, in the order a player looks at them: the other seats across the top, the
 * table surface in the middle, your own zones down the side of it, and your hand along the
 * bottom. That is the shape of sitting at a table, and the point of stating it here rather
 * than in the screen is that "at whatever size the window happens to be" can then be checked
 * against every size rather than the one it was written at.
 *
 * <p>The surface is not a grid. A card goes wherever it was dropped, at whatever angle it was
 * left at, and may overlap its neighbours as much as a player likes - that is what a table
 * is, and a grid is a spreadsheet with card art in it. So the only thing this has to say
 * about the surface is how big a card is drawn on it and how a stored
 * {@link TablePosition} and a screen pixel convert into each other.
 *
 * <p>That conversion addresses a card's top-left corner within the surface <em>inset by one
 * card</em>, rather than its centre within the whole surface. Position zero is then flush
 * against the left edge and position one is flush against the right, every card is fully on
 * the table without anything having to clamp it afterwards, and the two directions are exact
 * inverses - which matters because one of them draws the card and the other decides what the
 * cursor is pointing at, and a table where those disagree drops cards next to where you
 * aimed.
 */
public record TableScreenLayout(
        Rect opponents,
        Rect surface,
        Rect zones,
        Rect hand,
        Rect actions,
        int cardWidth,
        int cardHeight) {

    /** The printed aspect ratio, 2.5 by 3.5 inches. */
    private static final float CARD_ASPECT = 488f / 680f;

    private static final int MARGIN = 6;
    private static final int GAP = 6;

    /** Zones column: library, graveyard, exile and command zone, stacked. */
    private static final int ZONE_WIDTH = 76;

    private static final int ACTION_HEIGHT = 22;

    /** A hand card small enough to fit a grip of fifteen and large enough to be a card. */
    private static final int HAND_HEIGHT_MIN = 54;
    private static final int HAND_HEIGHT_MAX = 116;
    private static final float HAND_HEIGHT_FRACTION = 0.22f;

    private static final int OPPONENTS_HEIGHT_MIN = 22;
    private static final float OPPONENTS_HEIGHT_FRACTION = 0.14f;

    /** A card on the surface, at the size it stops being identifiable. */
    private static final int CARD_HEIGHT_MIN = 26;
    private static final int CARD_HEIGHT_MAX = 74;

    public static TableScreenLayout of(int screenWidth, int screenHeight, int opponentCount) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        int opponentsHeight = Math.max(OPPONENTS_HEIGHT_MIN,
                Math.round(height * OPPONENTS_HEIGHT_FRACTION));
        // Every other seat needs a line; a big pod gets a taller strip rather than a cramped
        // one, and the surface gives up the room because the surface can shrink and a name
        // cannot.
        opponentsHeight = Math.max(opponentsHeight, OPPONENTS_HEIGHT_MIN * Math.max(1, opponentCount) / 2);
        opponentsHeight = Math.min(opponentsHeight, height / 3);

        int handHeight = clamp(Math.round(height * HAND_HEIGHT_FRACTION),
                HAND_HEIGHT_MIN, HAND_HEIGHT_MAX);
        handHeight = Math.min(handHeight, height / 3);

        Rect opponents = new Rect(MARGIN, MARGIN, width - MARGIN * 2, opponentsHeight);
        Rect actions = new Rect(MARGIN, height - MARGIN - ACTION_HEIGHT, width - MARGIN * 2, ACTION_HEIGHT);
        Rect hand = new Rect(
                MARGIN, actions.y() - GAP - handHeight, width - MARGIN * 2, handHeight);

        int middleTop = opponents.bottom() + GAP;
        int middleHeight = Math.max(CARD_HEIGHT_MIN, hand.y() - GAP - middleTop);

        // The zone column goes beside the surface when there is room for both, and under the
        // hand's own margin when there is not - a table you cannot see is worse than one
        // whose graveyard count you have to go looking for.
        boolean roomForZones = width - MARGIN * 2 > ZONE_WIDTH + GAP + CARD_HEIGHT_MIN * 3;
        Rect zones = roomForZones
                ? new Rect(MARGIN, middleTop, ZONE_WIDTH, middleHeight)
                : Rect.NONE;

        int surfaceLeft = roomForZones ? zones.right() + GAP : MARGIN;
        Rect surface = new Rect(
                surfaceLeft, middleTop, width - MARGIN - surfaceLeft, middleHeight);

        // A busy board is drawn smaller rather than wider, because the table never grows.
        int cardHeight = clamp(surface.height() / 3, CARD_HEIGHT_MIN, CARD_HEIGHT_MAX);
        cardHeight = Math.min(cardHeight, Math.max(1, surface.height()));
        int cardWidth = Math.max(1, Math.round(cardHeight * CARD_ASPECT));
        cardWidth = Math.min(cardWidth, Math.max(1, surface.width()));

        return new TableScreenLayout(opponents, surface, zones, hand, actions, cardWidth, cardHeight);
    }

    /** Where a card at this position is drawn, upright. Rotation is the renderer's business. */
    public Rect cardAt(TablePosition position) {
        return new Rect(
                surface.x() + (int) Math.round(position.acrossFraction() * placeableWidth()),
                surface.y() + (int) Math.round(position.downFraction() * placeableHeight()),
                cardWidth,
                cardHeight);
    }

    /**
     * The position a card would have if its top-left corner were put here.
     *
     * <p>Clamps rather than refusing: a drag that ends past the edge of the table puts the
     * card against the edge, which is what happens when you shove a card across a real one.
     * Callers that care whether the cursor was actually over the surface ask
     * {@link #isOnSurface}.
     */
    public TablePosition positionFor(int screenX, int screenY) {
        return TablePosition.fraction(
                (double) (screenX - surface.x()) / placeableWidth(),
                (double) (screenY - surface.y()) / placeableHeight());
    }

    /** The position for a drop that grabbed the card {@code grabX, grabY} in from its corner. */
    public TablePosition positionForDrop(int screenX, int screenY, int grabX, int grabY) {
        return positionFor(screenX - grabX, screenY - grabY);
    }

    public boolean isOnSurface(int x, int y) {
        return surface.contains(x, y);
    }

    /**
     * How far a card's corner can travel across the surface, in pixels.
     *
     * <p>Never zero, so the conversion cannot divide by nothing on a window too small to hold
     * a single card beside itself.
     */
    private int placeableWidth() {
        return Math.max(1, surface.width() - cardWidth);
    }

    private int placeableHeight() {
        return Math.max(1, surface.height() - cardHeight);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
