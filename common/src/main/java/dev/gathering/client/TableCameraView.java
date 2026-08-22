package dev.gathering.client;

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
     * Where the eye goes and which way it faces.
     *
     * @param height how far above the table's surface the eye sits, in blocks
     */
    public record Placement(double x, double y, double z, float yaw, float pitch) {
    }

    /** The table being played at, or nothing at all, which is the usual answer. */
    private static BlockPos table;

    /** How far above the surface the eye sits. Set by the view's own zoom. */
    private static double height = 2.6;

    private TableCameraView() {
    }

    /** Called when the seated view opens, so the camera knows there is a table to look at. */
    public static void lookAt(BlockPos corner, double eyeHeight) {
        table = corner;
        height = eyeHeight;
    }

    /** Called when it closes. The camera goes back to being the player's. */
    public static void release() {
        table = null;
    }

    public static boolean isLooking() {
        return table != null;
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
                top.worldX(dev.gathering.core.ui.TableSurface.SPAN / 2.0),
                top.topY() + height,
                top.worldZ(dev.gathering.core.ui.TableSurface.SPAN / 2.0),
                0f,
                LOOKING_DOWN));
    }
}
