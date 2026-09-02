package dev.gathering.server;

import dev.gathering.core.collection.CardTally;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.PocketCardsPayload;
import dev.gathering.registry.GatheringComponents;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Cards a player is carrying, going into the deck they are holding.
 * <p>The bundle gesture already does this one slot at a time - carry a deck, right-click a
 * stack of cards, the stack goes in. What it could not do is forty slots, and forty slots is
 * what somebody has after opening a box. This is that, once.
 * <p>The client names what it wants moved and the server moves it, checking each card is
 * really in that player's inventory before it goes anywhere. It has to be checked rather than
 * trusted for the ordinary reason: the list crosses the wire, and a list that was believed
 * would let a client name any card in Magic and be handed it.
 */
public final class PocketCards {

    private PocketCards() {
    }

    public static void handle(ServerPlayer player, PocketCardsPayload asked) {
        InteractionHand hand = asked.hand();
        ItemStack held = player.getItemInHand(hand);
        DeckComponent deck = DeckItem.deckOf(held).orElse(null);
        if (deck == null) {
            // The deck was put away inside the round trip. Nothing to add to, so nothing is
            // taken out of the inventory either - the two have to happen together or not.
            return;
        }

        int went = 0;
        int missed = 0;
        for (CardComponent wanted : asked.cards()) {
            if (deck.totalCards() >= DeckComponent.MAX_CARDS) {
                missed++;
                continue;
            }
            if (!take(player, wanted)) {
                missed++;
                continue;
            }
            Optional<DeckComponent> next =
                    deck.withAdded(DeckComponent.Section.MAINBOARD, wanted.faceUp());
            if (next.isEmpty()) {
                // The deck would not take it after all. The card has already left the
                // inventory, so it goes back rather than being destroyed.
                giveBack(player, wanted);
                missed++;
                continue;
            }
            deck = next.get();
            went++;
        }

        if (went == 0 && missed == 0) {
            return;
        }
        held.set(GatheringComponents.DECK.get(), deck);
        DeckItem.playAssembleSound(player);
        player.sendSystemMessage(
                Component.translatable("message.gathering.pocket_cards_added", went));
        if (missed > 0) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.pocket_cards_short", missed));
        }
    }

    /**
     * The loose cards this player is carrying, counted the way a collection counts.
     * <p>Only cards lying about as items. A card already sleeved into a deck is in the deck,
     * a card in an open pack is in the pack, and neither is a card somebody is holding - so
     * neither is offered to a builder that would take it out of their pockets.
     * <p>Server-side and read straight off the real inventory, which is what makes it safe to
     * pool with a box: the counts a builder shows are counts the server itself worked out.
     */
    public static CardTally loose(ServerPlayer player) {
        CardTally.Builder carrying = CardTally.builder();
        for (ItemStack stack : player.getInventory().items) {
            add(carrying, stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            add(carrying, stack);
        }
        return carrying.build();
    }

    private static void add(CardTally.Builder carrying, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CardItem)) {
            return;
        }
        CardItem.cardOf(stack)
                .map(CardComponent::faceUp)
                .ifPresent(card -> carrying.add(card.toIdentity(), stack.getCount()));
    }

    /**
     * Takes one copy of this card out of the player's own inventory, or says they had none.
     * <p>Matched on the card the stack carries rather than on the slot the client named,
     * because a slot index is a thing that moves: the inventory can be rearranged inside the
     * round trip, and taking whatever is in slot nine by then is how a request for a Forest
     * ends up eating somebody's commander.
     */
    static boolean take(ServerPlayer player, CardComponent wanted) {
        if (wanted == null) {
            return false;
        }
        CardComponent looking = wanted.faceUp();
        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, looking)) {
                stack.shrink(1);
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matches(stack, looking)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Face up either way round: which side a loose card is lying is not part of what it is. */
    private static boolean matches(ItemStack stack, CardComponent looking) {
        return !stack.isEmpty()
                && stack.getItem() instanceof CardItem
                && CardItem.cardOf(stack).map(CardComponent::faceUp).filter(looking::equals).isPresent();
    }

    private static void giveBack(ServerPlayer player, CardComponent card) {
        ItemStack stack = CardItem.of(card);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
