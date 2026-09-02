package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.service.CardDataService;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Puts a single named card in a player's hand.
 * <p>Small but load-bearing: until a card can be held, none of the card-reading half of the
 * mod is reachable. Import produces a deck, a deck is not a card, and a creative-mode card
 * item with no data on it is a blank. This is how a card becomes a thing you can look at,
 * and later it is how an admin grants a card and how a collection hands one over.
 * <p>The metadata summary goes out with the item, because a client that holds a card it has
 * never been told about knows nothing about it - correctly, and unhelpfully.
 */
public final class CardGrant {

    private CardGrant() {
    }

    /**
     * Resolves a card by name and gives it to the player.
     * <p>Runs entirely on the card pipeline's executor and returns to the server thread to
     * touch the inventory, like every other path that talks to Scryfall.
     */
    public static void byName(ServerPlayer player, CardDataService service, String cardName, boolean foil) {
        service.findByName(cardName)
                .whenComplete((found, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.card_lookup_failed", cardName));
                        return;
                    }
                    found.ifPresentOrElse(
                            card -> give(player, card, foil),
                            () -> player.sendSystemMessage(Component.translatable(
                                    "message.gathering.card_not_found", cardName)));
                }));
    }

    /** Server thread only. */
    public static void give(ServerPlayer player, CardMetadata card, boolean foil) {
        ItemStack stack = CardItem.of(CardComponent.of(
                CardIdentity.ofPrinting(card.scryfallId(), foil)));

        // The summary first: a client told about a card before it holds one never renders a
        // blank, which is the difference between "loading" and "broken" on screen.
        Sending.to(player,
                new CardMetadataPayload(List.of(CardSummary.of(card))));

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable("message.gathering.card_given", card.name()));
    }

    /** Exposed for the game tests, which build the stack without a player to give it to. */
    public static Optional<ItemStack> stackFor(CardMetadata card, boolean foil) {
        if (card == null || card.scryfallId() == null) {
            return Optional.empty();
        }
        return Optional.of(CardItem.of(CardComponent.of(CardIdentity.ofPrinting(card.scryfallId(), foil))));
    }
}
