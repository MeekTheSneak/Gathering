package dev.gathering.client;

import dev.gathering.item.DeckComponent;
import dev.gathering.network.MyDeckPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What is really in the decks this player is holding.
 * <p>The deck item's own component is the public one - a name, a color, sleeves, commanders
 * and a thickness - because it is synchronized to everybody who can see the item. The list
 * itself arrives here, addressed to this player about a deck of their own.
 * <p>Kept by which deck it is rather than by which hand it is in. A hand is a place, not an
 * identity: swap two decks between hands and the place says the same thing about a different
 * deck, and a client that believed it would show one list while the player held the other.
 * The handle on the item and the handle in the push are the same value, so a screen either
 * has the list for the deck it is looking at or has nothing and falls back to the public copy.
 * <p>Client thread only.
 */
public final class ClientHeldDeck {

    /** What the server last said about one deck, and which push said it. */
    private record Told(int revision, DeckComponent deck) {
    }

    /**
     * How many decks to remember lists for.
     * <p>Two are held at once, but a player putting decks down and picking them back up walks
     * through more than two, and re-showing a list already sent beats a blank screen while a
     * fresh push crosses. Past this the least recently touched is dropped, which costs that
     * deck one fallback-to-public frame if it comes back.
     */
    private static final int REMEMBERED = 16;

    private static final Map<UUID, Told> HELD = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Told> eldest) {
            return size() > REMEMBERED;
        }
    };

    private ClientHeldDeck() {
    }

    /**
     * Takes what the server says is in this deck.
     * <p>Older pushes are dropped rather than applied. Two pushes about one deck can arrive
     * out of order after an edit, and applying the older one leaves the screen showing a deck
     * the server has already changed.
     */
    public static void accept(MyDeckPayload said) {
        if (said == null || said.deck() == null || said.deckHandle() == null) {
            return;
        }
        Told have = HELD.get(said.deckHandle());
        if (have != null && said.revision() < have.revision()) {
            return;
        }
        HELD.put(said.deckHandle(), new Told(said.revision(), said.deck()));
    }

    /**
     * The real contents of that deck, if the server has said.
     * <p>Empty before the first push, which is a frame or two after picking a deck up: a
     * screen reading this falls back to the public copy and shows the count without the list.
     */
    public static Optional<DeckComponent> of(UUID deckHandle) {
        return deckHandle == null
                ? Optional.empty()
                : Optional.ofNullable(HELD.get(deckHandle)).map(Told::deck);
    }

    /** Between worlds: one server's decks are not the next one's. */
    public static void clear() {
        HELD.clear();
    }
}
