package dev.gathering.server;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.DraftedPool;
import dev.gathering.registry.GatheringComponents;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A deck moved in the creative inventory keeps its cards.
 * <p>A deck crosses to clients with its cards hidden - a deck is a held item, and every client
 * nearby is sent it. The creative inventory is the one place vanilla trusts the client's copy of
 * an item: it sends the stack back and the server stores it as it is. So a creative player who
 * picked a deck up and put it down again put back the hidden copy, and every card in it became
 * an unnamed placeholder.
 * <p>So a creative slot change is looked at first. A deck leaving a slot is remembered, by its
 * handle; a hidden copy arriving is replaced with the real deck of the same handle, from that
 * memory or from wherever in the player's inventory the real one still is. A hidden copy of a
 * deck the server has never seen is left as vanilla would leave it.
 * <p>Called by each loader's hook on the creative slot packet. Server thread only.
 */
public final class CreativeDecks {

    /** The most decks remembered per player; a creative inventory rearranged in one go is far fewer. */
    private static final int MOST_REMEMBERED = 64;

    private static final Map<UUID, LinkedHashMap<UUID, ItemStack>> REMEMBERED = new HashMap<>();

    private CreativeDecks() {
    }

    /**
     * The stack to store for a creative slot change.
     *
     * @param slot     the inventory menu slot being set, or negative for a drop
     * @param incoming what the client sent
     */
    public static ItemStack incoming(ServerPlayer player, int slot, ItemStack incoming) {
        if (player == null || incoming == null) {
            return incoming;
        }
        if (slot >= 0 && slot < player.inventoryMenu.slots.size()) {
            remember(player, player.inventoryMenu.getSlot(slot).getItem());
        }
        if (!isHiddenCopy(incoming)) {
            return incoming;
        }
        UUID handle = DeckItem.handleOf(incoming).orElse(null);
        if (handle == null) {
            return incoming;
        }
        ItemStack real = forget(player, handle);
        if (real == null) {
            real = inInventory(player, handle);
        }
        if (real == null) {
            return incoming;
        }
        ItemStack restored = real.copy();
        restored.setCount(incoming.getCount());
        return withCardsAddedTo(restored, incoming);
    }

    /**
     * The server's own deck, plus whatever real cards the client put into its hidden copy.
     * <p>A creative player holding a deck and right-clicking a card puts the card in on their own
     * screen - the creative inventory does its clicks locally and sends the slots afterwards - and the
     * copy they are holding has the deck's own cards hidden. Restoring the server's deck whole then
     * threw the new card away, and the slot it came from arrived empty in the same breath: the card was
     * destroyed by putting it in a deck, which the owner found at once.
     * <p>A card in the hidden copy that is not a stand-in was put there by the player, so it is added to
     * the real deck rather than dropped. Nothing else of the copy is trusted.
     */
    private static ItemStack withCardsAddedTo(ItemStack restored, ItemStack incoming) {
        DeckComponent theirs = DeckItem.deckOf(incoming).orElse(null);
        DeckComponent mine = DeckItem.deckOf(restored).orElse(null);
        if (theirs == null || mine == null) {
            return restored;
        }
        List<CardComponent> put = theirs.entries().stream().filter(card -> !card.isHidden()).toList();
        List<CardComponent> putAside = theirs.sideboard().stream().filter(card -> !card.isHidden()).toList();
        if (put.isEmpty() && putAside.isEmpty()) {
            return restored;
        }
        List<CardComponent> entries = new java.util.ArrayList<>(mine.entries());
        entries.addAll(put);
        List<CardComponent> sideboard = new java.util.ArrayList<>(mine.sideboard());
        sideboard.addAll(putAside);
        if (entries.size() + sideboard.size() > DeckComponent.MAX_CARDS) {
            // A deck cannot hold more than this, and a creative menu is not the place to find out
            // sideways. What the server had stands, and the card stays where it was.
            return restored;
        }
        restored.set(dev.gathering.registry.GatheringComponents.DECK.get(),
                new DeckComponent(mine.name(), mine.description(), mine.owner(), entries, mine.commanders(),
                        sideboard, mine.color(), mine.sleeve(), mine.stories(), mine.loaner()));
        return restored;
    }

    /** Forgets one player, who has gone. */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            REMEMBERED.remove(player.getUUID());
        }
    }

    /** Forgets everybody, for a server that is stopping. */
    public static void clear() {
        REMEMBERED.clear();
    }

    private static void remember(ServerPlayer player, ItemStack leaving) {
        if (leaving == null || leaving.isEmpty() || isHiddenCopy(leaving) || DeckItem.deckOf(leaving).isEmpty()) {
            return;
        }
        UUID handle = DeckItem.handleOf(leaving).orElse(null);
        if (handle == null) {
            return;
        }
        LinkedHashMap<UUID, ItemStack> decks = REMEMBERED.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
        decks.put(handle, leaving.copy());
        while (decks.size() > MOST_REMEMBERED) {
            decks.remove(decks.keySet().iterator().next());
        }
    }

    private static ItemStack forget(ServerPlayer player, UUID handle) {
        LinkedHashMap<UUID, ItemStack> decks = REMEMBERED.get(player.getUUID());
        return decks == null ? null : decks.remove(handle);
    }

    private static ItemStack inInventory(ServerPlayer player, UUID handle) {
        for (var slot : player.inventoryMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!isHiddenCopy(stack) && handle.equals(DeckItem.handleOf(stack).orElse(null))) {
                return stack;
            }
        }
        return null;
    }

    /** Whether this deck, or the pool it carries, is the copy clients are sent rather than the real one. */
    static boolean isHiddenCopy(ItemStack stack) {
        boolean deckHidden = DeckItem.deckOf(stack).map(deck -> deck.isRedacted()).orElse(false);
        DraftedPool pool = stack.get(GatheringComponents.POOL.get());
        boolean poolHidden = pool != null && pool.cards().stream().anyMatch(CardComponent::isHidden);
        return deckHidden || poolHidden;
    }
}
