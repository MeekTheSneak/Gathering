package dev.gathering.service;

import dev.gathering.item.CardComponent;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * The seam between a card going into a deck on a client's creative menu and the server being told so.
 * <p>The items live in common and the creative menu is client-only, so the items call through here and
 * the client bootstrap binds the real thing - which sends {@code CreativeDeckEditPayload} when, and only
 * when, the click happened in the creative menu. Anywhere else the server sees the click itself.
 */
@FunctionalInterface
public interface CreativeDeckHook {

    void cardsWentIn(Player player, UUID deck, List<CardComponent> cards);

    /** Does nothing, which is exactly right on a server. */
    CreativeDeckHook NONE = (player, deck, cards) -> { };

    final class Binding {

        private static volatile CreativeDeckHook current = NONE;

        private Binding() {
        }

        public static void bind(CreativeDeckHook hook) {
            current = java.util.Objects.requireNonNull(hook, "hook");
        }

        public static void cardsWentIn(Player player, UUID deck, List<CardComponent> cards) {
            current.cardsWentIn(player, deck, cards);
        }
    }
}
