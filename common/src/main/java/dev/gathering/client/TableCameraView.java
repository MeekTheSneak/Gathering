package dev.gathering.client;

import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableTop;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Where the camera goes while somebody is playing at a table.
 *
 * <p>A card game wants to be looked at from above. Standing at the edge in first person shows
 * you a board at a raking angle with the far half foreshortened into a line, which is why
 * nobody plays cards standing up at the end of a table - and why the seated view is worth
 * having at all.
 *
 * <p>Minecraft will not simply be told where to put its camera. {@code ViewportEvent} on
 * NeoForge offers the angles and not the position, and the position is set inside
 * {@code Camera.setup} through a protected method after that event has already fired. So each
 * loader carries a small mixin that reaches into the camera at the end of setup, and the
 * decision about <em>what</em> to reach in with lives here, once, where both can ask it.
 *
 * <p>Client-only.
 */
public final class TableCameraView {

    /** Straight down. The board is a plane and this is the only angle that does not lie. */
    public static final float LOOKING_DOWN = 90f;

    /**
     * Which way up the table is drawn, for a player whose own mat is on the north half.
     *
     * <p>Looking straight down, the camera's up vector is whichever way it was facing: at yaw
     * zero {@code Camera.setRotation} leaves it pointing south, so a larger world z is drawn
     * higher up the screen and the north half of the table is at the bottom. Which is right
     * for the player sitting at the north edge, and exactly backwards for the one opposite -
     * they got their own board across the top of the screen with their zones along the far
     * edge, reading upside down, which is what a table looks like from the wrong chair.
     */
    private static final float FACING_FROM_NORTH = 0f;
    private static final float FACING_FROM_SOUTH = 180f;

    /** Where the eye goes and which way it faces. */
    public record Placement(double x, double y, double z, float yaw, float pitch) {
    }

    /** Far enough up to see the whole table, and close enough to read a card. */
    private static final double LOWEST = 0.55;
    private static final double HIGHEST = 4.5;
    private static final double STARTING_HEIGHT = 2.2;

    /** The table being played at, or nothing at all, which is the usual answer. */
    private static BlockPos table;

    /** Which way round to draw it, so the player's own mat is the near one. */
    private static float facing = FACING_FROM_NORTH;

    /** How far above the surface the eye sits. */
    private static double height = STARTING_HEIGHT;

    /** How far the view has been slid off the middle of the table, in blocks. */
    private static double offsetX;
    private static double offsetZ;

    private TableCameraView() {
    }

    /**
     * Called when the in-world view opens, so the camera knows there is a table to look at.
     *
     * @param southHalf whether the viewer's own mat is on the far side of the table from the
     *     block's north-west corner - in which case the whole board is turned around, so that
     *     their own zones are the ones nearest them
     */
    public static void lookAt(BlockPos corner, boolean southHalf) {
        table = corner;
        facing = southHalf ? FACING_FROM_SOUTH : FACING_FROM_NORTH;
        height = STARTING_HEIGHT;
        offsetX = 0;
        offsetZ = 0;
    }

    /** Called when it closes. The camera goes back to being the player's. */
    public static void release() {
        table = null;
    }

    public static boolean isLooking() {
        return table != null;
    }

    /**
     * Moves the eye up or down, which is what zoom is when you are looking straight down.
     *
     * <p>Bounded at both ends for the same reasons the seated view bounds its own: too close
     * and the table is a card and a half, too far and it is a mosaic. A factor above one
     * leans in, matching the wheel on the seated screen.
     */
    public static void zoom(double factor) {
        if (factor > 0) {
            height = Math.max(LOWEST, Math.min(HIGHEST, height / factor));
        }
    }

    /**
     * Slides the view across the table, in blocks.
     *
     * <p>Clamped to the table itself rather than to nothing: panning until the board is off
     * screen is a way to lose the game you are playing.
     */
    public static void pan(double acrossBlocks, double downBlocks) {
        // Turned with the view. Dragging right has to move the table right on the screen, and
        // for the player sitting opposite, screen-right is world-west.
        double sense = facing == FACING_FROM_SOUTH ? -1 : 1;
        double reach = TableTop.SPAN_BLOCKS / 2;
        offsetX = Math.max(-reach, Math.min(reach, offsetX + acrossBlocks * sense));
        offsetZ = Math.max(-reach, Math.min(reach, offsetZ + downBlocks * sense));
    }

    /** Back to the whole table, centred. */
    public static void showEverything() {
        height = STARTING_HEIGHT;
        offsetX = 0;
        offsetZ = 0;
    }

    /**
     * Where the camera should be this frame, or empty to leave it alone.
     *
     * <p>Empty is the answer almost every frame of almost every session, and it has to be
     * cheap and unconditional: this is called from inside the camera's own setup, for every
     * frame, for every player who has never seen a table.
     */
    public static Optional<Placement> wanted() {
        BlockPos corner = table;
        if (corner == null) {
            return Optional.empty();
        }
        TableTop top = TableTop.forCorner(corner.getX(), corner.getY(), corner.getZ());
        return Optional.of(new Placement(
                top.worldX(TableSurface.SPAN / 2.0) + offsetX,
                top.topY() + height,
                top.worldZ(TableSurface.SPAN / 2.0) + offsetZ,
                facing,
                LOOKING_DOWN));
    }
}
