package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.server.TableActions;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a client is allowed to say happened.
 * <p>A client sends a move it says it made. The server's job is to refuse the ones it did not
 * - and the check that matters is not "was that legal", because the mod has no opinion about
 * legality, but "is that your name on it". Attribution is what makes the permissiveness safe:
 * any seated player may move any public card, and the log says who did.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableActionGameTest {

    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID STRANGER = new UUID(0L, 2L);
    /** Somebody seated across the table, who has gone home: in the game, and never asked to agree. */
    private static final UUID ACROSS = new UUID(0L, 3L);

    @GameTest(template = "tables")
    public static void yourOwnMoveIsAccepted(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 1, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (!accepted) {
            helper.fail("A player's own move was refused");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void aMoveSignedWithSomebodyElsesSeatIsRefused(GameTestHelper helper) {
        // The whole reason the actor is checked rather than trusted. Without this a client
        // could take a card off an opponent's board and have the log say the opponent did it.
        BlockPos origin = seatedGame(helper, 5, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(1), new SeatId(0), -40))).isPresent();

        if (accepted) {
            helper.fail("A move signed with another seat was accepted");
        }
        helper.succeed();
    }

    /**
     * The events the table writes about itself are not a client's to send.
     * <p>Signed correctly - the forged event names the player's own seat, so the attribution
     * check above is satisfied - and still refused, because a seat being taken, a deck being
     * loaded and the session ending are things the server does when a player sits, crouches
     * or concedes. Authorization allows anything it does not name, which is right for a mod
     * with no rules enforcement and left these three open: a client could end the game for
     * everybody with nothing put away, or swap its own library for any cards mid-game.
     */
    @GameTest(template = "tables")
    public static void theTablesOwnEventsAreNotAClientsToSend(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 1, 2, 1);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        boolean endedBefore = session.state().ended();

        boolean ended = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.SessionEnded(new SeatId(0), "forged"))).isPresent();
        boolean loaded = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.DeckLoaded(new SeatId(0), java.util.List.of(),
                        java.util.List.of(), dev.gathering.core.card.Sleeve.DEFAULT))).isPresent();
        boolean sat = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.SeatTaken(new SeatId(1),
                        new dev.gathering.core.game.PlayerRef(ALICE, "Alice")))).isPresent();

        if (ended || loaded || sat) {
            helper.fail("A client's forged lifecycle event was accepted: ended=" + ended
                    + " loaded=" + loaded + " sat=" + sat);
        }
        if (session.state().ended() != endedBefore) {
            helper.fail("A refused SessionEnded still ended the game");
        }
        // And the honest one beside them still goes through, so this refused the right thing.
        if (TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.Conceded(new SeatId(0)))).isEmpty()) {
            helper.fail("A player's own concession was refused along with the forgeries");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void somebodyWithNoSeatCannotMoveAtAll(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 9, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, STRANGER,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (accepted) {
            helper.fail("A player with no seat made a move");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void nonsenseIsRefusedRatherThanGuessedAt(GameTestHelper helper) {
        BlockPos origin = seatedGame(helper, 13, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                new byte[] {9, 9, 9, 9}).isPresent();

        if (accepted) {
            helper.fail("A malformed move was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
    public static void thereIsNothingToDoAtATableWithNoGame(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);

        boolean accepted = TableActions.accept(helper.getLevel(), origin, ALICE,
                encoded(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -1))).isPresent();

        if (accepted) {
            helper.fail("A move was accepted at a table with no game on it");
        }
        helper.succeed();
    }

    /**
     * Putting more cards under a library at random than one pick may take moves all of them.
     * <p>The pick is bounded for the effects that ask for a few; a whole hand sent under is
     * more than that bound, and the first twenty used to go while the rest stayed in the
     * hand with nothing said.
     */
    @GameTest(template = "tables")
    public static void everyCardSentUnderAtRandomGoesUnder(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player.getUUID());
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        java.util.List<dev.gathering.core.card.CardIdentity> deck = new java.util.ArrayList<>();
        for (int index = 0; index < 30; index++) {
            deck.add(dev.gathering.core.card.CardIdentity.ofPrinting(
                    java.util.UUID.fromString(String.format("00000000-0000-4000-8000-%012d", index))));
        }
        session.submit(new GameEvent.DeckLoaded(new SeatId(0), deck, java.util.List.of()));
        session.submit(new GameEvent.CardsDrawn(new SeatId(0), new SeatId(0), 25));
        java.util.List<dev.gathering.core.game.CardInstanceId> inHand =
                java.util.List.copyOf(session.state().contents(new SeatId(0), dev.gathering.core.game.Zone.HAND));
        if (inHand.size() != 25) {
            helper.fail("Expected twenty-five cards in hand, found " + inHand.size());
            return;
        }

        dev.gathering.server.RandomReturns.handle(player,
                new dev.gathering.network.ToBottomAtRandomPayload(origin, inHand));

        int left = session.state().contents(new SeatId(0), dev.gathering.core.game.Zone.HAND).size();
        if (left != 0) {
            helper.fail("Twenty-five cards were sent under the library and " + left + " stayed in the hand");
            return;
        }
        helper.succeed();
    }

    /**
     * A library emptied into its owner's hand is not taken back by that player alone.
     * <p>Holding your own library and letting go over your hand sends exactly this move, and the
     * hand is open to its owner - so it showed them their whole library in order, and one press of
     * Undo put the library back in the order they had just read. Through the real move and undo
     * handlers.
     * <p>With somebody else seated across the table. Written with one player at it, as it was, it now
     * checks nothing: since the owner's rule of 2026-10-27 a player with nobody else seated is everyone,
     * and takes back what everyone agreeing takes back - see {@code UndoAloneGameTest}.
     */
    @GameTest(template = "tables")
    public static void aLibraryReadIntoTheHandIsNotTakenBackAlone(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player.getUUID());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.SOUTH, ACROSS);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        SeatId me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
        java.util.List<dev.gathering.core.card.CardIdentity> deck = new java.util.ArrayList<>();
        for (int index = 0; index < 30; index++) {
            deck.add(dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(0x11b4L, index)));
        }
        session.submit(new GameEvent.DeckLoaded(me, deck, java.util.List.of()));
        java.util.List<dev.gathering.core.game.CardInstanceId> library =
                session.state().contents(me, dev.gathering.core.game.Zone.LIBRARY);

        TableActions.handle(player, new dev.gathering.network.TableActionPayload(origin, encoded(
                new GameEvent.ZoneMoved(me, me, dev.gathering.core.game.Zone.LIBRARY,
                        dev.gathering.core.game.ZoneRef.of(me, dev.gathering.core.game.Zone.HAND),
                        dev.gathering.core.game.Placement.BOTTOM))));
        if (!session.state().contents(me, dev.gathering.core.game.Zone.HAND).equals(library)) {
            helper.fail("the library did not go into the hand in order, so this checks nothing");
            return;
        }

        TableActions.handleUndo(player, new dev.gathering.network.UndoPayload(origin, 1));

        if (!session.state().contents(me, dev.gathering.core.game.Zone.LIBRARY).isEmpty()) {
            helper.fail("one press of Undo put back, in the same order, a library its owner had just read");
            return;
        }
        helper.succeed();
    }

    /**
     * A table's answer to a press reaches the board as well as the chat.
     * <p>Both boards cover the chat window: the flat one is felt from edge to edge, and the one on
     * the block hides the game's own interface. So an Undo across a draw - refused, because a draw
     * shows a card - and a token name that found nothing were answered in a place nobody at the
     * table could see, and read as presses that did nothing. A stand-in player cannot take the
     * notice's payload, so this listens one step before the wire, at {@code Notices}.
     * <p>With somebody else seated, so the Undo across the draw is refused: a player alone is
     * everyone since the owner's rule of 2026-10-27, and takes it back.
     */
    @GameTest(template = "tables")
    public static void aTablesAnswerToAPressIsShownOnTheBoard(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getCenter());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, player.getUUID());
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.SOUTH, ACROSS);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        SeatId me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
        java.util.List<dev.gathering.core.card.CardIdentity> deck = new java.util.ArrayList<>();
        for (int index = 0; index < 30; index++) {
            deck.add(dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(0x2a5L, index)));
        }
        session.submit(new GameEvent.DeckLoaded(me, deck, java.util.List.of()));

        java.util.List<net.minecraft.network.chat.Component> shown =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        UUID who = player.getUUID();
        dev.gathering.server.Notices.watchForTesting((to, line) -> {
            if (who.equals(to)) {
                shown.add(line);
            }
        });

        // A draw, and then Undo: the draw showed a card, so one player may not take it back without
        // the other's agreement, which the table's Undo does not collect.
        TableActions.handle(player, new dev.gathering.network.TableActionPayload(origin,
                encoded(new GameEvent.CardsDrawn(me, me, 1))));
        if (session.state().contents(me, dev.gathering.core.game.Zone.HAND).size() != 1) {
            helper.fail("the draw did not happen, so this checks nothing");
            return;
        }
        TableActions.handleUndo(player, new dev.gathering.network.UndoPayload(origin, 1));
        if (session.state().contents(me, dev.gathering.core.game.Zone.HAND).size() != 1) {
            helper.fail("an Undo across a draw was taken by one player alone, so this checks nothing");
            return;
        }
        if (shown.isEmpty()) {
            helper.fail("a refused Undo was answered only in chat, which the board covers: the press did"
                    + " nothing anybody at the table could see");
            return;
        }

        // A token name that finds nothing.
        shown.clear();
        dev.gathering.server.TokenCreation.put(player, helper.getLevel(), origin, me, java.util.List.of(), 1,
                "Goblin Tokn", "message.gathering.token_created", "message.gathering.token_not_found");
        boolean told = shown.stream().anyMatch(line -> line.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents said
                && said.getKey().equals("message.gathering.token_not_found"));
        if (!told) {
            helper.fail("a token name that found nothing was answered only in chat: " + shown);
            return;
        }
        helper.succeed();
    }

    /** A table with a game on it and Alice in seat zero. */
    private static BlockPos seatedGame(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = place(helper, x, y, z);
        TableSeats.take(helper.getLevel(), origin, new TableCell(0, 0), Side.NORTH, ALICE);
        TableSessions.start(helper.getLevel(), origin, TableSessions.defaultRules());
        return origin;
    }

    private static byte[] encoded(GameEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, event);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new GameTestAssertException("Could not encode a test move: " + e);
        }
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        return TestTables.place(helper, x, y, z);
    }
}
