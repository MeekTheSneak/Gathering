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

    /**
     * The slots vanilla will actually write for a creative slot packet.
     * <p>Its own window, read off {@code ServerGamePacketListenerImpl}: one to forty-five is stored
     * and a negative slot is a drop, which is a real gesture and goes through here so that dropping a
     * deck drops the real one rather than the stand-in. Everything else - nought, or past
     * forty-five - vanilla throws away, so anything done for it is done to an inventory that is not
     * about to change.
     */
    private static final int FIRST_REAL_SLOT = 1;

    private static final int LAST_REAL_SLOT = 45;

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
        // Only the slots the game itself will go on to write. This runs before vanilla has decided
        // whether to accept the packet at all, and it refuses several - a slot outside one to
        // forty-five, a stack over its limit - so anything done here for a slot vanilla will throw
        // out is done to an inventory that is not about to change. A client sending a slot number
        // outside that window could have had the real deck taken out of its slot and the only
        // remembered copy dropped, with vanilla then storing nothing: the deck gone with no way back.
        if (slot >= 0 && (slot < FIRST_REAL_SLOT || slot > LAST_REAL_SLOT)) {
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
        int lyingIn = -1;
        if (real == null) {
            lyingIn = slotHolding(player, handle);
            real = lyingIn < 0 ? null : player.inventoryMenu.getSlot(lyingIn).getItem();
        }
        if (real == null) {
            return incoming;
        }
        if (lyingIn >= 0 && lyingIn != slot) {
            // The deck is being put down here while the real one is still lying somewhere else. On a
            // drag between slots that is the same deck arriving before its old slot is reported empty,
            // and taking it out here is what that slot's own packet would do a moment later. On a
            // creative hotbar being loaded it is a saved copy of a deck the player still has, and
            // without this the player ended up with two real decks of the same cards.
            player.inventoryMenu.getSlot(lyingIn).set(ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        }
        ItemStack restored = real.copy();
        restored.setCount(incoming.getCount());
        // The deck as it was when it left its slot, which is what the client's copy was made from.
        DeckComponent left = DeckItem.deckOf(real).orElse(null);
        // What the deck holds now, which is the vault's to say. The copy remembered when the deck left
        // its slot is the deck as it was then, and a card put into it while it sat on the creative
        // cursor - a gesture the server is told about and does itself, see DeckSweeps - happened after
        // that. Putting the remembered copy back dropped the card that had just gone in.
        DeckVault.deckOf(player.getUUID(), handle).ifPresent(
                now -> restored.set(GatheringComponents.DECK.get(), now));
        return withCardsAddedTo(restored, incoming, left);
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
     * <p>Except the ones the server has already put in itself. Both halves of the gesture run: the
     * server does the insert it was told about (see {@link DeckSweeps}) and the client does the same
     * click on its own copy, so the card is in both - and adding every face-up card of the copy on top
     * of the deck the server now holds put each one in twice. Doing it again minted another pair. So
     * what the deck has gained since it left its slot is taken off what the copy is offering, and only
     * a card the server never saw go in is added.
     *
     * @param left the deck as it was when it left its slot, which is what the client's copy was made
     *             from, or null where the server has no such record
     */
    private static ItemStack withCardsAddedTo(ItemStack restored, ItemStack incoming, DeckComponent left) {
        DeckComponent theirs = DeckItem.deckOf(incoming).orElse(null);
        DeckComponent mine = DeckItem.deckOf(restored).orElse(null);
        if (theirs == null || mine == null) {
            return restored;
        }
        List<CardComponent> put = notAlreadyIn(theirs.entries(), left == null ? List.of() : left.entries(), mine.entries());
        List<CardComponent> putAside = notAlreadyIn(theirs.sideboard(), left == null ? List.of() : left.sideboard(),
                mine.sideboard());
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

    /**
     * The face-up cards of the client's copy that the server has not already put in for itself.
     * <p>What the server put in is exactly what the deck has gained since it left the slot: the cards
     * it holds now, less the ones it held then. Each of those cancels one face-up card of the copy.
     *
     * @param theirs what the client's copy holds
     * @param left   what the deck held when it left the slot
     * @param now    what the server's deck holds
     */
    private static List<CardComponent> notAlreadyIn(List<CardComponent> theirs, List<CardComponent> left,
            List<CardComponent> now) {
        List<CardComponent> gained = new java.util.ArrayList<>(now);
        for (CardComponent had : left) {
            gained.remove(had);
        }
        List<CardComponent> put = new java.util.ArrayList<>();
        for (CardComponent card : theirs) {
            if (card.isHidden()) {
                continue;
            }
            if (!gained.remove(card)) {
                put.add(card);
            }
        }
        return List.copyOf(put);
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
        // And the vault learns it too, so a deck that has just gone onto the creative cursor is a deck
        // the server can put cards into. Without this the server only knew decks that had been swept
        // before, so the same gesture took two different paths depending on the deck's history: on one
        // the server did the insert and on the other only the client did, which is a card in the deck
        // twice or a card in no deck at all. One path.
        DeckItem.deckOf(leaving).ifPresent(deck -> DeckVault.remember(player.getUUID(), handle, deck));
        while (decks.size() > MOST_REMEMBERED) {
            decks.remove(decks.keySet().iterator().next());
        }
    }

    /**
     * Whether this deck has left one of this player's own slots and not come back - which in a creative
     * inventory means it is on their cursor.
     * <p>The one thing the server can know about a cursor it is never sent. Without it, a client could
     * name any deck its owner had ever held and have cards taken out of the world into a copy nothing
     * would ever show again.
     */
    public static boolean isInHand(ServerPlayer player, UUID handle) {
        LinkedHashMap<UUID, ItemStack> decks = player == null ? null : REMEMBERED.get(player.getUUID());
        return decks != null && handle != null && decks.containsKey(handle);
    }

    private static ItemStack forget(ServerPlayer player, UUID handle) {
        LinkedHashMap<UUID, ItemStack> decks = REMEMBERED.get(player.getUUID());
        return decks == null ? null : decks.remove(handle);
    }

    /** Which of the player's own menu slots holds this deck for real, or -1. */
    private static int slotHolding(ServerPlayer player, UUID handle) {
        for (int at = 0; at < player.inventoryMenu.slots.size(); at++) {
            ItemStack stack = player.inventoryMenu.getSlot(at).getItem();
            if (!isHiddenCopy(stack) && handle.equals(DeckItem.handleOf(stack).orElse(null))) {
                return at;
            }
        }
        return -1;
    }

    /** Whether this deck, or the pool it carries, is the copy clients are sent rather than the real one. */
    static boolean isHiddenCopy(ItemStack stack) {
        boolean deckHidden = DeckItem.deckOf(stack).map(deck -> deck.isRedacted()).orElse(false);
        DraftedPool pool = stack.get(GatheringComponents.POOL.get());
        boolean poolHidden = pool != null && pool.cards().stream().anyMatch(CardComponent::isHidden);
        return deckHidden || poolHidden;
    }
}
