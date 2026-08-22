package dev.gathering.core.ui;

/**
 * Where everything goes on the seated view, at whatever size the window happens to be.
 *
 * <p>Four bands, in the order a player looks at them: the other seats across the top, the
 * table surface in the middle, your own zones down the side of it, and your hand along the
 * bottom. That is the shape of sitting at a table, and the point of stating it here rather
 * than in the screen is that "at whatever size the window happens to be" can then be checked
 * against every size rather than the one it was written at.
 *
 * <p>The surface is a grid of card squares. How many fit is a function of the space, not a
 * constant: a busy board is drawn smaller rather than wider, because the table never grows.
 */
public record TableScreenLayout(
        Rect opponents,
        Rect surface,
        Rect zones,
        Rect hand,
        Rect actions,
        int squareWidth,
        int squareHeight,
        int columns,
        int rows) {

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
    private static final int SQUARE_MIN = 26;
    private static final int SQUARE_MAX = 74;

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
        int middleHeight = Math.max(SQUARE_MIN, hand.y() - GAP - middleTop);

        // The zone column goes beside the surface when there is room for both, and under the
        // hand's own margin when there is not - a table you cannot see is worse than one
        // whose graveyard count you have to go looking for.
        boolean roomForZones = width - MARGIN * 2 > ZONE_WIDTH + GAP + SQUARE_MIN * 3;
        Rect zones = roomForZones
                ? new Rect(MARGIN, middleTop, ZONE_WIDTH, middleHeight)
                : Rect.NONE;

        int surfaceLeft = roomForZones ? zones.right() + GAP : MARGIN;
        Rect surface = new Rect(
                surfaceLeft, middleTop, width - MARGIN - surfaceLeft, middleHeight);

        int squareHeight = clamp(surface.height() / 3, SQUARE_MIN, SQUARE_MAX);
        int squareWidth = Math.max(1, Math.round(squareHeight * CARD_ASPECT));
        int columns = Math.max(1, surface.width() / (squareWidth + 2));
        int rows = Math.max(1, surface.height() / (squareHeight + 2));

        return new TableScreenLayout(
                opponents, surface, zones, hand, actions, squareWidth, squareHeight, columns, rows);
    }

    /** Where a square of the surface grid sits on screen. */
    public Rect squareAt(int column, int row) {
        return new Rect(
                surface.x() + column * (squareWidth + 2),
                surface.y() + row * (squareHeight + 2),
                squareWidth,
                squareHeight);
    }

    /** Which square of the grid a point is over, or null if it is off the surface. */
    public int[] squareOf(int x, int y) {
        if (!surface.contains(x, y)) {
            return null;
        }
        int column = (x - surface.x()) / (squareWidth + 2);
        int row = (y - surface.y()) / (squareHeight + 2);
        if (column >= columns || row >= rows) {
            return null;
        }
        return new int[] {column, row};
    }

    /** How many squares the surface shows at once. */
    public int visibleSquares() {
        return columns * rows;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
