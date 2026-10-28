package dev.gathering.fabric.mixin;

import dev.gathering.client.TableScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Leaves the world undrawn while the flat board covers the whole window.
 * <p>The board's felt is laid edge to edge over the window before anything else on it, and the world was drawn
 * under it every frame anyway - every chunk in view, every entity, and every table's miniature, which nobody
 * could see. Measured at a four-seat game, that was two thirds of the frame. Neither loader has an event that
 * can stop the level being drawn, so this asks one question as the frame begins and does nothing at all if the
 * answer is no; the answer, and what is done in place of the drawing, are
 * {@link TableScreen#leavesTheWorldUndrawn}'s, so this and the other loader's copy cannot disagree.
 * <p>By the game's own switch rather than by cutting the level's drawing short: the frame is drawn the way the
 * game draws one with no level in it, which is the way it draws every frame on the title screen, so nothing
 * that hooks the level's drawing is left halfway through it. That way also leaves out the interface under the
 * screen, which the felt covered too, and the autosave sign, which the board draws for itself.
 */
@Mixin(GameRenderer.class)
public abstract class WorldBehindTheBoardMixin {

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot find its place,
    // the game still starts and the world is drawn behind the board as it always was.
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean gathering$notBehindTheBoard(boolean renderLevel) {
        return renderLevel && !TableScreen.leavesTheWorldUndrawn();
    }
}
