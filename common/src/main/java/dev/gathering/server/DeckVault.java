package dev.gathering.server;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What is really in the decks players are carrying, by each deck's handle, so a copy with its cards
 * hidden can never stand in for the deck.
 * <p>A deck item is sent to clients with its cards hidden - see {@code DeckComponent.PUBLIC_STREAM_CODEC}
 * - and a player in creative mode sends the server back whatever their client holds for every slot
 * they touch in the creative menu. So moving a deck in that menu, or making one by right-clicking a card
 * onto another, handed the server a deck of hidden stand-ins: the owner watched its cards say "Loading"
 * for ever, and a card taken out of it came out blank.
 * <p>So the server remembers the real contents of every deck it sees in a player's pockets, and puts
 * them back on a copy that arrives hidden. What a creative click changes in a deck, the client says
 * separately - which cards went in - and that is applied here first. A creative player may make any card
 * at all, so taking their word for which cards they put in gives them nothing they did not have.
 * <p>Server thread only. Forgotten when the server stops.
 */
public final class DeckVault {

    /** How many decks are remembered per player at once; the least recently seen goes first. */
    private static final int REMEMBERED = 256;

    /**
     * Kept under the player holding the deck, and then under the deck's handle.
     * <p>Per player, and not by handle alone, because a handle is not a secret. It rides on the item in
     * its own component, which is sent to every client that can see the item - somebody carrying a deck
     * past you, a deck in a display case, a deck lying on the ground. A vault keyed on the handle alone
     * is a vault whose keys are broadcast, so anything that can write to it by handle can write over
     * somebody else's deck. Under the holder, the worst a write can reach is the writer's own cards.
     */
    private static final Map<UUID, LinkedHashMap<UUID, DeckComponent>> KEPT = new HashMap<>();

    private DeckVault() {
    }

    /** Remembers the real contents of a deck. Hidden copies are not remembered, which is the point. */
    public static void remember(UUID player, UUID handle, DeckComponent deck) {
        if (player == null || handle == null || deck == null || deck.isRedacted()) {
            return;
        }
        KEPT.computeIfAbsent(player, who -> new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, DeckComponent> eldest) {
                return size() > REMEMBERED;
            }
        }).put(handle, deck);
    }

    /**
     * The deck this player's vault holds under that handle, if it holds one.
     * <p>For the one gesture the server cannot see for itself: a deck on the cursor of a creative
     * inventory, which is the client's alone. Their own decks only - the vault is per player.
     */
    public static Optional<DeckComponent> deckOf(UUID player, UUID handle) {
        LinkedHashMap<UUID, DeckComponent> theirs = player == null ? null : KEPT.get(player);
        return Optional.ofNullable(theirs == null || handle == null ? null : theirs.get(handle));
    }

    /** Everything this player's vault knows, for a test to read. */
    public static java.util.Set<UUID> handlesFor(UUID player) {
        LinkedHashMap<UUID, DeckComponent> theirs = KEPT.get(player);
        return theirs == null ? java.util.Set.of() : java.util.Set.copyOf(theirs.keySet());
    }

    /**
     * The deck this stack should be: itself when its cards are real, and when they are hidden, its box -
     * name, note, color, sleeves, commanders - holding the cards last known to be in it. Empty when a
     * hidden copy arrives for a deck never seen with its cards.
     */
    public static Optional<DeckComponent> real(UUID player, UUID handle, DeckComponent deck) {
        if (deck == null || !deck.isRedacted()) {
            return Optional.ofNullable(deck);
        }
        LinkedHashMap<UUID, DeckComponent> theirs = player == null ? null : KEPT.get(player);
        DeckComponent known = theirs == null || handle == null ? null : theirs.get(handle);
        if (known == null) {
            return Optional.empty();
        }
        // The cards it is kept with, and then whatever this copy carries face up. A copy that crossed the
        // wire has every card hidden, so a card in it that is not hidden is one the client has just put in -
        // a creative player right-clicking cards onto a deck, whose click the server never sees. Taking the
        // kept list alone threw those cards away, which is a card deleted rather than a card moved.
        //
        // Believed only when the stand-ins account for exactly the cards the vault knows about. That
        // is what a wire copy looks like: one stand-in per card that was in the deck, and anything
        // face up beside them is new. The test used to be isRedacted, which asks whether *any* card
        // is hidden - so a copy of a hundred real cards with one stand-in among them read as a
        // hundred cards just added, and the deck came back holding two hundred. When the counts do
        // not line up there is no way to tell which cards are which, so it is put back as it was
        // last known and nothing is added.
        DeckComponent restored = deck.holding(known);
        long standIns = deck.entries().stream().filter(CardComponent::isHidden).count();
        if (standIns != known.entries().size()) {
            return Optional.of(restored);
        }
        for (CardComponent card : deck.entries()) {
            if (card == null || card.isHidden()) {
                continue;
            }
            restored = restored.withAdded(DeckComponent.Section.MAINBOARD, card.faceUp()).orElse(restored);
        }
        return Optional.of(restored);
    }

    /** For a server that is stopping. */
    public static void clear() {
        KEPT.clear();
    }
}
