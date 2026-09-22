package dev.gathering.fabric.mixin;

import dev.gathering.block.BreakRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What is in use survives the blast, on this loader too.
 * <p>NeoForge has an event that hands a mod the list of blocks an explosion is about to take, and
 * Fabric has nothing of the kind - so this is the twin of {@code GatheringTableRules.onExplode},
 * and like every other pair in this mod both sides ask {@link BreakRules#survivesExplosions} so
 * they cannot come to different answers.
 * <p>Vanilla asks a block how much explosion it can take and never says <em>where</em> that block
 * is, so "this table, which has a game on it" is not a question the block itself can answer. The
 * explosion's own list is, and taking entries out of it after it has been worked out and before
 * anything is removed is the whole of this.
 * <p>Injected at the head of the removal half rather than at the end of the calculation, because
 * the calculation is also what decides how far the blast carries: a protected table should stop
 * being destroyed, not start shielding everything behind it from a blast that would have gone
 * through an ordinary one.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow
    public abstract java.util.List<BlockPos> getToBlow();

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Level level;

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot
    // find its place, the game still starts and only this one behavior is lost.
    @Inject(method = "finalizeExplosion", at = @At("HEAD"), require = 0)
    private void gathering$leaveWhatIsInUse(boolean spawnParticles, CallbackInfo callback) {
        getToBlow().removeIf(pos -> BreakRules.survivesExplosions(level, pos));
    }
}
