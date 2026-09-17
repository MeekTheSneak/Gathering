package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gathering.core.ui.LabelStandoff;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lines of text floating over a block, turned to face whoever is looking: a tournament table's
 * number, a Scorekeeper's Desk's tournament. The first line is the heading, in gold.
 * <p>Text rather than a sprite, so it needs no artwork, and one way of drawing it so the labels
 * in a hall read as one set.
 */
public final class FloatingLabel {

    private static final int HEADING = 0xFFE0B15A;
    private static final int TEXT = 0xFFFFFFFF;

    private FloatingLabel() {
    }

    /**
     * Draws the lines centered on a point over a block, facing the camera.
     * <p>The point is given in the block's own coordinates and the block's position beside it,
     * because where the writing ends up depends on where it is being read from: walk into the
     * block and the writing is kept at arm's length rather than pressed against the reader's
     * face, where a line of it is four windows wide and only the middle of it is on the
     * screen. See {@link dev.gathering.core.ui.LabelStandoff}.
     */
    public static void draw(PoseStack poseStack, MultiBufferSource buffers, List<Component> lines,
            BlockPos origin, double x, double y, double z) {
        Minecraft client = Minecraft.getInstance();
        LabelStandoff.Spot anchor = new LabelStandoff.Spot(
                origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
        LabelStandoff.Spot drawn = LabelStandoff.keptBack(
                anchor, new LabelStandoff.Spot(eye.x, eye.y, eye.z));
        draw(poseStack, buffers, lines,
                drawn.x() - origin.getX(), drawn.y() - origin.getY(), drawn.z() - origin.getZ(),
                client.getEntityRenderDispatcher().cameraOrientation());
    }

    /** The same, turned a fixed way rather than to the camera. */
    public static void draw(PoseStack poseStack, MultiBufferSource buffers, List<Component> lines, double x, double y, double z,
            Quaternionf turned) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(turned);
        // The one scale, from the one place that knows it: the standoff and the culling box
        // are both worked out in font pixels, and a scale written twice is a scale that drifts.
        float perPixel = (float) LabelStandoff.PER_PIXEL;
        poseStack.scale(perPixel, -perPixel, perPixel);
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            float left = -font.width(line) / 2f;
            // The backing first and the words over it with a polygon offset, the way a sign's
            // text is drawn. In one pass the letters and their backing share a depth, and
            // turned to face the camera half of each line lost to the backing.
            //
            // Both passes see through whatever is in front of them. A label is a flat sheet
            // turned to face the reader, so the nearer they stand the more of the room that
            // sheet cuts through: the desk under it, the table beside it, the wall behind
            // them. Depth-tested, every block it passes through takes a bite out of the
            // writing, which is the reported "portions of the display phase out of existence"
            // - and the bites are worst exactly where somebody is close enough to be reading
            // it. Through the wall is the lesser of the two: this is a label for finding a
            // table across a hall, and a name that can be read from the next room is what it
            // is for.
            font.drawInBatch(line, left, index * LabelStandoff.LINE_PIXELS, 0x00FFFFFF, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
            font.drawInBatch(line, left, index * LabelStandoff.LINE_PIXELS, index == 0 ? HEADING : TEXT, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
    }
}
