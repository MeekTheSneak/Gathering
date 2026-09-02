package dev.gathering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The pack item's renderer, in vanilla terms so both loaders can reach it.
 * <p>The same shape as the card renderer next door, for the same reason: {@code
 * BlockEntityWithoutLevelRenderer} is a Minecraft class rather than a loader one,
 * which is why this can live in common: NeoForge hands one of these back from its client
 * item extensions, and Fabric's builtin renderer calls straight into the same drawing code.
 * <p>Client-only.
 */
public final class PackItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static PackItemRenderer instance;

    private PackItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /**
     * Built on first use rather than at registration.
     * <p>Its superclass wants two things off {@code Minecraft.getInstance()}, and registration
     * runs long before either exists.
     */
    public static PackItemRenderer instance() {
        if (instance == null) {
            instance = new PackItemRenderer();
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
        PackFaceRenderer.render(stack, poseStack, buffers, packedLight);
    }
}
