package dev.gathering.core.ui;

import dev.gathering.core.table.TableCell;
import java.util.Optional;

/**
 * The table's playing surface as a plane in the world, and how to hit it.
 * <p>The seated screen turns a card's position into a rectangle because the felt is the
 * screen. Playing on the block itself needs the other conversion: the player is looking at a
 * flat surface somewhere in the world through a cursor, and something has to say which point
 * of the shared surface that cursor is over. That is a ray against a horizontal plane, and it
 * is the whole of the arithmetic - which is why it lives here, in plain doubles, rather than
 * inside a renderer where it could only be checked by looking at it.
 * <p>Surface coordinates are the same ones {@link TableSurface} works in, so a hit comes back
 * ready to hand to {@code seatAt} or {@code positionOn} with nothing in between.
 * <p>Everything below except {@link #at} and {@link #inTheWorld} is measured in the surface's own
 * unturned frame - west to east along its x, north to south along its y, from {@code westX} and
 * {@code northZ} - which for an unturned table is simply the world. The camera frames the table in
 * that frame and turns the answer at the end, the same way the drawing does.
 *
 * @param westX  the world x of the surface's (0, 0) corner
 * @param topY   the world y the surface sits at
 * @param northZ the world z of the surface's (0, 0) corner
 * @param span   how many blocks across one table is
 * @param tablesAcross how many tables the cluster is wide, west to east
 * @param tablesDown   how many tables the cluster is deep, north to south
 * @param turned       whether the surface lies a quarter turn clockwise in the world, as a line of
 *     tables running north to south does, or a lone table seating east and west: its x then runs
 *     south along the world and its y runs west, from the north-east corner of the tables
 */
public record TableTop(
        double westX, double topY, double northZ, double span,
        int tablesAcross, int tablesDown, boolean turned) {

    public TableTop(double westX, double topY, double northZ, double span, int tablesAcross, int tablesDown) {
        this(westX, topY, northZ, span, tablesAcross, tablesDown, false);
    }

    /**
     * How far in from the block's edge the playing surface starts.
     * <p>None: the surface is the table's whole top. The inset that keeps a playmat off the
     * lip belongs to the mat, where it is measured in pixels and comes out the same on every
     * table - see {@code TableSurface}. Taking a slice off here as well made the playing area
     * an awkward one-and-three-quarter blocks that nothing else in the game is measured in.
     */
    public static final double MARGIN = 0.0;

    /** Just above the felt, so what is on the table is on it rather than in it. */
    public static final double SURFACE_HEIGHT = 15.02 / 16.0;

    /** How many blocks of playing surface a table has, once the margin is taken off. */
    public static final double SPAN_BLOCKS = TableCell.BLOCKS_PER_TABLE - MARGIN * 2;

    /**
     * The surface of the table whose owning corner is this block.
     * <p>Everything that draws on the table and everything that works out what a player is
     * pointing at builds one of these, so the picture and the pointing cannot end up measured
     * from different corners.
     */
    public static TableTop forCorner(double cornerX, double cornerY, double cornerZ) {
        return forCluster(cornerX, cornerY, cornerZ, 1, 1);
    }

    /**
     * The surface of a whole cluster, whose owning corner is this block.
     * <p>A cluster is several tables and one game, and the shared surface grows by a table's
     * worth for each of them - so the mats at the far end sit past the first table's edge.
     * Hit testing did not: it was written for one table, checked a point against one table's
     * blocks, and clamped anything beyond into it. An audit reproduced the result, which is
     * that on a four-seat pod the second table's mat is drawn where nothing can be clicked.
     *
     * @param tablesAcross how many tables wide the cluster is, at least one
     * @param tablesDown   how many tables deep the cluster is, at least one
     */
    public static TableTop forCluster(
            double cornerX, double cornerY, double cornerZ, int tablesAcross, int tablesDown) {
        return forCluster(cornerX, cornerY, cornerZ, tablesAcross, tablesDown, false);
    }

    /** The same, for a cluster that may lie a quarter turn round in the world. */
    public static TableTop forCluster(
            double cornerX, double cornerY, double cornerZ, int tablesAcross, int tablesDown, boolean turned) {
        return new TableTop(cornerX + MARGIN, cornerY + SURFACE_HEIGHT, cornerZ + MARGIN,
                SPAN_BLOCKS, tablesAcross, tablesDown, turned);
    }

    public TableTop {
        if (span <= 0) {
            throw new IllegalArgumentException("a table with no surface: span " + span);
        }
        tablesAcross = Math.max(1, tablesAcross);
        tablesDown = Math.max(1, tablesDown);
    }

    /** How many blocks the whole cluster is, west to east. */
    public double widthInBlocks() {
        return span * tablesAcross;
    }

    /** How many blocks the whole cluster is, north to south. */
    public double depthInBlocks() {
        return span * tablesDown;
    }

    /** How many surface units wide the shared surface is - a table's worth per table. */
    public double surfaceWidth() {
        return (double) TableSurface.SPAN * tablesAcross;
    }

    /** How many surface units deep the shared surface is. */
    public double surfaceDepth() {
        return (double) TableSurface.SPAN * tablesDown;
    }

    /**
     * The same surface lifted this far, in surface units.
     * <p>For aiming at the top of something standing on the table - a pile is as tall as the cards
     * in it - rather than at the felt under it. Seen from a chair a pile's top is well to the near
     * side of the felt beneath it, so a ray cast at the felt picked the card behind the pile.
     */
    public TableTop raisedBy(double surfaceUnits) {
        return new TableTop(westX, topY + blocks(surfaceUnits), northZ, span, tablesAcross, tablesDown, turned);
    }

    /** A point on the shared surface, in {@link TableSurface} units. */
    public record Spot(double x, double y) {
    }

    /**
     * Where a ray meets the surface, if it meets it at all.
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
     * <p>Whether the point is on the table is decided in world coordinates and the answer is
     * clamped after converting, rather than the other way round. Converting first and then
     * checking looks equivalent and is not: the far corner converts to a hair over the span -
     * the division does not come back exactly - so the one point at the very edge of the
     * table read as off it, and a card let go there quietly went nowhere.
     */
    public Optional<Spot> at(double worldX, double worldZ) {
        if (turned) {
            // Back into the surface's own frame first: the inverse of inTheWorld.
            double unturnedX = westX + (worldZ - northZ);
            double unturnedZ = northZ + (westX + depthInBlocks() - worldX);
            worldX = unturnedX;
            worldZ = unturnedZ;
        }
        if (worldX < westX || worldX > westX + widthInBlocks()
                || worldZ < northZ || worldZ > northZ + depthInBlocks()) {
            return Optional.empty();
        }
        return Optional.of(new Spot(
                clamped((worldX - westX) / span * TableSurface.SPAN, surfaceWidth()),
                clamped((worldZ - northZ) / span * TableSurface.SPAN, surfaceDepth())));
    }

    private static double clamped(double surfaceUnits, double most) {
        return Math.max(0, Math.min(most, surfaceUnits));
    }

    /**
     * Where a point in the surface's own unturned frame is on the table, as {x, z}: itself for an
     * unturned table, and turned a quarter clockwise about the tables' footprint for a turned one.
     */
    public double[] inTheWorld(double unturnedX, double unturnedZ) {
        if (!turned) {
            return new double[] {unturnedX, unturnedZ};
        }
        return new double[] {westX + depthInBlocks() - (unturnedZ - northZ), northZ + (unturnedX - westX)};
    }

    /**
     * How far a view in the surface's own frame is turned in the world, in degrees of yaw: a quarter
     * for a turned table, whose surface y runs west.
     */
    public float yawTurned() {
        return turned ? 90f : 0f;
    }

    /** The x of a point on the surface, in the surface's own unturned frame. */
    public double worldX(double surfaceX) {
        return westX + surfaceX / TableSurface.SPAN * span;
    }

    /** The z of a point on the surface, in the surface's own unturned frame. */
    public double worldZ(double surfaceY) {
        return northZ + surfaceY / TableSurface.SPAN * span;
    }

    /** How many blocks a surface distance is, for sizing anything drawn on the table. */
    public double blocks(double surfaceUnits) {
        return surfaceUnits / TableSurface.SPAN * span;
    }
}
