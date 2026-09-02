package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.TakeLoanerPayload;
import dev.gathering.server.Lending;
import dev.gathering.server.LoanerDecks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Borrowing a deck, in a running world.
 * <p>What the shelf is - which files become which names - is a pure rule with its own tests.
 * What cannot be checked there is the part that matters to a new player: sit down at a table
 * with nothing, pick a deck, and be playing. That is three separate pieces of machinery
 * agreeing (a seat, a shelf, a session), and each of them can be right on its own while the
 * player still ends up holding an item and wondering what to do with it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LoanerGameTest {

    private static final UUID FOREST = UUID.fromString("cccccccc-3333-4333-8333-333333333333");

    private static final String NAME = "House Deck";

    /** Borrowing at a table you are sitting at puts the deck down, not in your pockets. */
    @GameTest(template = "empty")
    public static void aBorrowedDeckGoesStraightDown(GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        BlockPos origin = table(helper);
        ServerPlayer player = seated(helper, origin);
        TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER));

        Lending.handle(player, new TakeLoanerPayload(origin, NAME));

        int inLibrary = libraryOf(helper, origin, player);
        if (inLibrary != 60) {
            helper.fail("Borrowing at the table left " + inLibrary
                    + " cards in the library; sixty were expected");
            return;
        }
        if (decksCarried(player) != 0) {
            helper.fail("The borrowed deck went into the player's pockets as well as onto"
                    + " the table");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    /**
     * A deck the shelf does not have is nothing at all.
     * <p>The name crosses the wire, so this is the request a client makes up. It must not
     * conjure a deck, and it must not throw either - a thrown handler is a disconnect.
     */
    @GameTest(template = "empty")
    public static void aDeckTheShelfDoesNotHaveIsNotLent(GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        BlockPos origin = table(helper);
        ServerPlayer player = seated(helper, origin);
        Lending.handle(player, new TakeLoanerPayload(origin, "Something Else"));

        if (decksCarried(player) != 0) {
            helper.fail("A deck that is not on the shelf was lent anyway");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    /**
     * Borrowing from across the world does not work.
     * <p>The offer names a table and the deck goes down at one. Without this, a client with
     * the screen open could walk away and keep taking decks out of a table it is nowhere near.
     */
    @GameTest(template = "empty")
    public static void aBorrowerHasToBeAtTheTable(GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        BlockPos origin = table(helper);
        ServerPlayer player = seated(helper, origin);
        player.setPos(player.getX() + 200.0, player.getY(), player.getZ());

        Lending.handle(player, new TakeLoanerPayload(origin, NAME));

        if (decksCarried(player) != 0) {
            helper.fail("A deck was lent to somebody two hundred blocks from the table");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    /**
     * With no session yet, the deck is handed over rather than dropped on the floor.
     * <p>Sitting down before anybody has started a game is the ordinary case, and a loaner
     * that vanished because there was nowhere to put it would be the worst possible first
     * minute.
     */
    @GameTest(template = "empty")
    public static void withNoGameRunningTheDeckGoesIntoYourHands(GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        BlockPos origin = table(helper);
        ServerPlayer player = seated(helper, origin);
        Lending.handle(player, new TakeLoanerPayload(origin, NAME));

        if (decksCarried(player) != 1) {
            helper.fail("Borrowing with no game running left the player with "
                    + decksCarried(player) + " decks; one was expected");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    /** Every borrower gets their own copy, with their own name on it. */
    @GameTest(template = "empty")
    public static void aLoanerBelongsToWhoeverBorrowedIt(GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        UUID borrower = UUID.fromString("dddddddd-4444-4444-8444-444444444444");
        DeckComponent lent = LoanerDecks.borrow(NAME, borrower).orElse(null);
        if (lent == null || lent.owner().isEmpty() || !lent.owner().get().equals(borrower)) {
            helper.fail("A borrowed deck did not belong to the borrower");
            return;
        }
        // And the shelf copy was not the one handed over: lending twice lends twice.
        UUID other = UUID.fromString("eeeeeeee-5555-4555-8555-555555555555");
        DeckComponent second = LoanerDecks.borrow(NAME, other).orElseThrow();
        if (second.owner().orElseThrow().equals(borrower)) {
            helper.fail("The second borrower was handed the first borrower's deck");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    /** Nothing on the shelf means nothing is offered, and nothing goes wrong. */
    @GameTest(template = "empty")
    public static void anEmptyShelfLendsNothing(GameTestHelper helper) {
        LoanerDecks.clear();
        if (LoanerDecks.lends() || !LoanerDecks.names().isEmpty()) {
            helper.fail("An empty shelf said it had decks on it");
            return;
        }
        BlockPos origin = table(helper);
        ServerPlayer player = seated(helper, origin);
        Lending.offerIfEmptyHanded(player, origin);
        Lending.handle(player, new TakeLoanerPayload(origin, NAME));
        if (decksCarried(player) != 0) {
            helper.fail("A deck came off an empty shelf");
            return;
        }
        helper.succeed();
    }

    /**
     * Reading the folder again never empties the shelf while it is doing it.
     * <p>Resolving a decklist is a network call, so a reload that cleared first and resolved
     * afterwards would leave a window seconds wide where the server lends nothing - and
     * somebody who sat down inside it would be told this server has no decks. The folder here
     * has nothing readable in it, which is the worst case for the naive version: clear, find
     * nothing, and have thrown away a working shelf for good.
     */
    @GameTest(template = "empty")
    public static void readingTheFolderAgainDoesNotEmptyTheShelfWhileItRuns(
            GameTestHelper helper) {
        LoanerDecks.clear();
        LoanerDecks.stock(NAME, sixtyForests());

        LoanerDecks.reload();
        // Checked on the very next instruction, which is inside any window a clear-first
        // version would have opened.
        if (!LoanerDecks.lends()) {
            helper.fail("re-reading the folder emptied the shelf while it was doing it");
            return;
        }
        LoanerDecks.clear();
        helper.succeed();
    }

    // ------------------------------------------------------------------ bits

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(origin,
                GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static ServerPlayer seated(GameTestHelper helper, BlockPos origin) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH,
                player.getUUID());
        return player;
    }

    private static int libraryOf(GameTestHelper helper, BlockPos origin, ServerPlayer player) {
        var session = TableSessions.sessionAt(helper.getLevel(), origin).orElse(null);
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID())
                .orElse(null);
        if (session == null || seat == null) {
            return -1;
        }
        GameView view = VisibilityRules.viewFor(session.state(), new Viewer.Seated(seat));
        return view.seat(seat).zone(Zone.LIBRARY).count();
    }

    private static int decksCarried(ServerPlayer player) {
        int carried = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (DeckItem.deckOf(stack).isPresent()) {
                carried += stack.getCount();
            }
        }
        return carried;
    }

    private static DeckComponent sixtyForests() {
        List<CardComponent> cards = new ArrayList<>();
        CardComponent forest = CardComponent.of(CardIdentity.ofPrinting(FOREST, false));
        for (int copy = 0; copy < 60; copy++) {
            cards.add(forest);
        }
        return new DeckComponent(NAME, "The server's own.", Optional.empty(),
                cards, List.of(), List.of());
    }
}
