package dev.gathering.server;

import dev.gathering.item.CardItem;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckSweepPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A deck swept over cards, done on the server.
 * <p>The gesture is the client's: right-click held, the cursor dragged across a row of cards. What each
 * card's slot then does is this, on the server's own stacks, through the very method a single
 * right-click uses ({@link DeckItem#overrideStackedOnOther}) - one rule for both.
 * <p>Not a click sent per slot, which is where this started. The creative inventory does its clicks on
 * the client and sends the slots afterwards, and the copy of a deck a client holds has its cards
 * hidden - so a card put in on the client was dropped when the server put its own deck back, and the
 * slot the card came from arrived empty in the same breath. A creative player sweeping a row of cards
 * into a deck destroyed the row.
 * <p>Server thread only.
 */
public final class DeckSweeps {

    private DeckSweeps() {
    }

    /** Puts the card in each named slot into the deck the player is carrying. */
    public static void handle(ServerPlayer player, DeckSweepPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        AbstractContainerMenu menu = menuOf(player, payload.containerId());
        if (menu == null) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (!(carried.getItem() instanceof DeckItem) || carried.getCount() != 1
                || DeckItem.deckOf(carried).isEmpty()) {
            return;
        }
        boolean anything = false;
        for (int id : payload.slots()) {
            if (id < 0 || id >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.getSlot(id);
            if (!(slot.getItem().getItem() instanceof CardItem) || !slot.allowModification(player)) {
                continue;
            }
            // The same call the vanilla click makes, so a sweep can never do what a click cannot.
            anything |= carried.getItem().overrideStackedOnOther(carried, slot, ClickAction.SECONDARY, player);
        }
        if (anything) {
            menu.setCarried(carried);
            menu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * The menu those slots belong to: the one the player has open, or their own inventory - which is
     * what the creative inventory's slots really are, its own menu being the client's alone.
     */
    private static AbstractContainerMenu menuOf(ServerPlayer player, int containerId) {
        if (player.containerMenu != null && player.containerMenu.containerId == containerId) {
            return player.containerMenu;
        }
        return player.inventoryMenu.containerId == containerId ? player.inventoryMenu : null;
    }
}
