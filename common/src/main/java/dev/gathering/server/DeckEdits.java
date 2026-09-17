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

    /**
     * A deck the client has just made in the creative menu, said before the stack arrives.
     * <p>That menu never replays the click on the server - it sends the resulting stack, and a deck
     * component's only wire format replaces every card in it with a stand-in, in both directions. So
     * the deck reached the server with its cards already gone, under a handle nothing had seen, and
     * there was no copy of the real list anywhere. This is that copy, and it goes to the vault the
     * server already reads when a redacted deck turns up: the repair happens on the path that was
     * already there rather than as a second way to make a deck.
     * <p>Refused unless the sender is in creative, where they can conjure any card in the game from
     * the menu they are standing in - so believing them grants nothing that was not already theirs.
     * A player who is not in creative is refused and does not need it, because for them the server
     * ran the click and built the deck itself.
     * <p>Refused, too, for a deck that arrives already hidden: that is a client passing on a copy it
     * was given rather than one it made, and remembering it would write stand-ins over the truth.
     */
    public static void made(Player player, dev.gathering.network.DeckMadePayload said) {
        if (!player.getAbilities().instabuild) {
            return;
        }
        if (said.handle() == null || said.deck() == null || said.deck().isRedacted()) {
            return;
        }
        // Only the one shape the gesture this speaks for can make: two cards, nothing else, no name.
        // Without it this is a message that writes an arbitrary deck of a thousand cards into the
        // server's memory as often as a client cares to send it, which is a cost a client should not
        // be able to choose. See CardItem#overrideOtherStackedOnMe, which builds exactly this.
        DeckComponent made = said.deck();
        if (made.entries().size() != 2 || !made.commanders().isEmpty() || !made.sideboard().isEmpty()
                || !made.stories().isEmpty() || !made.name().isEmpty() || made.loaner()) {
            return;
        }
        // And filed under the player who made it. A handle is not a secret - it rides on the item,
        // where every client that can see the item is sent it - so a vault keyed on the handle alone
        // would let anybody who has walked past a deck rewrite what the server thinks is in it.
        dev.gathering.server.DeckVault.remember(player.getUUID(), said.handle(), made);
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
        // Never the stand-in. A client's copy of a deck is a row of cards it is not being told the names
        // of, and an edit naming one is a client whose real list has not arrived - so obeying it would
        // take a card out by an identity nothing has and hand back a blank one.
        if (edit.card() != null && edit.card().isHidden()) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.gathering.deck_not_listed_yet"));
            return;
        }
        if (deck.loaner() && edit.action() == DeckEditPayload.Action.TAKE) {
            // A card taken out of a loaner would be a card made out of nothing.
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.gathering.loaner_not_kept"));
            return;
        }

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
        // And its history comes out with it, if the deck was keeping one for this printing.
        DeckComponent left = without.get();
        var story = left.storyOf(card).orElse(null);
        if (story != null) {
            drawn.set(dev.gathering.registry.GatheringComponents.STORY.get(),
                    new dev.gathering.item.StoryComponent(story));
            left = left.withoutStoryOf(card);
        }
        dev.gathering.server.Handing.give(player, drawn);
        return Optional.of(left);
    }

}
