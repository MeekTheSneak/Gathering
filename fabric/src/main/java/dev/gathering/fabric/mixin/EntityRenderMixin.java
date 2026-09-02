package dev.gathering.fabric.mixin;

import dev.gathering.client.TableCameraView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the players out while the camera is over a table.
 * <p>The second and last thing in this mod that reaches into the game's own code, and here
 * for the same reason the camera hook is: the eye sits a couple of blocks above the felt
 * looking straight down, so everybody standing around the table is between it and the board -
 * and neither loader offers a way to say "not this entity, this frame" without one of these.
 * <p>Deliberately the smallest thing that could work: it asks one question at the top of one
 * method and does nothing at all if the answer is no. What it should do lives in
 * {@link TableCameraView#hides}, so this and the other loader's copy cannot disagree.
 */
@Mixin(LevelRenderer.class)
public abstract class EntityRenderMixin {

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void gathering$outOfTheWay(
            Entity entity, double camX, double camY, double camZ, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo callback) {
        if (TableCameraView.hides(entity)) {
            callback.cancel();
        }
    }
}
