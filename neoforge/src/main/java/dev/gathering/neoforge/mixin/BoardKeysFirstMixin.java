package dev.gathering.neoforge.mixin;

import dev.gathering.client.TableScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a key press to the table's board before anything else hears it, when it is a table verb's key there.
 * <p>At the board a key bound to a table verb is that verb, whatever else is bound to it - the owner's rule
 * (2026-10-27). The board answers its own keys in that order, but two kinds of key are read before any screen is
 * told of a press at all: the game's own screenshot and full-screen keys, and other mods' keys for screens, which
 * the loader asks before the screen. Neither loader has an event any earlier, so this asks one question as the game
 * begins to handle a key; the answer, and what the board does with the press, are
 * {@link TableScreen#takesAVerbsKeyFirst}'s, so this and the other loader's copy cannot disagree. When it says no -
 * which is every press with no board open, and every key that is not a verb's there - the game goes on exactly as
 * it would have.
 */
@Mixin(KeyboardHandler.class)
public abstract class BoardKeysFirstMixin {

    // Optional (require = 0): if another mod has changed this vanilla method so the hook cannot find its place, the
    // game still starts, and the board's verbs give way to those keys as they always did.
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void gathering$theBoardsVerbsFirst(long window, int key, int scanCode, int action, int modifiers,
            CallbackInfo info) {
        if (window == Minecraft.getInstance().getWindow().getWindow()
                && TableScreen.takesAVerbsKeyFirst(key, scanCode, action, modifiers)) {
            info.cancel();
        }
    }
}
