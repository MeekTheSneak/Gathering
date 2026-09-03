package dev.gathering.server;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Giving something to a player without ever destroying it.
 * <p>{@code Inventory#add} cannot be trusted to say whether it worked. Its last branch reads:
 * <pre>
 *     if (stack.getCount() == i &amp;&amp; this.player.hasInfiniteMaterials()) {
 *         stack.setCount(0);
 *         return true;
 *     }
 * </pre>
 * so for a creative player with no room it empties the stack and reports success. Every caller
 * in this mod was written as {@code if (!add(stack)) drop(stack)}, which is correct in survival
 * and never fires in creative - the card is gone and the code believes it was delivered.
 * <p>Reported from a real session as a card deleted when a deck was edited with a full bag. It
 * was every route a card can arrive by, not that one: packs, trades, draft picks, loaners, a
 * collection take, the ante pot paying out. A card is a collection item and must never
 * evaporate because a bag was full.
 * <p>So room is worked out before anything is handed over, and what will not fit is dropped
 * rather than offered.
 */
public final class Handing {

    private Handing() {
    }

    /**
     * Puts this stack in the player's inventory, or on the floor at their feet.
     * <p>The stack is consumed either way: after this it is empty, and the item exists in the
     * world exactly once.
     */
    public static void give(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        if (!hasRoomFor(player, stack)) {
            player.drop(stack.copyAndClear(), false);
            return;
        }
        player.getInventory().add(stack);
        // Survival's honest answer: whatever would not fit is still in the stack.
        if (!stack.isEmpty()) {
            player.drop(stack.copyAndClear(), false);
        }
    }

    /**
     * Whether the inventory could take any of this stack at all.
     * <p>A free slot, or a slot already holding the same thing with room on it. Cards carry
     * their own components and so never stack, which makes the free slot the deciding question
     * for almost everything this mod hands out.
     */
    public static boolean hasRoomFor(Player player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        if (inventory.getFreeSlot() >= 0) {
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack there = inventory.getItem(slot);
            if (!there.isEmpty() && there.getCount() < there.getMaxStackSize()
                    && ItemStack.isSameItemSameComponents(there, stack)) {
                return true;
            }
        }
        return false;
    }
}
