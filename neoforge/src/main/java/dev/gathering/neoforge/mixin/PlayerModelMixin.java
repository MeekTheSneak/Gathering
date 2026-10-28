package dev.gathering.neoforge.mixin;

import dev.gathering.client.TableBodyPose;
import dev.gathering.client.TablePoseParts;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points a seated player's arm and head at whatever their cursor is over.
 * <p>Here because there is no door: {@code RenderPlayerEvent.Pre} fires <em>before</em> the model
 * is posed, so anything it sets is overwritten a moment later by {@code setupAnim} itself, and
 * neither loader has a hook that runs after the pose and before the draw. Fabric has no such event
 * either, which is why this file has a twin.
 * <p>Deliberately the smallest thing that could work: it asks one question at the end of one
 * method and does nothing at all if the answer is no. What the body should be doing lives in
 * {@link TableBodyPose} and how it reaches the model lives in {@link TablePoseParts}, so this and
 * the other loader's copy cannot come to different conclusions.
 * <p><b>Nothing may be remembered on the model.</b> One {@code PlayerModel} draws every player in
 * the frame; the entity handed to {@code setupAnim} is the only thing that says whose body this is.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    static {
        // A mixin that fails to apply throws at load, which is loud. A mixin that is never *listed*
        // is silent: the game boots, the mod runs, and no arm ever moves. This line is what the boot
        // check looks for, so "the hook is installed" is something the build can be told rather than
        // something somebody has to go and look at. tools/mixincheck.py covers the other half.
        org.slf4j.LoggerFactory.getLogger("Gathering").info("Gathering table pose hook installed");
    }

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot find
    // its place, the game still starts and only this one behavior is lost - never a crash at launch
    // for somebody's pack.
    @Inject(method = "setupAnim", at = @At("TAIL"), require = 0)
    private void gathering$atTheTable(
            LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        TablePoseParts.pose((PlayerModel<?>) (Object) this, entity);
    }
}
