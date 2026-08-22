package dev.gathering.service;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;

/**
 * The seam between "the player right-clicked a deck" and the screen that shows its contents.
 *
 * <p>The item lives in common and the screen is client-only, and a common class that named
 * the screen directly would throw {@code NoClassDefFoundError} on a dedicated server - a
 * failure single-player testing never reveals, because single-player runs an integrated
 * server inside the client. So the item calls through here and the client bootstrap binds
 * the real thing.
 *
 * <p>What crosses the seam is the hand, not the deck. The screen can edit the deck, so it
 * needs the one the player is holding right now rather than a copy taken when it opened -
 * otherwise every edit would leave the list showing the deck as it used to be.
 */
@FunctionalInterface
public interface DeckScreenHook {

    void open(InteractionHand hand);

    /** Does nothing, which is exactly right on a server. */
    DeckScreenHook NONE = hand -> { };

    final class Binding {

        private static volatile DeckScreenHook current = NONE;

        private Binding() {
        }

        public static void bind(DeckScreenHook hook) {
            current = java.util.Objects.requireNonNull(hook, "hook");
        }

        public static void open(InteractionHand hand) {
            current.open(hand);
        }
    }

    static void bindOpener(Consumer<InteractionHand> opener) {
        Binding.bind(opener::accept);
    }
}
