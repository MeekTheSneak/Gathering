package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
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

    /** Draws the lines centered on a point, given in the block's own coordinates, facing the camera. */
    public static void draw(PoseStack poseStack, MultiBufferSource buffers, List<Component> lines, double x, double y, double z) {
        draw(poseStack, buffers, lines, x, y, z, Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    }

    /** The same, turned a fixed way rather than to the camera. */
    public static void draw(PoseStack poseStack, MultiBufferSource buffers, List<Component> lines, double x, double y, double z,
            Quaternionf turned) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(turned);
        poseStack.scale(0.025f, -0.025f, 0.025f);
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            float left = -font.width(line) / 2f;
            // The backing first and the words over it with a polygon offset, the way a sign's
            // text is drawn. In one pass the letters and their backing share a depth, and
            // turned to face the camera half of each line lost to the backing.
            font.drawInBatch(line, left, index * 10f, 0x00FFFFFF, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x40000000, LightTexture.FULL_BRIGHT);
            font.drawInBatch(line, left, index * 10f, index == 0 ? HEADING : TEXT, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
    }
}
