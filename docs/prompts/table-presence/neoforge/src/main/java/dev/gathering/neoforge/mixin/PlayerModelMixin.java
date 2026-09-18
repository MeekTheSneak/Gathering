package dev.gathering.neoforge.mixin;

import dev.gathering.client.TableBodyPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points a seated player's arm and head at whatever their cursor is over.
 * <p>The third thing in this mod that reaches into the game's own code, and here because there is
 * no door: {@code RenderPlayerEvent.Pre} fires <em>before</em> the model is posed, so anything it
 * sets is overwritten a moment later by {@code setupAnim} itself, and neither loader has a hook
 * that runs after the pose and before the draw. Fabric has no such event either, which is why
 * this file has a twin.
 * <p>Deliberately the smallest thing that could work: it asks one question at the end of one
 * method and does nothing at all if the answer is no. What the body should be doing lives in
 * {@link TableBodyPose}, so this and the other loader's copy cannot come to different conclusions.
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
        throw new UnsupportedOperationException("""
                Not written yet. In order:
                  1. entity instanceof Player, and TableBodyPose.poses(player) - else return, having
                     touched nothing. This runs for every drawn player in every frame.
                  2. TableBodyPose.aimOf(player, partialTick). The partial tick is not a parameter
                     here; ageInTicks carries its fraction, or read it from Minecraft's own
                     frame timer - settle which in a running game rather than by reasoning.
                  3. Set the pointing arm's xRot and zRot from the Aim, in radians, and the head's
                     yRot and xRot. The sign conventions for the left and right arms are mirrored
                     and the pitch sign is the opposite of the one you will first write: expect to
                     flip it once, with the game open.
                  4. **Copy the parts you moved onto their sleeves.** PlayerModel#setupAnim does
                     leftSleeve.copyFrom(leftArm) and friends as the last thing it does, and this
                     injection runs after that - so an arm moved here leaves its jacket sleeve
                     behind, floating where the arm used to be. Re-copy every part touched,
                     including the hat if the head moved.
                  5. The player is riding a ChairSeat, so HumanoidModel has already posed them
                     sitting - arms forward, legs out. Overwrite what is needed rather than
                     assuming the arm started at rest.""");
    }
}
