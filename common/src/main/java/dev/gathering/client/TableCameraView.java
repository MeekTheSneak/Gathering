package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableSurface;
import dev.gathering.core.ui.TableTop;
import java.util.Optional;
import net.minecraft.client.Minecraft;
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
    private static final double LOWEST = 0.4;
    private static final double HIGHEST = 4.5;
    private static final double STARTING_HEIGHT = 2.2;

    /**
     * A little air around whatever is being framed, so nothing sits against the edge.
     *
     * <p>Nearly none. The seated view frames the same rectangle with none at all - it already
     * knows the status row and the hand are not table - and the two views are supposed to
     * differ only in whether a point is a pixel or a place on the felt. A tenth of air here
     * and none there made the board on the block a tenth smaller than the same board on the
     * screen, which is a difference a player reads as the real table being the worse one.
     */
    private static final double BREATHING_ROOM = 1.03;

    /**
     * How much of the window the screen's own furniture covers, top and bottom.
     *
     * <p>The camera centres what it is looking at in the <em>window</em>, and the window has a
     * status strip across the top of it and the player's hand across the bottom. Left alone,
     * that puts the near edge of the table - the player's own zones and their own mat -
     * underneath their own cards, and the far edge behind the life totals. So what is framed
     * is centred in the strip between the two, and sized to fit it.
     */
    private static double coveredAtTheTop;

    private static double coveredAtTheBottom;

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
    private static void lookAt(BlockPos corner, boolean southHalf) {
        hideTheHud();
        table = corner;
        facing = southHalf ? FACING_FROM_SOUTH : FACING_FROM_NORTH;
        height = STARTING_HEIGHT;
        offsetX = 0;
        offsetZ = 0;
    }

    /**
     * Opens on the player's own mat rather than on the whole table.
     *
     * <p>The same choice the seated screen makes, for the same reason: a table framed whole is
     * a table whose cards are too small to read, and the half of it that matters on the turn
     * you are taking is your own. The whole table is one key away.
     *
     * @param ownMat the viewer's mat in surface units, or an empty rectangle to frame the lot
     */
    public static void focusOn(BlockPos corner, boolean southHalf, Rect ownMat,
            double coveredTop, double coveredBottom) {
        lookAt(corner, southHalf);
        covering(coveredTop, coveredBottom);
        if (ownMat.isEmpty()) {
            return;
        }
        // Surface units run north to south whichever way the viewer is facing, and so does
        // world z, so the same offset finds the same mat for both players; the camera's own
        // facing is what turns the picture round afterwards.
        offsetX = (ownMat.centreX() - TableSurface.SPAN / 2.0) / TableSurface.SPAN
                * TableTop.SPAN_BLOCKS;
        offsetZ = (ownMat.centreY() - TableSurface.SPAN / 2.0) / TableSurface.SPAN
                * TableTop.SPAN_BLOCKS;
        height = heightThatFrames(
                ownMat.width() / (double) TableSurface.SPAN * TableTop.SPAN_BLOCKS,
                ownMat.height() / (double) TableSurface.SPAN * TableTop.SPAN_BLOCKS);
    }

    /**
     * How high the eye has to be for something that size to fit in the strip of window that
     * is neither the status row nor the hand.
     *
     * <p>Both ways round: a mat is wider than it is deep and a narrow window runs out of width
     * long before it runs out of depth, so whichever side needs more air decides.
     */
    private static double heightThatFrames(double acrossBlocks, double downBlocks) {
        Minecraft client = Minecraft.getInstance();
        double perBlock = spread();
        double visible = visibleShare();
        double aspect = Math.max(0.1, client.getWindow().getWidth()
                / (double) Math.max(1, client.getWindow().getHeight()));
        double forDepth = downBlocks * BREATHING_ROOM / (visible * perBlock);
        double forWidth = acrossBlocks * BREATHING_ROOM / (aspect * perBlock);
        return Math.max(LOWEST, Math.min(HIGHEST, Math.max(forDepth, forWidth)));
    }

    /**
     * How many blocks of table one block of eye height shows, top to bottom of the window.
     *
     * <p>The field of view the player chose, not a constant: somebody playing at thirty
     * degrees and somebody playing at a hundred are looking at very different amounts of
     * table from the same height, and framing a mat for one of them frames nothing for the
     * other.
     *
     * <p>Package-private rather than private because the scripted run builds a camera ray by
     * hand to check the board's picker against, and the half-angle is the one thing such a
     * ray cannot work out for itself. Reading it here rather than writing the formula out
     * again is what stops the check and the framing from disagreeing about the same camera.
     */
    static double spread() {
        double fov = Minecraft.getInstance().options.fov().get();
        return 2 * Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2));
    }

    /** Tells the camera how much of the window, top and bottom, it cannot use. */
    private static void covering(double top, double bottom) {
        coveredAtTheTop = Math.max(0, Math.min(0.4, top));
        coveredAtTheBottom = Math.max(0, Math.min(0.6, bottom));
    }

    /** The share of the window's height that is actually table. */
    private static double visibleShare() {
        return Math.max(0.1, 1 - coveredAtTheTop - coveredAtTheBottom);
    }

    /**
     * Points the camera back at a table it was already looking at, without re-framing it.
     *
     * <p>Opening a graveyard takes the table screen away, and the camera goes back to the
     * player with it. Coming back is not arriving: the player had zoomed in on a corner and
     * expects to find that corner, not the whole table from the starting height again.
     */
    public static void resume(BlockPos corner, boolean southHalf,
            double coveredTop, double coveredBottom) {
        hideTheHud();
        table = corner;
        facing = southHalf ? FACING_FROM_SOUTH : FACING_FROM_NORTH;
        covering(coveredTop, coveredBottom);
    }

    /** Called when it closes. The camera goes back to being the player's, and so does the HUD. */
    public static void release() {
        table = null;
        showTheHud();
    }

    /**
     * What the HUD was before the table took it over, or null while the player has it back.
     *
     * <p>Minecraft draws the crosshair and the held item whatever screen is open, which for
     * every other screen is invisible behind it. This one has the world showing through, so
     * the player got a crosshair in the middle of the board and their own arm holding a deck
     * across the corner of it. Hiding the HUD turns both off together, and it is the option
     * the player themselves can toggle, so nothing about it is a surprise.
     */
    private static Boolean hudWas;

    private static void hideTheHud() {
        if (hudWas == null) {
            hudWas = Minecraft.getInstance().options.hideGui;
            Minecraft.getInstance().options.hideGui = true;
        }
    }

    private static void showTheHud() {
        if (hudWas != null) {
            Minecraft.getInstance().options.hideGui = hudWas;
            hudWas = null;
        }
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

    /** Back to the whole table, centred, and still clear of the hand. */
    public static void showEverything() {
        height = heightThatFrames(TableTop.SPAN_BLOCKS, TableTop.SPAN_BLOCKS);
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
        // Screen-up is north for one player and south for the other, so lifting the picture
        // clear of the hand is a step in opposite world directions for the two of them.
        double lift = (coveredAtTheTop - coveredAtTheBottom) / 2.0 * height * spread()
                * (facing == FACING_FROM_SOUTH ? -1 : 1);
        return Optional.of(new Placement(
                top.worldX(TableSurface.SPAN / 2.0) + offsetX,
                top.topY() + height,
                top.worldZ(TableSurface.SPAN / 2.0) + offsetZ + lift,
                facing,
                LOOKING_DOWN));
    }
}
