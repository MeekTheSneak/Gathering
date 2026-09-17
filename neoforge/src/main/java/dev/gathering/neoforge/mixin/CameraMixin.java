package dev.gathering.neoforge.mixin;

import dev.gathering.client.TableCameraView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the camera over the table while somebody is playing at one.
 * <p>The only thing in this mod that reaches into the game's own code, and it is here because
 * there is no other door. NeoForge's {@code ViewportEvent.ComputeCameraAngles} hands over the
 * yaw, pitch and roll and nothing else, and it fires <em>before</em> {@code Camera.setup}
 * moves the camera onto the player - so an event handler can turn the camera to face the table
 * and cannot move it there.
 * <p>Deliberately the smallest thing that could work: it runs at the very end of setup, after
 * everything vanilla and every other listener has had its say, asks one question, and does
 * nothing at all if the answer is no. What it should do lives in {@link TableCameraView}, so
 * this and the Fabric one cannot come to different conclusions.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    static {
        // A mixin that fails to apply throws at load, which is loud. A mixin that is never
        // *listed* is silent: the game boots, the mod runs, and the camera simply never moves.
        // This line is what the boot check looks for, so "the hook is installed" is something
        // the build can be told rather than something somebody has to go and look at.
        org.slf4j.LoggerFactory.getLogger("Gathering").info("Gathering camera hook installed");
    }

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot find its place,
    // the game still starts and only this one behavior is lost - never a crash at launch for somebody's pack.
    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void gathering$overTheTable(
            net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity,
            boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo callback) {
        TableCameraView.wanted().ifPresent(where -> {
            setRotation(where.yaw(), where.pitch());
            setPosition(where.x(), where.y(), where.z());
        });
        // And the other thing that decides where somebody is looking: a card held up to be
        // read holds the view still. Asked here rather than from a tick because this is the
        // one moment in a frame after the mouse has been applied and before the world is
        // drawn with it - anywhere else and the view moves for a frame before it is put back,
        // which is a shudder rather than a lock. It puts the player's own rotation back too;
        // this only says what the camera draws.
        dev.gathering.client.ViewKeeper.heldStill()
                .ifPresent(still -> setRotation(still.yaw(), still.pitch()));
    }
}
