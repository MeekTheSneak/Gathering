package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.UndoMode;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.EventCodec;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.TableActionPayload;
import dev.gathering.network.UndoPayload;
import dev.gathering.server.AwayFromBoard;
import dev.gathering.server.TableActions;
import dev.gathering.server.TableJoining;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's rule for a table with one person at it (working record, decisions, item 7, 2026-10-27): the person
 * asking is everyone, so their asking is the agreement a rewind across a shuffle or a draw needs. Before it a player
 * alone could take back neither - the table's Undo collects nobody's agreement, and nobody else at a table of one
 * could give it. Somebody else seated still has to agree, and a seat kept for a player away from the board is still
 * theirs. Through real chairs, the real move handler and the real undo handler.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UndoAloneGameTest {

    private UndoAloneGameTest() {
    }

    /**
     * A player alone at the table takes back their own shuffle, and then their own draw, in every undo mode that takes
     * anything back - with somebody watching from a chair beside the table, who is never asked. With undo off, still
     * nothing.
     */
    @GameTest(template = "tables")
    public static void aPlayerAloneTakesBackTheirOwnShuffleAndDraw(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer alone = sit(helper, table, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        start(helper, table);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId me = deckFor(helper, table, alone, 0x10e1L);
        // Off the middle of an edge, which watches: in the chair, and with no seat in the game.
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        BlockPos side = chairAt(helper, table.offset(0, 0, -1), Direction.SOUTH);
        Chairs.sit(watching, side, helper.getLevel().getBlockState(side));
        if (!TableJoining.watchers(helper.getLevel(), table).contains(watching)) {
            helper.fail("fixture: the second player is not watching from the chair beside the table");
            return;
        }

        for (UndoMode mode : List.of(UndoMode.FREE, UndoMode.UNANIMOUS)) {
            session.setUndoMode(mode);
            // A draw first, so the shuffle is the player's own and not the one a deck goes down with, which is the
            // table's setup and is never rewound.
            move(alone, table, new GameEvent.CardsDrawn(me, me, 1));
            List<CardInstanceId> beforeTheShuffle = session.state().contents(me, Zone.LIBRARY);
            move(alone, table, new GameEvent.LibraryShuffled(me, me));
            if (session.state().contents(me, Zone.HAND).size() != 1
                    || session.state().contents(me, Zone.LIBRARY).equals(beforeTheShuffle)) {
                helper.fail("the draw and the shuffle did not both happen in " + mode + ", so this checks nothing");
                return;
            }

            TableActions.handleUndo(alone, new UndoPayload(table, 1));
            if (!session.state().contents(me, Zone.LIBRARY).equals(beforeTheShuffle)) {
                helper.fail("a player alone at the table could not take back their own shuffle in " + mode
                        + ": nobody else is seated to disagree, and their asking is everyone agreeing");
                return;
            }
            TableActions.handleUndo(alone, new UndoPayload(table, 1));
            if (!session.state().contents(me, Zone.HAND).isEmpty()) {
                helper.fail("a player alone at the table could not take back their own draw in " + mode);
                return;
            }
        }

        session.setUndoMode(UndoMode.OFF);
        move(alone, table, new GameEvent.CardsDrawn(me, me, 1));
        TableActions.handleUndo(alone, new UndoPayload(table, 1));
        if (session.state().contents(me, Zone.HAND).size() != 1) {
            helper.fail("with undo off, a player alone at the table took back their draw");
            return;
        }
        helper.succeed();
    }

    /** With somebody else seated, the same shuffle and the same draw still need them to agree. */
    @GameTest(template = "tables")
    public static void withASecondPlayerSeatedTheyStillHaveToAgree(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer asking = sit(helper, table, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        ServerPlayer other = sit(helper, table, chairAt(helper, table.offset(1, 0, 3), Direction.NORTH));
        start(helper, table);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId me = deckFor(helper, table, asking, 0x10e2L);
        deckFor(helper, table, other, 0x10e3L);

        move(asking, table, new GameEvent.CardsDrawn(me, me, 1));
        move(asking, table, new GameEvent.LibraryShuffled(me, me));
        List<CardInstanceId> shuffled = session.state().contents(me, Zone.LIBRARY);
        TableActions.handleUndo(asking, new UndoPayload(table, 1));
        if (!session.state().contents(me, Zone.LIBRARY).equals(shuffled)) {
            helper.fail("with another player seated, one player alone took back their shuffle");
            return;
        }

        move(asking, table, new GameEvent.CardsDrawn(me, me, 1));
        TableActions.handleUndo(asking, new UndoPayload(table, 1));
        if (session.state().contents(me, Zone.HAND).size() != 2) {
            helper.fail("with another player seated, one player alone took back their draw: "
                    + session.state().contents(me, Zone.HAND).size() + " cards in hand, not 2");
            return;
        }
        helper.succeed();
    }

    /**
     * A seat kept for a player away from the board is still theirs, after the game has let go of them for leaving the
     * server: the person asking is not alone, and their shuffle still needs that player's agreement.
     */
    @GameTest(template = "tables")
    public static void aSeatKeptForAPlayerAwayIsStillTheirs(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer asking = sit(helper, table, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        ServerPlayer away = sit(helper, table, chairAt(helper, table.offset(1, 0, 3), Direction.NORTH));
        start(helper, table);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId me = deckFor(helper, table, asking, 0x10e4L);
        SeatId theirs = deckFor(helper, table, away, 0x10e5L);
        try {
            // What the server does to a player who loses their connection mid-game: marked gone, then out of the
            // chair - which keeps their seat for eight minutes. And what the game does once it finds them gone, as
            // the reconcile does when they are no longer on the server: it lets go of them in the seat.
            away.disconnect();
            away.stopRiding();
            if (!AwayFromBoard.isAway(helper.getLevel(), table, away.getUUID())) {
                helper.fail("fixture: the player who left mid-game has no seat kept for them");
                return;
            }
            session.submit(new GameEvent.SeatReleased(theirs));
            if (session.state().seatState(theirs).isOccupied()) {
                helper.fail("fixture: the game still has the player who left in their seat");
                return;
            }

            move(asking, table, new GameEvent.CardsDrawn(me, me, 1));
            move(asking, table, new GameEvent.LibraryShuffled(me, me));
            List<CardInstanceId> shuffled = session.state().contents(me, Zone.LIBRARY);
            TableActions.handleUndo(asking, new UndoPayload(table, 1));
            if (!session.state().contents(me, Zone.LIBRARY).equals(shuffled)) {
                helper.fail("with a seat kept for a player away from the board, the one player left took back their"
                        + " shuffle as though they were alone at the table");
                return;
            }
            helper.succeed();
        } finally {
            AwayFromBoard.forget(helper.getLevel(), table, away.getUUID());
        }
    }

    /**
     * A player who left the server with no cards down keeps their chair - nothing is kept on a clock for them, since
     * they have nothing on the board - and it is still their seat once the game has let go of them: the one player
     * left is not alone, and their shuffle still needs that player's agreement.
     */
    @GameTest(template = "tables")
    public static void aPlayerWhoLeftWithNoCardsDownStillHoldsTheirSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer asking = sit(helper, table, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        ServerPlayer gone = sit(helper, table, chairAt(helper, table.offset(1, 0, 3), Direction.NORTH));
        start(helper, table);
        GameSession session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId me = deckFor(helper, table, asking, 0x10e6L);
        SeatId theirs = TableSessions.seatIdOf(helper.getLevel(), table, gone.getUUID()).orElseThrow();

        gone.disconnect();
        gone.stopRiding();
        if (TableSeats.seatOf(helper.getLevel(), table, gone.getUUID()).isEmpty()
                || AwayFromBoard.isAway(helper.getLevel(), table, gone.getUUID())) {
            helper.fail("fixture: the player who left with no cards down should keep their chair, with no clock on it");
            return;
        }
        session.submit(new GameEvent.SeatReleased(theirs));

        move(asking, table, new GameEvent.CardsDrawn(me, me, 1));
        move(asking, table, new GameEvent.LibraryShuffled(me, me));
        List<CardInstanceId> shuffled = session.state().contents(me, Zone.LIBRARY);
        TableActions.handleUndo(asking, new UndoPayload(table, 1));
        if (!session.state().contents(me, Zone.LIBRARY).equals(shuffled)) {
            helper.fail("with a chair still held by a player who left the server, the one player left took back their"
                    + " shuffle as though they were alone at the table");
            return;
        }
        helper.succeed();
    }

    /**
     * A chair at another player's board held by somebody who does not play it is no seat in the game: the board is the
     * first player's, so whoever holds the chair watches it, and the one player with a seat is alone. Sitting down
     * there is refused, so the chair is handed over the way a claim kept on the table outlives the seating it was made
     * in - a chair that changed hands while the server was down - and the game is reconciled with it as it is when the
     * seating next changes.
     */
    @GameTest(template = "tables")
    public static void aChairAtAnotherPlayersBoardHoldsNoSeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer alone = sit(helper, table, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        ServerPlayer gone = sit(helper, table, chairAt(helper, table.offset(1, 0, 3), Direction.NORTH));
        start(helper, table);
        GameSession session = TableSessions.sessionAt(level, table).orElseThrow();
        SeatId me = deckFor(helper, table, alone, 0x10e7L);
        SeatId theirs = deckFor(helper, table, gone, 0x10e8L);
        SeatAnchor chair = TableSeats.seatOf(level, table, gone.getUUID()).orElseThrow();

        // Out of the chair with their cards down and the seat not given up, as a chair knocked away leaves it: the
        // board waits for them. Then the chair is somebody else's.
        TableSeats.leave(level, table, gone.getUUID());
        ServerPlayer holding = helper.makeMockServerPlayerInLevel();
        TableBlock.entityAt(level, TableClusters.blockPos(table, chair.cell())).orElseThrow()
                .claim(chair.side(), holding.getUUID());
        TableSessions.seatingChanged(level, table);
        if (!TableSeats.holderOf(level, table, chair).map(holding.getUUID()::equals).orElse(false)
                || TableSessions.seatIdOf(level, table, holding.getUUID()).isPresent()
                || session.state().seatState(theirs).isOccupied()) {
            helper.fail("fixture: the chair at the other player's board should be held by somebody with no seat in the"
                    + " game, and the game should have nobody in that seat");
            return;
        }

        move(alone, table, new GameEvent.CardsDrawn(me, me, 1));
        List<CardInstanceId> beforeTheShuffle = session.state().contents(me, Zone.LIBRARY);
        move(alone, table, new GameEvent.LibraryShuffled(me, me));
        TableActions.handleUndo(alone, new UndoPayload(table, 1));
        if (!session.state().contents(me, Zone.LIBRARY).equals(beforeTheShuffle)) {
            helper.fail("with the only other chair held by somebody watching another player's board, the one player"
                    + " with a seat could not take back their own shuffle: somebody watching is never asked");
            return;
        }
        helper.succeed();
    }

    /** Puts a deck of thirty different printings down at this player's seat, and says which seat it is. */
    private static SeatId deckFor(GameTestHelper helper, BlockPos table, ServerPlayer player, long kind) {
        GameSession session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), table, player.getUUID())
                .orElseThrow(() -> new GameTestAssertException("fixture: the player has no seat in the game"));
        List<CardIdentity> deck = new ArrayList<>();
        for (int line = 0; line < 30; line++) {
            deck.add(CardIdentity.ofPrinting(new UUID(kind, line)));
        }
        session.submit(new GameEvent.DeckLoaded(seat, deck, List.of()));
        return seat;
    }

    /** A move, sent the way the player's client sends it. */
    private static void move(ServerPlayer player, BlockPos table, GameEvent event) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                EventCodec.write(out, event);
            }
            TableActions.handle(player, new TableActionPayload(table, bytes.toByteArray()));
        } catch (IOException e) {
            throw new GameTestAssertException("fixture: the move would not encode: " + e);
        }
    }

    private static void start(GameTestHelper helper, BlockPos table) {
        if (TableSessions.start(helper.getLevel(), table, TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            throw new GameTestAssertException("fixture: the game did not start");
        }
    }

    private static ServerPlayer sit(GameTestHelper helper, BlockPos table, BlockPos chair) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(chair.getCenter());
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isEmpty()) {
            throw new GameTestAssertException("fixture: sitting in the chair took no seat at the table");
        }
        return player;
    }

    private static BlockPos chairAt(GameTestHelper helper, BlockPos where, Direction facing) {
        helper.getLevel().setBlock(where,
                GatheringContent.CHAIR.get().defaultBlockState().setValue(ChairBlock.FACING, facing), 3);
        return where;
    }
}
