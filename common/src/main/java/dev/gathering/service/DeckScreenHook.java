package dev.gathering.service;

import dev.gathering.item.DeckComponent;
import java.util.function.Consumer;

/**
 * The seam between "the player right-clicked a deck" and the screen that shows its contents.
 *
 * <p>The item lives in common and the screen is client-only, and a common class that named
 * the screen directly would throw {@code NoClassDefFoundError} on a dedicated server - a
 * failure single-player testing never reveals, because single-player runs an integrated
 * server inside the client. So the item calls through here and the client bootstrap binds
 * the real thing.
 */
@FunctionalInterface
public interface DeckScreenHook {

    void open(DeckComponent deck);

    /** Does nothing, which is exactly right on a server. */
    DeckScreenHook NONE = deck -> { };

    final class Binding {

        private static volatile DeckScreenHook current = NONE;

        private Binding() {
        }

        public static void bind(DeckScreenHook hook) {
            current = java.util.Objects.requireNonNull(hook, "hook");
        }

        public static void open(DeckComponent deck) {
            current.open(deck);
        }
    }

    static void bindOpener(Consumer<DeckComponent> opener) {
        Binding.bind(opener::accept);
    }
}
