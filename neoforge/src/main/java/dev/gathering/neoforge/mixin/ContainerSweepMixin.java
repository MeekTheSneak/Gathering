package dev.gathering.neoforge.mixin;

import dev.gathering.client.DeckSweep;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
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

    @Shadow
    protected boolean isQuickCrafting;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    protected Set<Slot> quickCraftSlots;

    @Shadow
    private boolean skipNextRelease;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type);

    @Override
    public Slot gathering$hovered() {
        return hoveredSlot;
    }

    @Override
    public void gathering$rightClick(Slot slot) {
        slotClicked(slot, slot.index, 1, ClickType.PICKUP);
    }

    @Override
    public void gathering$standDownTheDrag() {
        isQuickCrafting = false;
        quickCraftSlots.clear();
        skipNextRelease = true;
    }
}
