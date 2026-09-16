package dev.gathering.service;

import dev.gathering.item.DeckComponent;
import java.util.UUID;

/**
 * The seam between "this client just made a deck out of two cards" and telling the server what is
 * in it.
 * <p>The item lives in common and the sending lives in the client, and a common class that named
 * the client's networking directly would throw {@code NoClassDefFoundError} on a dedicated server -
 * a failure single-player testing never reveals, because single-player runs an integrated server
 * inside the client. Same seam, same reason, as {@link DeckScreenHook}.
 * <p>Why anything has to cross at all: the creative menu never replays the click on the server, it
 * sends the resulting stack, and a deck component's one wire format replaces every card in it with a
 * stand-in in both directions. So the deck reached the server with its cards already gone and no
 * copy of the real list anywhere. See {@code DeckMadePayload}.
 */
@FunctionalInterface
public interface DeckMadeHook {

    void say(UUID handle, DeckComponent deck);

    /** Does nothing, which is exactly right on a server, where the click was run for real. */
    DeckMadeHook NONE = (handle, deck) -> { };

    final class Binding {

        private static volatile DeckMadeHook current = NONE;

        private Binding() {
        }

        public static void bind(DeckMadeHook hook) {
            current = java.util.Objects.requireNonNull(hook, "hook");
        }

        public static void say(UUID handle, DeckComponent deck) {
            current.say(handle, deck);
        }
    }
}
