package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The card item's renderer, in vanilla terms so both loaders can reach it.
 * <p>{@code BlockEntityWithoutLevelRenderer} is a Minecraft class rather than a loader one,
 * which is why this can live in common: NeoForge hands one of these back from its client
 * item extensions, and Fabric's builtin renderer calls straight into the same drawing code.
 * <p>Client-only.
 */
public final class CardItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static CardItemRenderer instance;

    private CardItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /**
     * Built on first use rather than at registration.
     * <p>Its superclass wants two things off {@code Minecraft.getInstance()}, and registration
     * runs long before either exists.
     */
    public static CardItemRenderer instance() {
        if (instance == null) {
            instance = new CardItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        CardFaceRenderer.render(stack, poseStack, buffers, packedLight);
    }
}
