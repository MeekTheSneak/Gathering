package dev.gathering.client;

import dev.gathering.core.collection.WantsList;
import dev.gathering.network.MarkWantedPayload;
import dev.gathering.network.WantsPayload;
import java.util.UUID;

/**
 * What this player is chasing, as their client last heard it.
 * <p>Held rather than asked for, because it is read while drawing: every row of a collection,
 * every card out of a pack, every card in a trade wants to know whether it is on the list, and
 * a question asked per card per frame cannot be a packet.
 * <p>Never decided here. Pressing the button sends the change and draws nothing; what is drawn
 * is what came back. A client that marked its own card and then heard the server refuse - a
 * full list does refuse - would show a mark on a card nobody is chasing, and nothing would
 * ever correct it.
 * <p>Emptied when a connection ends: what one server knows about somebody is not true of the
 * next one.
 * <p>Client thread only.
 */
public final class ClientWants {

    private static WantsList wants = WantsList.EMPTY;

    private ClientWants() {
    }

    /** Whether this card is one being chased. */
    public static boolean wants(UUID printing) {
        return wants.wants(printing);
    }

    /** Everything on the list, in the order it was wanted. */
    public static WantsList all() {
        return wants;
    }

    /** Asks the server to put this card on the list, or take it off. */
    public static void mark(UUID printing, boolean wanted) {
        if (printing != null) {
            ClientNetworking.send(new MarkWantedPayload(printing, wanted));
        }
    }

    /** And the other way about, which is what one button does. */
    public static void toggle(UUID printing) {
        mark(printing, !wants(printing));
    }

    /** What the server says the list is. */
    public static void accept(WantsPayload payload) {
        wants = payload.asWants();
    }

    /** What one server knew is not true of the next one. */
    public static void clear() {
        wants = WantsList.EMPTY;
    }
}
