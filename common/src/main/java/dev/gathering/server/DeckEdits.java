package dev.gathering.server;

import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.registry.GatheringComponents;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Applies a deck edit the player asked for, on the server, to the deck in their hand.
 * <p>Every rule about what a deck may contain lives on this side. The screen is a view: it
 * sends what the player clicked and then waits to be told what the deck now is, which
 * arrives for free on the next held-item sync. Nothing here trusts the client's picture of
 * the deck, and every disagreement resolves as "do nothing" rather than as an error, because
 * a click that raced a change is ordinary rather than hostile.
 * <p>No legality check on any of it. Whether a card may be a commander, or how big a
 * sideboard may be, is a format question, and the validator answers it when a game starts.
 * Refusing here would make the screen argue with a player about a deck they have not chosen a
 * format for yet - and would be wrong for every house rule and every card the validator does
 * not know about.
 * <p>Only ever called from a serverbound payload handler. It takes a {@link Player} rather
 * than a {@code ServerPlayer} because nothing it does needs the wider type, and the narrower
 * one makes the deck rules reachable from a test without standing up a connection.
 */
public final class DeckEdits {

    private DeckEdits() {
    }

    /**
     * Calls the deck in somebody's hand something else.
     * <p>The one thing a deck could not have done to it. Import names a deck and so does a
     * precon; a deck started by putting two cards together has no name at all and had no way
     * to get one, which made "start a deck" a thing you could do once and never finish.
     * <p>A blank name is allowed and means what it says: the deck goes back to being called
     * "Deck". Refusing to clear a name would be a rename that only works in one direction.
     */
    public static void rename(Player player, dev.gathering.network.RenameDeckPayload asked) {
        InteractionHand hand = asked.hand();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DeckItem) || stack.getCount() != 1) {
            return;
        }
        DeckComponent deck = DeckItem.deckOf(stack).orElse(null);
        if (deck == null || deck.name().equals(asked.name())) {
            return;
        }
        stack.set(GatheringComponents.DECK.get(), deck.named(asked.name()));
    }

    /**
     * Sleeves the deck in hand.
     * <p>The same shape as a rename and for the same reasons: the deck is the one being held,
     * a choice that changes nothing is dropped rather than written, and what a player picks
     * decides only what their own cards look like from behind.
     */
    public static void sleeve(Player player, dev.gathering.network.SleeveDeckPayload asked) {
        InteractionHand hand = asked.hand();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DeckItem) || stack.getCount() != 1) {
            return;
        }
        DeckComponent deck = DeckItem.deckOf(stack).orElse(null);
        if (deck == null || deck.sleeve() == asked.chosen()) {
            return;
        }
        stack.set(GatheringComponents.DECK.get(), deck.sleeved(asked.chosen()));
    }

    public static void handle(Player player, DeckEditPayload edit) {
        InteractionHand hand = edit.hand();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DeckItem) || stack.getCount() != 1) {
            return;
        }
        Optional<DeckComponent> held = DeckItem.deckOf(stack);
        if (held.isEmpty()) {
            return;
        }
        DeckComponent deck = held.get();

        Optional<DeckComponent> updated = switch (edit.action()) {
            case TAKE -> take(player, deck, edit.from(), edit.card());
            case MOVE -> deck.moved(edit.from(), edit.to(), edit.card());
        };
        updated.ifPresent(next -> {
            if (next.isEmpty()) {
                // A deck with no cards is a deckbox with nothing in it. Taking the last card
                // out should hand you a card, not a card and an empty object to tidy away.
                player.setItemInHand(hand, ItemStack.EMPTY);
            } else {
                stack.set(GatheringComponents.DECK.get(), next);
            }
        });
    }

    /**
     * Takes one copy out of the deck and hands it to the player.
     * <p>The card leaves the deck only if it actually reaches the player: giving a card to a
     * full inventory drops it at their feet rather than deleting it, and if even that cannot
     * happen the deck is left alone. A card is a collection item and must never evaporate
     * because a bag was full.
     */
    private static Optional<DeckComponent> take(
            Player player, DeckComponent deck, DeckComponent.Section section, CardComponent card) {
        Optional<DeckComponent> without = deck.withoutOne(section, card);
        if (without.isEmpty()) {
            return Optional.empty();
        }
        ItemStack drawn = CardItem.of(card);
        if (!player.getInventory().add(drawn)) {
            player.drop(drawn, false);
        }
        return without;
    }

}
