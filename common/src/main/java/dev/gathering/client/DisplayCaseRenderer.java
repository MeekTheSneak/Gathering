package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gathering.block.DisplayCaseBlockEntity;
import dev.gathering.item.CardItem;
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

    /** How tall the card stands in the block, as a fraction of it. Room for the frame above and below. */
    private static final float TALL = 0.72f;

    /** The middle of the block, and the height the card's own middle sits at. */
    private static final float MIDDLE = 0.5f;
    private static final float STANDS_AT = 0.52f;

    public DisplayCaseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DisplayCaseBlockEntity display, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        display.card().ifPresent(card -> {
            Direction facing = display.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                    ? display.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                    : Direction.SOUTH;
            poseStack.pushPose();
            poseStack.translate(MIDDLE, STANDS_AT, MIDDLE);
            // The card faces the way the case does. A card drawn facing north in a case facing south is a
            // case a player has to walk round the back of to read.
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-facing.toYRot())));
            poseStack.scale(TALL, TALL, TALL);
            // The card renderer draws in a one-by-one space with its origin at a corner, and centers
            // itself within it; undo the centering it is about to do.
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            CardFaceRenderer.render(CardItem.of(card), poseStack, buffers, packedLight);
            poseStack.popPose();
        });
    }

    /** Seen from as far away as any other block: a case across a room is the point of a case. */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
