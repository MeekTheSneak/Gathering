package dev.gathering.neoforge.mixin;

import dev.gathering.client.DeckSweep;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A card right-clicked into a deck in the creative inventory is told to the server.
 * <p>The creative inventory does its clicks itself, on the client's own copies, and sends the slots it
 * changed afterwards - and a deck crosses the wire with its cards hidden, so the card the client put in
 * came back as a stand-in while the slot it came from arrived empty. The card was destroyed by being put
 * into a deck, which is what the owner found. The click still happens here, so the screen behaves as it
 * looks; what this adds is the server being told which card went into which deck, so its own copy - the
 * one that counts - gains it too. See {@link DeckSweep}.
 * <p>Its own hook rather than the one on the ordinary container screen: this screen overrides that click
 * and never calls it, which is why the first attempt at this never fired.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeDeckClickMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), require = 0)
    private void gathering$deckTakesTheCard(Slot slot, int slotId, int button, ClickType type, CallbackInfo info) {
        if (type == ClickType.PICKUP) {
            DeckSweep.clickedInCreative((CreativeModeInventoryScreen) (Object) this, slot, button);
        }
    }
}
