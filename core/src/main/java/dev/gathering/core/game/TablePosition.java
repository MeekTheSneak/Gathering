package dev.gathering.core.game;

/**
 * Where a card sits on the table, as a grid square inside one seat's region.
 *
 * <p>Cards snap to a grid rather than floating freely. TTS-style physics is fun in TTS and
 * misery in Minecraft's interaction model, where you are aiming with a crosshair rather than
 * a mouse pointer on a desk. Snapping also makes a board legible from across the room, which
 * the in-world view depends on.
 *
 * <p>Coordinates are <em>relative to a seat's region</em>, not absolute on the table. Which
 * region a card is in lives in its {@link ZoneRef}, so moving a stolen creature to your own
 * side is a move to your battlefield with a new position - one act, one event, and control
 * falls out of it. That keeps "who is this in front of" and "where exactly" as separate
 * questions with separate answers.
 *
 * <p>The grid is bounded so a malformed position cannot ask the renderer to draw a card a
 * mile off the table. It is deliberately larger than any real board: the table never grows
 * in world footprint, so a busy board is drawn smaller rather than wider.
 */
public record TablePosition(int column, int row) {

    /** Wide enough for a board nobody has ever actually assembled. */
    public static final int MAX_COLUMN = 63;
    public static final int MAX_ROW = 63;

    /** How wide auto-placement runs before it wraps to the next row. */
    public static final int DEFAULT_ROW_WIDTH = 12;

    public static final TablePosition ORIGIN = new TablePosition(0, 0);

    public TablePosition {
        if (column < 0 || column > MAX_COLUMN || row < 0 || row > MAX_ROW) {
            throw new IllegalArgumentException(
                    "Table position out of bounds: (" + column + ", " + row + ")");
        }
    }

    public static TablePosition of(int column, int row) {
        return new TablePosition(column, row);
    }

    /**
     * The nth square in reading order, for dropping a card without aiming.
     *
     * <p>Used when something arrives on the battlefield without a chosen square - a token
     * being created, a card played by a verb rather than by a drag. Players rearrange
     * afterwards; the point is that a card always has a definite place rather than the
     * renderer inventing one differently on each client.
     */
    public static TablePosition slot(int index) {
        int bounded = Math.max(0, index);
        return new TablePosition(bounded % DEFAULT_ROW_WIDTH, Math.min(MAX_ROW, bounded / DEFAULT_ROW_WIDTH));
    }

    @Override
    public String toString() {
        return "(" + column + ", " + row + ")";
    }
}
