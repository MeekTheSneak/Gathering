package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gathering.block.DisplayCaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Quaternionf;

/**
 * The card in a display case, standing up in the world.
 * <p>Drawn through the same code a card in the hand goes through, so a card in a case is the card the
 * player would see if they were holding it - art, sleeve, foil sheen and all - rather than a second
 * drawing of a card that would drift from the first.
 * <p>The block's model is the glass and the frame; this is only what is inside it. Nothing is drawn for
 * an empty case, which is what an empty case looks like.
 * <p>Client-only.
 */
public final class DisplayCaseRenderer implements BlockEntityRenderer<DisplayCaseBlockEntity> {

    /** How tall a card stands in the case, as a fraction of a block. It is a counter, not a cabinet. */
    private static final float TALL = 0.34f;

    /** The middle of the block, and the height a card's own middle sits at. */
    private static final float MIDDLE = 0.5f;
    private static final float STANDS_AT = 0.72f;

    /** How far apart the four of them stand, across the front of the block. */
    private static final float APART = 0.23f;

    /** How far back they lean, in degrees: a case you look down into, not a shelf you look along. */
    private static final float LEANS = 18f;

    public DisplayCaseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DisplayCaseBlockEntity display, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        java.util.List<net.minecraft.world.item.ItemStack> showing = display.asStacks();
        if (showing.isEmpty()) {
            return;
        }
        Direction facing = display.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? display.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.SOUTH;
        // Centered as a row however many are in it, so two cards sit in the middle of the case rather
        // than at one end of a row of four gaps.
        float from = -APART * (showing.size() - 1) / 2f;
        for (int at = 0; at < showing.size(); at++) {
            poseStack.pushPose();
            poseStack.translate(MIDDLE, STANDS_AT, MIDDLE);
            // The cards face the way the case does. A card drawn facing north in a case facing south is a
            // case a player has to walk round the back of to read.
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-facing.toYRot())));
            poseStack.translate(from + APART * at, 0f, 0f);
            poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(LEANS)));
            poseStack.scale(TALL, TALL, TALL);
            // The card renderer draws in a one-by-one space with its origin at a corner, and centers
            // itself within it; undo the centering it is about to do.
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            CardFaceRenderer.render(showing.get(at), poseStack, buffers, packedLight);
            poseStack.popPose();
        }
    }

    /** Seen from as far away as any other block: a case across a room is the point of a case. */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
