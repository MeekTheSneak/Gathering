package dev.gathering.fabric.mixin;

import dev.gathering.client.DeckSweep;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * What {@link DeckSweep} needs of an inventory screen: the slot under the cursor, a right-click on a
 * slot, and a way to stand vanilla's right-drag down. All three are the screen's own and private to it.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSweepMixin implements DeckSweep.Sweepable {

    @Shadow
    protected Slot hoveredSlot;

    /**
     * A right-click of a deck onto a card in the creative inventory goes to the server as the gesture it
     * is, and is not done here: see DeckSweep.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "slotClicked", at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
            cancellable = true, require = 0)
    private void gathering$deckClick(Slot slot, int slotId, int button, net.minecraft.world.inventory.ClickType type,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        if (type == net.minecraft.world.inventory.ClickType.PICKUP
                && DeckSweep.clickedInCreative((AbstractContainerScreen<?>) (Object) this, slot, button)) {
            info.cancel();
        }
    }

    @Shadow
    protected boolean isQuickCrafting;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    protected Set<Slot> quickCraftSlots;

    @Shadow
    private boolean skipNextRelease;

    @Override
    public Slot gathering$hovered() {
        return hoveredSlot;
    }

    @Override
    public boolean gathering$isTheCreativeInventory() {
        return (AbstractContainerScreen<?>) (Object) this instanceof CreativeModeInventoryScreen;
    }

    @Override
    public int gathering$serverContainerId() {
        // The creative inventory's own menu is the client's alone; the slots in its inventory tab stand
        // for the player's own menu, which is the one the server has.
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return screen instanceof CreativeModeInventoryScreen && player != null
                ? player.inventoryMenu.containerId
                : screen.getMenu().containerId;
    }

    @Override
    public int gathering$serverSlotId(Slot slot) {
        // Its slots stand in front of the player's own, and the arithmetic that says which is
        // InventorySlots'. Reading the container slot straight was right on the inventory tab and wrong
        // on every other one, where the row along the bottom is the hotbar numbered 0 to 8 - so a card
        // swept there named the crafting square and the armor, and the gesture did nothing at all.
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (!(screen instanceof CreativeModeInventoryScreen) || player == null) {
            return slot.index;
        }
        var slots = screen.getMenu().slots;
        return dev.gathering.core.ui.InventorySlots.creativeSlot(
                slots.indexOf(slot), slots.size(), slot.getContainerSlot(),
                slot.container == player.getInventory());
    }

    @Override
    public void gathering$standDownTheDrag() {
        isQuickCrafting = false;
        quickCraftSlots.clear();
        skipNextRelease = true;
    }

    // Fabric has no event for a drag inside a screen, so the hooks are here. NeoForge's are its screen
    // events, which run before Mouse Tweaks' own.
    @org.spongepowered.asm.mixin.injection.Inject(method = "mouseClicked", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), require = 0)
    private void gathering$pressed(double mouseX, double mouseY, int button,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> result) {
        DeckSweep.pressed((AbstractContainerScreen<?>) (Object) this, button);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "mouseDragged", at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
            cancellable = true, require = 0)
    private void gathering$dragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> result) {
        if (DeckSweep.dragged((AbstractContainerScreen<?>) (Object) this, button)) {
            result.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "mouseReleased", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), require = 0)
    private void gathering$released(double mouseX, double mouseY, int button,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> result) {
        DeckSweep.released(button);
    }
}
