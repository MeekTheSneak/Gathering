package dev.gathering.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.gathering.core.ui.TableTop;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What the mouse is pointing at, once the table is a thing in the world.
 * <p>On the seated screen the cursor was already in the same space as the felt. Playing on the
 * block means it is not: the pointer is a position on a window and the table is a plane in a
 * world, and getting from one to the other means running the game's own camera backwards.
 * <p><b>The matrices are the ones the game actually drew with, captured while it drew.</b>
 * Rebuilding them from the field of view instead is the obvious approach and it is wrong in a
 * way that would be very hard to find: the effective field of view is not the setting, it is
 * the setting through a private method, a sprint modifier, a potion, and a loader hook - so a
 * player under Speed would find every card sitting a little to one side of where they were
 * pointing, and only that player, and only sometimes. Reading the real projection costs a
 * field and cannot drift.
 * <p>Client-only.
 */
public final class TablePointer {

    /** Last frame's projection, including whatever the game had folded into it. */
    private static final Matrix4f projection = new Matrix4f();

    private static Vec3 eye = Vec3.ZERO;
    private static final Matrix4f view = new Matrix4f();
    private static boolean ready;

    private TablePointer() {
    }

    /**
     * Remembers how this frame was drawn.
     * <p>Called from the thing that draws the board, because that runs inside the world render
     * with the projection still set - which is the only moment any of this is true.
     */
    public static void capture(Camera camera) {
        projection.set(RenderSystem.getProjectionMatrix());
        // The view matrix the game builds: the camera's rotation, conjugated, with the world
        // drawn relative to the camera rather than to the origin. Built here from the camera's
        // own rotation rather than read back, because that is the one the level renderer was
        // handed and it is public.
        view.rotation(camera.rotation().conjugate(new Quaternionf()));
        eye = camera.getPosition();
        ready = true;
    }

    /**
     * How many blocks of table one block of eye height shows, top to bottom of the window.
     * <p>Read off the projection the game actually drew with, for the same reason the picker
     * is: the effective field of view is the setting through a private method, a sprint
     * modifier, a potion and a loader hook, and a camera that framed the table from the
     * setting alone would frame it wrongly for exactly the players whose view is not the
     * default one. Empty until a frame has been drawn, which is the first frame of the view.
     */
    public static java.util.OptionalDouble verticalSpread() {
        // A perspective projection's m11 is 1 / tan(half the vertical angle), so twice its
        // reciprocal is the height of what is visible one unit in front of the eye.
        return ready && projection.m11() > 0
                ? java.util.OptionalDouble.of(2.0 / projection.m11())
                : java.util.OptionalDouble.empty();
    }

    /**
     * How much wider than tall the drawn frame is, by the same reading.
     * <p>The window's own ratio in every ordinary case, and read from the projection anyway
     * so that the two halves of a framing decision come from one place.
     */
    public static java.util.OptionalDouble aspect() {
        return ready && projection.m00() > 0
                ? java.util.OptionalDouble.of(projection.m11() / (double) projection.m00())
                : java.util.OptionalDouble.empty();
    }

    /**
     * Where a place on the felt lands on the window, which is the picker run forwards.
     * <p>The one way to ask how big the in-world board is actually drawn. Everything else
     * about it is measured in surface units, which say where a mat is on the felt and nothing
     * at all about whether a player can read it - and "the board on the block is smaller than
     * the same board on the screen" is a complaint about pixels.
     * <p>In GUI coordinates, the same ones a mouse arrives in, so a caller can compare what
     * it gets back against a rectangle on the seated board without converting anything.
     * Empty when the point is behind the eye or no frame has been drawn yet.
     */
    public static Optional<double[]> onScreen(TableTop top, double surfaceX, double surfaceY) {
        if (!ready) {
            return Optional.empty();
        }
        Minecraft client = Minecraft.getInstance();
        int width = Math.max(1, client.getWindow().getGuiScaledWidth());
        int height = Math.max(1, client.getWindow().getGuiScaledHeight());
        int pixelsWide = Math.max(1, client.getWindow().getWidth());
        int pixelsHigh = Math.max(1, client.getWindow().getHeight());

        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        // Camera-relative, because that is the space the world was drawn in.
        Vector3f point = new Vector3f(
                (float) (top.worldX(surfaceX) - eye.x),
                (float) (top.topY() - eye.y),
                (float) (top.worldZ(surfaceY) - eye.z));
        Vector3f window = viewProjection.project(
                point.x(), point.y(), point.z(),
                new int[] {0, 0, pixelsWide, pixelsHigh}, new Vector3f());
        if (window.z() < 0 || window.z() > 1) {
            return Optional.empty();
        }
        // Back to GUI units, and back to counting down the screen.
        return Optional.of(new double[] {
                window.x() / pixelsWide * width,
                (pixelsHigh - window.y()) / pixelsHigh * height});
    }

    /** Forgotten when the view closes, so a stale frame can never answer for a live one. */
    public static void forget() {
        ready = false;
    }

    /**
     * Where a point on the screen meets a table's surface, if it meets it.
     * <p>Mouse coordinates are the GUI's, which are the window's divided by the GUI scale, so
     * they are turned into a fraction of the screen first and the framebuffer's own size used
     * from there. Mixing the two is how a picker ends up right at one GUI scale and off at
     * every other.
     */
    public static Optional<TableTop.Spot> at(TableTop top, double mouseX, double mouseY) {
        if (!ready) {
            return Optional.empty();
        }
        Minecraft client = Minecraft.getInstance();
        int width = Math.max(1, client.getWindow().getGuiScaledWidth());
        int height = Math.max(1, client.getWindow().getGuiScaledHeight());
        int pixelsWide = Math.max(1, client.getWindow().getWidth());
        int pixelsHigh = Math.max(1, client.getWindow().getHeight());

        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        Vector3f from = new Vector3f();
        Vector3f along = new Vector3f();
        // OpenGL counts up the screen and the GUI counts down it, so the y is turned over.
        viewProjection.unprojectRay(
                (float) (mouseX / width * pixelsWide),
                (float) (pixelsHigh - mouseY / height * pixelsHigh),
                new int[] {0, 0, pixelsWide, pixelsHigh},
                from, along);

        // Camera-relative, because that is how the world was drawn; the eye puts it back.
        return top.hit(
                eye.x + from.x(), eye.y + from.y(), eye.z + from.z(),
                along.x(), along.y(), along.z());
    }
}
