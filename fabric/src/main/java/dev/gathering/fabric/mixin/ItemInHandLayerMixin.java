package dev.gathering.fabric.mixin;

import dev.gathering.client.TableBodyPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts down whatever a seated player was carrying, for as long as they are seated.
 * <p>Both their hands are busy: one is out over the felt and the other is holding their cards. A
 * sword drawn through a fan of cards is the worst frame this feature can produce and it is the one
 * a player gets by sitting down mid-fight.
 * <p>A mixin because neither loader can drop one layer, for one entity, for one frame.
 * {@code RenderHandEvent} is the first-person hand and is a different thing; there is no
 * third-person equivalent. The twin of the Fabric one, and identical on purpose.
 * <p>This is not the seated player's own view: {@link dev.gathering.client.TableCameraView} already
 * sets {@code hideGui}, which takes the crosshair and the held item together.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot find
    // its place, the game still starts and only this one behavior is lost.
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void gathering$handsFull(
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, LivingEntity entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (entity instanceof net.minecraft.world.entity.player.Player player
                && TableBodyPose.hidesHeldItems(player)) {
            callback.cancel();
        }
    }
}
