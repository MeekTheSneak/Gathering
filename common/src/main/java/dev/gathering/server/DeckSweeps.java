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
        ItemStack carried = deckInHand(player, menu, payload);
        if (carried == null) {
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
        if (!anything) {
            return;
        }
        if (menu.getCarried() == carried) {
            menu.setCarried(carried);
        } else {
            // The creative inventory keeps its cursor to itself, so there is nothing here to put the
            // deck back into. It is kept instead, and the copy the client eventually puts down - hidden
            // cards and all - is restored from this: see CreativeDecks.
            dev.gathering.server.DeckVault.remember(player.getUUID(), DeckItem.handleOf(carried).orElse(null),
                    DeckItem.deckOf(carried).orElse(null));
        }
        menu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * The deck the cards are going into: the one on the server's own cursor where there is one, and
     * otherwise the one the client named, for the creative inventory.
     * <p>The creative inventory does its clicks on the client and never sends the cursor, so a deck
     * held there exists on the server only as what it last saw - which is what the vault keeps. The
     * client says which deck by its handle; the handle is looked up among this player's own decks and
     * nobody else's, and the cards still come out of slots the server reads for itself.
     */
    private static ItemStack deckInHand(ServerPlayer player, AbstractContainerMenu menu, DeckSweepPayload payload) {
        ItemStack carried = menu.getCarried();
        if (carried.getItem() instanceof DeckItem && carried.getCount() == 1 && DeckItem.deckOf(carried).isPresent()) {
            return carried;
        }
        java.util.UUID handle = payload.deck().orElse(null);
        if (handle == null || !player.hasInfiniteMaterials()) {
            return null;
        }
        var kept = dev.gathering.server.DeckVault.deckOf(player.getUUID(), handle).orElse(null);
        if (kept == null) {
            return null;
        }
        ItemStack deck = DeckItem.of(kept);
        deck.set(dev.gathering.registry.GatheringComponents.DECK_HANDLE.get(), handle);
        return deck;
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
