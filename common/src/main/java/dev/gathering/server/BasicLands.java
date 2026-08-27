package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.AddBasicsPayload;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.service.CardDataService;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Basic lands, given away.
 *
 * <p>A draft pool is forty-five spells. Without lands there is no forty-card deck to build
 * from it at all, so a draft that ended in a pool ended in a deck nobody could put down -
 * which is the whole point of the format not working. Basics are free everywhere in Magic
 * for exactly this reason, and they are free here.
 *
 * <p>Not only for drafted decks. A deck is a deck: somebody building a Commander list from
 * imported cards wants lands in it too, and a rule that only applied to pools would be a
 * rule nobody could guess at.
 *
 * <p>The printing is resolved here rather than named by the client. Every other card that
 * reaches a deck came from somewhere - imported, drafted, opened - and this is the one that
 * is conjured out of nothing, so a client naming a printing would be a client asking to be
 * handed any card in Magic.
 */
public final class BasicLands {

    private BasicLands() {
    }

    public static void handle(ServerPlayer player, AddBasicsPayload asked) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.basics_no_service"));
            return;
        }
        InteractionHand hand = asked.hand();
        if (DeckItem.deckOf(player.getItemInHand(hand)).isEmpty()) {
            return;
        }

        // Off the server thread, like every other card lookup: a basic this server has not
        // seen before is a file read at best and a Scryfall round trip at worst, and neither
        // belongs on the thread the game ticks on.
        cards.findByName(asked.land().printedName()).whenComplete((found, failure) ->
                player.server.execute(() -> give(player, asked, found, failure)));
    }

    private static void give(
            ServerPlayer player, AddBasicsPayload asked,
            Optional<CardMetadata> found, Throwable failure) {
        if (player.hasDisconnected()) {
            return;
        }
        CardMetadata land = failure == null && found != null ? found.orElse(null) : null;
        if (land == null) {
            // Said out loud rather than swallowed. A button that does nothing and explains
            // nothing is worse than one that is not there.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.basics_not_found", asked.land().printedName()));
            return;
        }
        if (!land.isBasicLand()) {
            // Whatever came back is not a basic land, so it is not what this hands out. Only
            // reachable if Scryfall answers a name with something surprising, and the answer
            // to that is to do nothing rather than to put it in somebody's deck.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.basics_not_found", asked.land().printedName()));
            return;
        }

        // Re-read now rather than writing back the deck as it was when the lookup started: a
        // network round trip is long enough to edit it, and writing back a stale copy would
        // undo whatever happened in between. It does not identify the deck - somebody who
        // swaps decks inside the round trip gets their lands in the new one - and that is
        // left alone deliberately: the cost is a couple of basics in the wrong deckbox,
        // which are free and one click to remove, and the check that would prevent it would
        // also silently drop a request from anybody who edited their deck while waiting.
        InteractionHand hand = asked.hand();
        ItemStack stack = player.getItemInHand(hand);
        DeckComponent deck = DeckItem.deckOf(stack).orElse(null);
        if (deck == null) {
            return;
        }

        DeckComponent grown = grow(
                deck, CardComponent.of(CardIdentity.ofPrinting(land.scryfallId())), asked.howMany());
        if (grown.totalCards() == deck.totalCards()) {
            player.sendSystemMessage(Component.translatable("message.gathering.basics_deck_full"));
            return;
        }
        stack.set(GatheringComponents.DECK.get(), grown);
    }

    /**
     * The deck with as many of this card added to the mainboard as will fit.
     *
     * <p>Separated from the looking-up so it can be checked without a card service standing
     * behind it. It is the only part of this with any arithmetic in it, and the only part
     * where "as many as will fit" could quietly become "none of them".
     *
     * <p>Whatever fitted is kept. Refusing the whole request because the last copy did not
     * fit would throw away the ones that did, and somebody who asked for five into a deck
     * with room for two wants the two.
     *
     * <p>Public so the game tests can reach it, which is the point of separating it.
     */
    public static DeckComponent grow(DeckComponent deck, CardComponent card, int howMany) {
        DeckComponent grown = deck;
        for (int copy = 0; copy < howMany; copy++) {
            DeckComponent next = grown.withAdded(DeckComponent.Section.MAINBOARD, card).orElse(null);
            if (next == null) {
                break;
            }
            grown = next;
        }
        return grown;
    }
}
