package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.OpenLoanersPayload;
import dev.gathering.network.Sending;
import dev.gathering.network.TakeLoanerPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Lending a deck to somebody who has nothing to play.
 * <p>Where {@link LoanerDecks} is the shelf, this is the counter: who may borrow, when they
 * are asked, and what happens to the deck once they have it.
 * <p>The whole feature is one moment - a player sits down at a table with nothing in their
 * hands and is asked which deck they would like. Everything here exists to make that moment
 * arrive on its own, because a new player does not know to go looking for it, and to make it
 * end with cards on the table rather than with an item and another gesture to work out.
 */
public final class Lending {

    private Lending() {
    }

    /**
     * Offers the shelf to somebody who has just sat down with nothing to play.
     * <p>Silent when they already have a deck: an offer of a loaner to a player holding their
     * own deck is a screen in the way of the game they came to play.
     */
    public static void offerIfEmptyHanded(ServerPlayer player, BlockPos tableOrigin) {
        if (!LoanerDecks.lends() || hasADeck(player)) {
            return;
        }
        offer(player, tableOrigin);
    }

    /** Offers the shelf, whatever they are carrying. The asked-for path. */
    public static void offer(ServerPlayer player, BlockPos tableOrigin) {
        List<String> names = LoanerDecks.names();
        if (names.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.gathering.no_loaners"));
            return;
        }
        Sending.to(player, new OpenLoanersPayload(tableOrigin, names));
    }

    /**
     * Hands over a deck.
     * <p>The name is looked up on the server's own shelf, so what a client sent is a key and
     * never a decklist - there is no way through here to conjure a card, which is what makes
     * lending safe on a server with importing switched off.
     */
    public static void handle(ServerPlayer player, TakeLoanerPayload asked) {
        if (player == null || asked == null) {
            return;
        }
        BlockPos table = asked.table();
        if (table != null && !TableReach.within(player, table)) {
            // The offer named a table and the deck goes down at one. Somewhere else is either
            // a client that has wandered off with the screen open or one that is making the
            // request up, and both get the same nothing.
            return;
        }
        DeckComponent deck = LoanerDecks.borrow(asked.name(), player.getUUID()).orElse(null);
        if (deck == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.no_such_loaner"));
            return;
        }

        ItemStack stack = DeckItem.of(deck);
        player.sendSystemMessage(Component.translatable(
                "message.gathering.loaner_taken", deck.name(), deck.deckSize()));

        // Straight down if they are sitting at a game that is waiting for a deck. Borrowing at
        // the table you are sitting at has already answered the only question there is, and
        // handing over an item to right-click the table with is a step nothing needs.
        if (table != null && putsItStraightDown(player, table)) {
            TableBlock.putDown(player.level(), table, player, stack);
            if (!stack.isEmpty()) {
                give(player, stack);
            }
            return;
        }
        give(player, stack);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        dev.gathering.server.Handing.give(player, stack);
    }

    /** Whether this borrower is seated at a live game here that has no deck down yet. */
    private static boolean putsItStraightDown(ServerPlayer player, BlockPos table) {
        ServerLevel level = player.serverLevel();
        return TableSessions.hasSession(level, table)
                && TableSeats.seatOf(level, table, player.getUUID()).isPresent();
    }

    /** Whether this player is carrying a deck already, anywhere they could reach it. */
    private static boolean hasADeck(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (DeckItem.deckOf(stack).isPresent()) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (DeckItem.deckOf(stack).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
