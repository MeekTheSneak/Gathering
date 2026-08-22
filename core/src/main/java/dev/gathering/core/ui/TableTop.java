package dev.gathering.core.ui;

import dev.gathering.core.table.TableCell;
import java.util.Optional;

/**
 * The table's playing surface as a plane in the world, and how to hit it.
 *
 * <p>The seated screen turns a card's position into a rectangle because the felt is the
 * screen. Playing on the block itself needs the other conversion: the player is looking at a
 * flat surface somewhere in the world through a cursor, and something has to say which point
 * of the shared surface that cursor is over. That is a ray against a horizontal plane, and it
 * is the whole of the arithmetic - which is why it lives here, in plain doubles, rather than
 * inside a renderer where it could only be checked by looking at it.
 *
 * <p>Surface coordinates are the same ones {@link TableSurface} works in, so a hit comes back
 * ready to hand to {@code seatAt} or {@code positionOn} with nothing in between.
 *
 * @param westX  the world x of the surface's (0, 0) corner
 * @param topY   the world y the surface sits at
 * @param northZ the world z of the surface's (0, 0) corner
 * @param span   how many blocks across the surface is
 */
public record TableTop(double westX, double topY, double northZ, double span) {

    /**
     * How far in from the block's edge the playing surface starts.
     *
     * <p>The table never grows in world footprint, so the board is inset rather than run to
     * the edge - a card half over the lip would be a card you cannot see the bottom of.
     */
    public static final double MARGIN = 0.12;

    /** Just above the felt, so what is on the table is on it rather than in it. */
    public static final double SURFACE_HEIGHT = 15.02 / 16.0;

    /** How many blocks of playing surface a table has, once the margin is taken off. */
    public static final double SPAN_BLOCKS = TableCell.BLOCKS_PER_TABLE - MARGIN * 2;

    /**
     * The surface of the table whose owning corner is this block.
     *
     * <p>Everything that draws on the table and everything that works out what a player is
     * pointing at builds one of these, so the picture and the pointing cannot end up measured
     * from different corners.
     */
    public static TableTop forCorner(double cornerX, double cornerY, double cornerZ) {
        return new TableTop(
                cornerX + MARGIN, cornerY + SURFACE_HEIGHT, cornerZ + MARGIN, SPAN_BLOCKS);
    }

    public TableTop {
        if (span <= 0) {
            throw new IllegalArgumentException("a table with no surface: span " + span);
        }
    }

    /** A point on the shared surface, in {@link TableSurface} units. */
    public record Spot(double x, double y) {
    }

    /**
     * Where a ray meets the surface, if it meets it at all.
     *
     * <p>Empty for the three ways a ray can fail to land on a table: parallel to it, aimed
     * away from it, and hitting the plane somewhere off the edge of the table. All three have
     * to be told apart from a hit at the very corner, because "the cursor is not over the
     * table" is a real answer that a drop has to respect - a card released over the floor goes
     * back where it came from rather than sliding to the nearest edge.
     */
    public Optional<Spot> hit(
            double eyeX, double eyeY, double eyeZ,
            double lookX, double lookY, double lookZ) {
        if (lookY == 0 || !Double.isFinite(lookY)) {
            return Optional.empty();
        }
        double distance = (topY - eyeY) / lookY;
        if (!(distance > 0) || !Double.isFinite(distance)) {
            return Optional.empty();
        }
        return at(eyeX + lookX * distance, eyeZ + lookZ * distance);
    }

    /**
     * The surface point under a world position, if that position is over the table.
     *
     * <p>Whether the point is on the table is decided in world coordinates and the answer is
     * clamped after converting, rather than the other way round. Converting first and then
     * checking looks equivalent and is not: the far corner converts to a hair over the span -
     * the division does not come back exactly - so the one point at the very edge of the
     * table read as off it, and a card let go there quietly went nowhere.
     */
    public Optional<Spot> at(double worldX, double worldZ) {
        if (worldX < westX || worldX > westX + span || worldZ < northZ || worldZ > northZ + span) {
            return Optional.empty();
        }
        return Optional.of(new Spot(
                clamped((worldX - westX) / span * TableSurface.SPAN),
                clamped((worldZ - northZ) / span * TableSurface.SPAN)));
    }

    private static double clamped(double surfaceUnits) {
        return Math.max(0, Math.min(TableSurface.SPAN, surfaceUnits));
    }

    /** The world x of a point on the surface. */
    public double worldX(double surfaceX) {
        return westX + surfaceX / TableSurface.SPAN * span;
    }

    /** The world z of a point on the surface. */
    public double worldZ(double surfaceY) {
        return northZ + surfaceY / TableSurface.SPAN * span;
    }

    /** How many blocks a surface distance is, for sizing anything drawn on the table. */
    public double blocks(double surfaceUnits) {
        return surfaceUnits / TableSurface.SPAN * span;
    }
}
