package dev.gathering.client;

import dev.gathering.item.DeckComponent;
import dev.gathering.network.MyDeckPayload;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.InteractionHand;

/**
 * What is really in the deck this player is holding.
 * <p>The deck item's own component is the public one - a name, a colour, sleeves, commanders
 * and a thickness - because it is synchronized to everybody who can see the item. The list
 * itself arrives here, addressed to this player about a deck in their own hand.
 * <p>Two hands, and nothing else: a deck in a backpack has no screen open on it, and its
 * tooltip is drawn from the public copy like everybody else's.
 * <p>Client thread only.
 */
public final class ClientHeldDeck {

    /** What the server last said about one hand, and which push said it. */
    private record Told(int revision, DeckComponent deck) {
    }

    private static final Map<InteractionHand, Told> HELD = new EnumMap<>(InteractionHand.class);

    private ClientHeldDeck() {
    }

    /**
     * Takes what the server says is in the deck in this hand.
     * <p>Older pushes are dropped rather than applied. Two pushes about one hand can arrive
     * out of order after an edit, and applying the older one leaves the screen showing a deck
     * the server has already changed.
     */
    public static void accept(MyDeckPayload said) {
        if (said == null || said.deck() == null) {
            return;
        }
        Told have = HELD.get(said.hand());
        if (have != null && said.revision() < have.revision()) {
            return;
        }
        HELD.put(said.hand(), new Told(said.revision(), said.deck()));
    }

    /** Which push this client last took for that hand, or -1 if none. */
    public static int revisionOf(InteractionHand hand) {
        Told told = HELD.get(hand);
        return told == null ? -1 : told.revision();
    }

    /**
     * The real contents of the deck in that hand, if the server has said.
     * <p>Empty before the first push, which is a frame or two after picking a deck up: a
     * screen reading this falls back to the public copy and shows the count without the list.
     */
    public static Optional<DeckComponent> of(InteractionHand hand) {
        return Optional.ofNullable(HELD.get(hand)).map(Told::deck);
    }

    /** Between worlds: one server's decks are not the next one's. */
    public static void clear() {
        HELD.clear();
    }
}
