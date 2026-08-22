package dev.gathering.core.game;

/**
 * Where a card sits on the table, and which way round it is.
 *
 * <p>Anywhere. Cards are objects you pick up and put down, not entries in a grid: you drop
 * one half-overlapping another to show it is attached, angle it because you feel like it,
 * fan a pile out. That is what playing with cards on a table is, and a grid is a spreadsheet
 * with card art in it.
 *
 * <p>Fixed point rather than floating point, in {@link #SPAN} units across the table. A
 * position is written to the event log, sent over the network and folded through undo, and
 * two clients that disagree in the last bit of a float would drift apart over a long game.
 * At this resolution a unit is far below a pixel on any screen, so it is continuous as far
 * as anybody using it is concerned and exact as far as everything storing it is concerned.
 *
 * <p>Coordinates are <em>relative to a seat's region</em>, not absolute on the table. Which
 * region a card is in lives in its {@link ZoneRef}, so moving a stolen creature to your own
 * side is a move to your battlefield with a new position - one act, one event, and control
 * falls out of it.
 *
 * <p>Rotation is separate from tapping. Tapping is a game state a card is in and a verb with
 * a name; turning a card because you like it that way is neither, and a table where the only
 * angle available is ninety degrees is not a table.
 */
public record TablePosition(int x, int y, int rotation) {

    /** How many units across a seat's region is. Fine enough to be continuous in use. */
    public static final int SPAN = 10_000;

    public static final TablePosition ORIGIN = new TablePosition(0, 0, 0);

    /** A quarter turn, which is what tapping looks like and what a nudge key steps by. */
    public static final int QUARTER_TURN = 90;

    public TablePosition {
        if (x < 0 || x > SPAN || y < 0 || y > SPAN) {
            throw new IllegalArgumentException("Table position out of bounds: (" + x + ", " + y + ")");
        }
        // Any angle is a legal angle; it is only ever wrapped, never rejected, because
        // "your card is at 361 degrees" is not a thing to tell somebody.
        rotation = Math.floorMod(rotation, 360);
    }

    public static TablePosition of(int x, int y) {
        return new TablePosition(x, y, 0);
    }

    public static TablePosition of(int x, int y, int rotation) {
        return new TablePosition(x, y, rotation);
    }

    /** From a fraction of the region, which is how a screen and a table top both think. */
    public static TablePosition fraction(double across, double down) {
        return new TablePosition(clamp(across), clamp(down), 0);
    }

    public TablePosition movedTo(int newX, int newY) {
        return new TablePosition(bound(newX), bound(newY), rotation);
    }

    public TablePosition rotatedTo(int degrees) {
        return new TablePosition(x, y, degrees);
    }

    public TablePosition turnedBy(int degrees) {
        return new TablePosition(x, y, rotation + degrees);
    }

    /** Where across the region this is, as a fraction, for anything that has to draw it. */
    public double acrossFraction() {
        return (double) x / SPAN;
    }

    public double downFraction() {
        return (double) y / SPAN;
    }

    /** How many unaimed spots there are before the fan starts a second layer. */
    public static final int FAN_ACROSS = 10;
    public static final int FAN_DOWN = 10;

    private static final double FAN_FIRST_ACROSS = 0.06;
    private static final double FAN_FIRST_DOWN = 0.06;
    private static final double FAN_STEP_ACROSS = 0.088;
    private static final double FAN_STEP_DOWN = 0.085;
    private static final double FAN_LAYER_NUDGE = 0.018;
    private static final int FAN_LAYERS = 5;

    /** How many distinct spots {@link #unaimed(int)} can hand out before it starts repeating. */
    public static final int FAN_SPOTS = FAN_ACROSS * FAN_DOWN * FAN_LAYERS;

    /**
     * Somewhere sensible for a card that arrived without being aimed.
     *
     * <p>A token appearing, a card put down by a verb rather than by a hand. They fan out
     * across the surface rather than stacking exactly, so five tokens are visibly five - and
     * a player then moves them wherever they actually want them.
     *
     * <p>Past the first hundred the fan starts again slightly offset rather than wrapping onto
     * itself, because a board with more permanents than there are fan spots should still show
     * every one of them. Past {@link #FAN_SPOTS} it does repeat, which is a board nobody has
     * ever had and a deliberate overlap is legal anyway.
     */
    public static TablePosition unaimed(int index) {
        int bounded = Math.max(0, index);
        int perLayer = FAN_ACROSS * FAN_DOWN;
        int layer = Math.min(bounded / perLayer, FAN_LAYERS - 1);
        int within = bounded % perLayer;
        double nudge = layer * FAN_LAYER_NUDGE;
        return new TablePosition(
                clamp(FAN_FIRST_ACROSS + (within % FAN_ACROSS) * FAN_STEP_ACROSS + nudge),
                clamp(FAN_FIRST_DOWN + (within / FAN_ACROSS) * FAN_STEP_DOWN + nudge),
                0);
    }

    private static int bound(int value) {
        return Math.max(0, Math.min(SPAN, value));
    }

    private static int clamp(double fraction) {
        return bound((int) Math.round(fraction * SPAN));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + " @" + rotation + ")";
    }
}
