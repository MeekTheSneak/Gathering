package dev.gathering.client;

import dev.gathering.item.CardItem;
import dev.gathering.item.DeckItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Holding right-click with a deck on the cursor and sweeping it over cards puts every one of them in.
 * <p>The bundle gesture, as Mouse Tweaks players expect it: that mod drags a bundle over slots and
 * right-clicks each one, but only for the vanilla bundle, so a deck dragged over a row of cards did
 * nothing - and a drag that crossed an empty slot dropped the deck into it. Done here, for every
 * inventory screen, with Mouse Tweaks or without it.
 * <p>Nothing new reaches the server: each card is one ordinary right-click of the deck on its slot,
 * the same click a player makes by hand, and the server inserts it as it always has. What this adds
 * is only which slots are clicked. Once a sweep has started, the rest of the drag is the sweep's -
 * vanilla's own right-drag, which would spread the deck over empty slots, and the release click,
 * which would drop it in the last slot swept, are both stood down.
 * <p>Client-only.
 */
public final class DeckSweep {

    /** What a container screen lets the sweep do. Put on vanilla's screen by each loader's hook. */
    public interface Sweepable {

        /** The slot under the cursor, or null. */
        Slot gathering$hovered();

        /** One ordinary right-click on a slot, as the screen would send it. */
        void gathering$rightClick(Slot slot);

        /** Stands vanilla's own right-drag down and swallows the release that ends it. */
        void gathering$standDownTheDrag();
    }

    /** The sweep this drag is, when a deck was on the cursor as the button went down; null otherwise. */
    private static dev.gathering.core.ui.DragSweep sweep;

    private DeckSweep() {
    }

    /** The right button going down: armed, if a single deck is on the cursor. */
    public static void pressed(AbstractContainerScreen<?> screen, int button) {
        sweep = null;
        if (button != 1 || !(screen instanceof Sweepable sweepable) || !carryingADeck(screen)) {
            return;
        }
        sweep = dev.gathering.core.ui.DragSweep.armedOn(indexOf(screen, sweepable.gathering$hovered()));
    }

    /**
     * The mouse moving with a button down.
     *
     * @return whether the sweep has this drag, and nothing else should see it
     */
    public static boolean dragged(AbstractContainerScreen<?> screen, int button) {
        dev.gathering.core.ui.DragSweep armed = sweep;
        if (button != 1 || armed == null || !(screen instanceof Sweepable sweepable)) {
            return false;
        }
        if (!carryingADeck(screen)) {
            // The deck left the cursor - put down, or swapped - and the sweep with it.
            sweep = null;
            return armed.sweeping();
        }
        var slots = screen.getMenu().slots;
        var step = armed.movedTo(indexOf(screen, sweepable.gathering$hovered()),
                index -> index >= 0 && index < slots.size() && takes(screen, slots.get(index)));
        if (step.standDown()) {
            sweepable.gathering$standDownTheDrag();
        }
        for (int index : step.clicks()) {
            sweepable.gathering$rightClick(slots.get(index));
        }
        return step.ours();
    }

    /** The button coming up, which ends any sweep. */
    public static void released(int button) {
        if (button == 1) {
            sweep = null;
        }
    }

    /** Where a slot is in the screen's menu, or none. */
    private static int indexOf(AbstractContainerScreen<?> screen, Slot slot) {
        return slot == null ? dev.gathering.core.ui.DragSweep.NONE : screen.getMenu().slots.indexOf(slot);
    }

    private static boolean carryingADeck(AbstractContainerScreen<?> screen) {
        ItemStack carried = screen.getMenu().getCarried();
        return carried.getItem() instanceof DeckItem && carried.getCount() == 1 && DeckItem.deckOf(carried).isPresent();
    }

    /** Whether a right-click of the deck on this slot would put its card in. */
    private static boolean takes(AbstractContainerScreen<?> screen, Slot slot) {
        var player = Minecraft.getInstance().player;
        return slot != null && player != null && slot.hasItem() && slot.getItem().getItem() instanceof CardItem
                && slot.allowModification(player)
                && DeckItem.wouldTake(screen.getMenu().getCarried(), slot.getItem());
    }
}
