package dev.gathering.server;

import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
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

    /** How many decks are remembered at once; the least recently seen goes first. */
    private static final int REMEMBERED = 2048;

    private static final Map<UUID, DeckComponent> KEPT = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, DeckComponent> eldest) {
            return size() > REMEMBERED;
        }
    };

    private DeckVault() {
    }

    /** Remembers the real contents of a deck. Hidden copies are not remembered, which is the point. */
    public static void remember(UUID handle, DeckComponent deck) {
        if (handle != null && deck != null && !deck.isRedacted()) {
            KEPT.put(handle, deck);
        }
    }

    /**
     * The deck this stack should be: itself when its cards are real, and when they are hidden, its box -
     * name, note, color, sleeves, commanders - holding the cards last known to be in it. Empty when a
     * hidden copy arrives for a deck never seen with its cards.
     */
    public static Optional<DeckComponent> real(UUID handle, DeckComponent deck) {
        if (deck == null || !deck.isRedacted()) {
            return Optional.ofNullable(deck);
        }
        DeckComponent known = handle == null ? null : KEPT.get(handle);
        if (known == null) {
            return Optional.empty();
        }
        // The cards it is kept with, and then whatever this copy carries face up. A copy that crossed the
        // wire has every card hidden, so a card in it that is not hidden is one the client has just put in -
        // a creative player right-clicking cards onto a deck, whose click the server never sees. Taking the
        // kept list alone threw those cards away, which is a card deleted rather than a card moved.
        DeckComponent restored = deck.holding(known);
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
