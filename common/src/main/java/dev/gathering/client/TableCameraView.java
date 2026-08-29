package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.TableCamera;
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

    /**
     * Where the eye starts, before anything has been framed.
     *
     * <p>How far up it may go and how far down are not constants here: they are the seated
     * board's own zoom limits, which are stated as how big a card comes out rather than as a
     * distance - see {@link #heightBounded}.
     */
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
     * <p>The camera centers what it is looking at in the <em>window</em>, and the window has a
     * status strip across the top of it and the player's hand across the bottom. Left alone,
     * that puts the near edge of the table - the player's own zones and their own mat -
     * underneath their own cards, and the far edge behind the life totals. So what is framed
     * is centered in the strip between the two, and sized to fit it.
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
        height = heightBounded(STARTING_HEIGHT);
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
        offsetX = (ownMat.centerX() - TableSurface.SPAN / 2.0) / TableSurface.SPAN
                * TableTop.SPAN_BLOCKS;
        offsetZ = (ownMat.centerY() - TableSurface.SPAN / 2.0) / TableSurface.SPAN
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
        // The frame's own ratio where there is one, for the same reason as the spread: what
        // was drawn beats what was configured.
        double aspect = Math.max(0.1, TablePointer.aspect().orElseGet(() ->
                client.getWindow().getWidth()
                        / (double) Math.max(1, client.getWindow().getHeight())));
        double forDepth = downBlocks * BREATHING_ROOM / (visible * perBlock);
        double forWidth = acrossBlocks * BREATHING_ROOM / (aspect * perBlock);
        return heightBounded(Math.max(forDepth, forWidth));
    }

    /**
     * How high the eye may go, said in the only units that mean anything: card pixels.
     *
     * <p>This view used to bound itself with a pair of distances of its own, and the seated
     * board bounds itself by how tall a reference card comes out. Two rules for one decision,
     * so the same key gave two different boards: "show me everything" on the screen stopped
     * at a card twenty-four pixels tall, and on the block carried on going, framing the same
     * table a fifth smaller. A player reads that as the real table being the worse one.
     *
     * <p>So the limits are the seated board's, converted. A reference card is a fixed share
     * of one table, a table is a known number of blocks, and at eye height {@code h} the
     * window shows {@code h * spread} blocks over its own height in pixels - which turns "a
     * card is twenty-four pixels" into a height and back.
     */
    private static double heightBounded(double wanted) {
        double cardBlocks = TableSurface.CARD_HEIGHT_UNITS / (double) TableSurface.SPAN
                * TableTop.SPAN_BLOCKS;
        double window = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        double perBlock = spread();
        double furthest = cardBlocks * window / (TableCamera.smallestCardPixels() * perBlock);
        double closest = cardBlocks * window / (TableCamera.largestCardPixels() * perBlock);
        return Math.max(closest, Math.min(furthest, wanted));
    }

    /**
     * How many blocks of table one block of eye height shows, top to bottom of the window.
     *
     * <p>Read off the frame the game actually drew wherever there is one, and only worked
     * out from the field-of-view setting before the first frame. The setting is not the
     * effective angle: it goes through a private method, a sprint modifier, a potion and a
     * loader hook, so a camera that framed from the setting alone would frame the board
     * wrongly for exactly the players whose view is not the default one - and only while the
     * modifier lasted. That is the same mistake the picker exists not to make, and there is
     * no reason for the framing to make it when the answer is already captured.
     *
     * <p>Package-private rather than private because the scripted run builds a camera ray by
     * hand to check the board's picker against, and the half-angle is the one thing such a
     * ray cannot work out for itself. Reading it here rather than writing the formula out
     * again is what stops the check and the framing from disagreeing about the same camera.
     */
    static double spread() {
        return TablePointer.verticalSpread().orElseGet(TableCameraView::spreadFromTheSetting);
    }

    /** What the setting says, for the first frame, before there is a drawn one to read. */
    private static double spreadFromTheSetting() {
        double fov = Minecraft.getInstance().options.fov().get();
        return 2 * Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2));
    }

    /**
     * What the camera is doing, for the scripted run to write down.
     *
     * <p>Both spreads, because the whole point is whether the one the framing is built on
     * matches the one the frame was drawn with.
     */
    static String report() {
        return "height=" + height
                + " spread=" + String.format("%.4f", spread())
                + " fromTheSetting=" + String.format("%.4f", spreadFromTheSetting())
                + " aspect=" + String.format("%.4f",
                        TablePointer.aspect().orElse(Double.NaN))
                + " coveredTop=" + String.format("%.4f", coveredAtTheTop)
                + " coveredBottom=" + String.format("%.4f", coveredAtTheBottom);
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
            height = heightBounded(height / factor);
        }
    }

    /**
     * The same, keeping whatever is under the cursor under the cursor.
     *
     * <p>This used to just lower the eye towards the middle of the view, on the reasoning
     * that leaning towards a table is what moving your head does. Looking at the pictures
     * said otherwise: six notches in from the opening framing gave a screen of bare felt,
     * because what the player was pointing at slid out of the frame while they zoomed
     * towards a spot that happened to be empty. Leaning towards a table is leaning towards
     * the thing you are looking at, and the seated board has anchored its wheel to the
     * cursor since it had one.
     *
     * <p>Straight down at a plane, a ground point's distance from the middle of the screen is
     * its distance from the eye's ground point divided by the height - so holding it still
     * means moving the eye's ground point in by the same ratio the height changed.
     */
    public static void zoomAt(double factor, double anchorWorldX, double anchorWorldZ) {
        BlockPos corner = table;
        if (corner == null || factor <= 0) {
            return;
        }
        double was = height;
        height = heightBounded(height / factor);
        if (height == was) {
            return;
        }
        double ratio = height / was;
        TableTop top = TableTop.forCorner(corner.getX(), corner.getY(), corner.getZ());
        double baseX = top.worldX(TableSurface.SPAN / 2.0);
        double baseZ = top.worldZ(TableSurface.SPAN / 2.0);
        double wantedX = anchorWorldX - (anchorWorldX - (baseX + offsetX)) * ratio;
        double wantedZ = anchorWorldZ
                - (anchorWorldZ - (baseZ + offsetZ + lift(was))) * ratio;
        double reach = TableTop.SPAN_BLOCKS / 2;
        offsetX = Math.max(-reach, Math.min(reach, wantedX - baseX));
        offsetZ = Math.max(-reach, Math.min(reach, wantedZ - baseZ - lift(height)));
    }

    /**
     * How far the picture is slid along the table to clear the hand and the status strip.
     *
     * <p>Screen-up is north for one player and south for the other, so lifting the picture
     * clear of the hand is a step in opposite world directions for the two of them.
     */
    private static double lift(double eyeHeight) {
        return (coveredAtTheTop - coveredAtTheBottom) / 2.0 * eyeHeight * spread()
                * (facing == FACING_FROM_SOUTH ? -1 : 1);
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

    /**
     * Slides the view by a drag on the window, in pixels.
     *
     * <p>How many blocks a pixel is worth depends entirely on how high the eye is, and this
     * view's zoom now spans an eleven-fold range - a card is twenty-four pixels at one end
     * and two hundred and sixty at the other. It used to convert with a fixed two hundred and
     * twenty pixels per block, on the reasoning that nobody notices a pan being five per cent
     * fast. Nobody does; what they notice is a drag that runs four times too slow zoomed in
     * and two and a half times too fast zoomed out, which is what that constant came to once
     * the zoom limits stopped being a pair of fixed heights.
     *
     * <p>So it is worked out rather than assumed, from the same height and the same drawn
     * spread the framing uses. One number for both axes: pixels are square, and the window is
     * exactly as many blocks wider than it is tall as it is pixels wider than it is tall.
     */
    public static void panByPixels(double pixelsX, double pixelsY) {
        double perBlock = pixelsPerBlock();
        pan(pixelsX / perBlock, pixelsY / perBlock);
    }

    /**
     * How much window one block of table covers, top to bottom, at this height.
     *
     * <p>Package-private because the screen sizes the card in the player's hand against it
     * too. A card lifted off the block is drawn at the size a card is <em>on</em> the block,
     * and that is this number times how much of a block a card is - so if the two ever came
     * from different places, picking a card up would change its size.
     */
    static double pixelsPerBlock() {
        double window = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        return window / Math.max(1e-6, height * spread());
    }

    /** Back to the whole table, centered, and still clear of the hand. */
    public static void showEverything() {
        height = heightThatFrames(TableTop.SPAN_BLOCKS, TableTop.SPAN_BLOCKS);
        offsetX = 0;
        offsetZ = 0;
    }

    /**
     * Whether this entity should be left out while the camera is over a table.
     *
     * <p>Reported as "do not render other players when in real table view". The eye sits a
     * couple of blocks above the felt looking straight down, so everybody standing around the
     * table is between it and the board - four players at a four-table cluster can cover most
     * of what they are all trying to read, and the person it hides most reliably is the one
     * whose own head is directly under the camera.
     *
     * <p>Players only. The board itself is drawn by the table's block entity rather than as an
     * entity, and item frames, armour stands and everything else somebody has arranged around
     * their table are part of the room they built - hiding those would be tidying up after
     * them. A player is the only thing here that is in the way rather than in the scene.
     *
     * <p>The decision lives here rather than in either loader's mixin so the two cannot come
     * to different conclusions, which is the same reason {@link #wanted} does.
     */
    public static boolean hides(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player && table != null;
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
        double lift = lift(height);
        return Optional.of(new Placement(
                top.worldX(TableSurface.SPAN / 2.0) + offsetX,
                top.topY() + height,
                top.worldZ(TableSurface.SPAN / 2.0) + offsetZ + lift,
                facing,
                LOOKING_DOWN));
    }
}
