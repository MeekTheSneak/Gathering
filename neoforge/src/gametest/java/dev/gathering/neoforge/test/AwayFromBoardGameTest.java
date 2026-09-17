package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.ChairBlock;
import dev.gathering.block.ChairSeat;
import dev.gathering.block.Chairs;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.AwayVotePayload;
import dev.gathering.network.JoinTableAnswerPayload;
import dev.gathering.server.AwayFromBoard;
import dev.gathering.server.TableJoining;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Away from the board, the owner's rule: getting up in the middle of a game without conceding keeps the seat
 * for eight minutes and nobody else sits there; sitting back down carries on; the time running out, or every
 * other player at a game of four or more voting, frees it, and the next player may take it board and all.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AwayFromBoardGameTest {

    private AwayFromBoardGameTest() {
    }

    /** Getting up mid-game keeps the seat; somebody else is refused it; sitting back down carries on. */
    @GameTest(template = "tables")
    public static void gettingUpMidGameKeepsTheSeat(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        start(helper, table);
        cardsFor(helper, table, owner);
        try {
            owner.stopRiding();
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isEmpty()
                    || !AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID())) {
                helper.fail("getting up in the middle of a game gave the seat up instead of keeping it");
                return;
            }
            ServerPlayer waiting = player(helper);
            Chairs.sit(waiting, north, helper.getLevel().getBlockState(north));
            if (waiting.isPassenger() || TableSeats.seatOf(helper.getLevel(), table, waiting.getUUID()).isPresent()) {
                helper.fail("somebody else sat down at a seat kept for a player away from the board");
                return;
            }
            Chairs.sit(owner, north, helper.getLevel().getBlockState(north));
            if (!(owner.getVehicle() instanceof ChairSeat) || AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID())) {
                helper.fail("sitting back down at a kept seat did not carry on: away " +
                        AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID()));
                return;
            }
            // Getting up with nothing on the board, or with no game, is only getting up.
            owner.stopRiding();
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    /**
     * Leaving the server in the middle of a game is getting up: the seat is kept for eight minutes, not for ever.
     * A player gone from the server used to keep it, cards and all, with nothing anybody could do.
     */
    @GameTest(template = "tables")
    public static void leavingTheServerMidGameKeepsTheSeatForEightMinutes(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        start(helper, table);
        cardsFor(helper, table, owner);
        long started = 4_000_000L;
        AwayFromBoard.clock = server -> started;
        try {
            // What the server does to a player who loses their connection: marked gone, then out of the chair.
            owner.disconnect();
            owner.stopRiding();
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isEmpty()
                    || !AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID())) {
                helper.fail("a player who left the server mid-game has no clock running on their seat");
                return;
            }
            AwayFromBoard.clock = server -> started + AwayFromBoard.MINUTES * 60 * 20L;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isPresent()) {
                helper.fail("the seat of a player who left the server was still held after eight minutes");
                return;
            }
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    /**
     * A restart keeps a seat for what was left of its time, with its votes, and remembers a seat given up. Saved,
     * forgotten from memory, and read back the way a server starting again reads it.
     */
    @GameTest(template = "tables")
    public static void aKeptSeatSurvivesARestart(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        start(helper, table);
        SeatId seat = cardsFor(helper, table, owner);
        long started = 5_000_000L;
        AwayFromBoard.clock = server -> started;
        try {
            owner.stopRiding();
            // Five minutes gone, then the server goes down; it comes back with its tick count started again.
            AwayFromBoard.clock = server -> started + 5 * 60 * 20L;
            AwayFromBoard.saveForTesting(helper.getLevel().getServer());
            AwayFromBoard.forgetForTesting();
            long restarted = 100L;
            AwayFromBoard.clock = server -> restarted;
            if (!AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID())) {
                helper.fail("a seat kept for a player away from the board was forgotten by a restart");
                return;
            }
            AwayFromBoard.clock = server -> restarted + 3 * 60 * 20L - 20;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isEmpty()) {
                helper.fail("after a restart the seat was freed before the three minutes it had left");
                return;
            }
            AwayFromBoard.clock = server -> restarted + 3 * 60 * 20L;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isPresent()) {
                helper.fail("after a restart the seat was still held once its time was up");
                return;
            }
            AwayFromBoard.saveForTesting(helper.getLevel().getServer());
            AwayFromBoard.forgetForTesting();
            if (!AwayFromBoard.wasGivenUp(helper.getLevel(), table, seat.index(), owner.getUUID())) {
                helper.fail("a restart forgot that a seat was given up, so its board would wait for ever");
                return;
            }
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    /**
     * A seat given up belongs to the game it was given up in, and not to the next one.
     * <p>These records outlived their games and were saved to disk. A give-up says "somebody may take
     * this board", and what that permits is another player being seated at it and sent the hand that
     * goes with it - so a seat given up on Monday quietly said yes again on Friday, at the same table,
     * in a game nobody in it had left. The visibility invariant, reached through a record whose
     * lifetime nobody had defined.
     */
    @GameTest(template = "tables")
    public static void agiveUpDoesNotOutliveItsGame(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        int seat = 0;

        TableSessions.start(helper.getLevel(), table,
                new dev.gathering.core.match.MatchRules(
                        dev.gathering.core.format.FormatPresets.MODERN, 1));
        AwayFromBoard.leftTheSeat(helper.getLevel(), table, owner.getUUID(), seat);
        if (!AwayFromBoard.wasGivenUp(helper.getLevel(), table, seat, owner.getUUID())) {
            helper.fail("leaving a seat did not record that it was given up");
            return;
        }

        // The game ends and another begins at the same table. Monday is over.
        TableSessions.end(helper.getLevel(), table, new SeatId(0), "test");
        TableSessions.start(helper.getLevel(), table,
                new dev.gathering.core.match.MatchRules(
                        dev.gathering.core.format.FormatPresets.MODERN, 1));

        if (AwayFromBoard.wasGivenUp(helper.getLevel(), table, seat, owner.getUUID())) {
            helper.fail("a seat given up in one game still said so in the next, "
                    + "which seats a stranger at somebody else's board");
            return;
        }
        helper.succeed();
    }

    /**
     * Too many give-ups forgets the oldest, not all of them.
     * <p>Emptying the set was the answer, and because nothing retired a record the cap was reached by
     * ordinary play - at which point every live seat whose player had walked away became untakeable
     * for the rest of its game, with nothing said to anybody.
     */
    @GameTest(template = "tables")
    public static void toomanyGiveUpsForgetTheOldestOnly(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        java.util.UUID recent = java.util.UUID.randomUUID();

        // The ceiling reached by old records, and then the one that matters, and then more after
        // it. The oldest going is right; this one is not the oldest and its game is still on.
        for (int filler = 0; filler < 1100; filler++) {
            AwayFromBoard.leftTheSeat(helper.getLevel(), table.offset(0, filler + 4, 0),
                    java.util.UUID.randomUUID(), 0);
        }
        AwayFromBoard.leftTheSeat(helper.getLevel(), table, recent, 1);
        for (int filler = 0; filler < 50; filler++) {
            AwayFromBoard.leftTheSeat(helper.getLevel(), table.offset(0, filler + 1200, 0),
                    java.util.UUID.randomUUID(), 0);
        }

        if (!AwayFromBoard.wasGivenUp(helper.getLevel(), table, 1, recent)) {
            helper.fail("filling the set up threw away a live seat's give-up along with the old ones");
            return;
        }
        helper.succeed();
    }

    /** Eight minutes on, the seat is free, and the next player to sit down there may take it with its board. */
    @GameTest(template = "tables")
    public static void aKeptSeatIsFreedWhenTheTimeRunsOut(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        start(helper, table);
        SeatId seat = cardsFor(helper, table, owner);
        long started = 2_000_000L;
        AwayFromBoard.clock = server -> started;
        try {
            owner.stopRiding();
            AwayFromBoard.clock = server -> started + (AwayFromBoard.MINUTES * 60 - 20) * 20L;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isEmpty()) {
                helper.fail("a kept seat was given up before its eight minutes were over");
                return;
            }
            AwayFromBoard.clock = server -> started + AwayFromBoard.MINUTES * 60 * 20L;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isPresent()
                    || AwayFromBoard.isAway(helper.getLevel(), table, owner.getUUID())) {
                helper.fail("a kept seat was still held after its eight minutes");
                return;
            }
            ServerPlayer next = player(helper);
            Chairs.sit(next, north, helper.getLevel().getBlockState(north));
            TableJoining.answer(next, new JoinTableAnswerPayload(table, true));
            if (!TableSessions.seatIdOf(helper.getLevel(), table, next.getUUID()).equals(java.util.Optional.of(seat))) {
                helper.fail("the next player could not take a seat given up when its time ran out");
                return;
            }
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    /**
     * Whoever takes over a seat freed while its player was away plays that player's deck, and when the game is
     * over the deck goes back to its owner - not to the player who took the seat, and not into the next game.
     */
    @GameTest(template = "tables")
    public static void aTakenOverSeatsDeckGoesBackToItsOwnerAfterTheGame(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        BlockPos north = chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH);
        ServerPlayer owner = sit(helper, north);
        owner.getInventory().clearContent();
        if (TableSessions.start(helper.getLevel(), table, new dev.gathering.core.match.MatchRules(
                dev.gathering.core.format.FormatPresets.defaultPreset(), 3)) != TableSessions.Outcome.STARTED) {
            helper.fail("fixture: the game did not start");
            return;
        }
        SeatId seat = cardsFor(helper, table, owner);
        dev.gathering.block.TableBlockEntity entity = dev.gathering.block.TableBlock.entityAt(helper.getLevel(), table).orElseThrow();
        entity.holdDeck(seat, new dev.gathering.item.DeckComponent("Owner's deck", "", java.util.Optional.empty(),
                List.of(dev.gathering.item.CardComponent.of(CardIdentity.ofPrinting(new UUID(7L, 0L), false))),
                List.of(), List.of()), null, owner.getUUID());
        long started = 3_000_000L;
        AwayFromBoard.clock = server -> started;
        try {
            owner.stopRiding();
            AwayFromBoard.clock = server -> started + AwayFromBoard.MINUTES * 60 * 20L;
            AwayFromBoard.tick(helper.getLevel().getServer());
            if (TableSeats.seatOf(helper.getLevel(), table, owner.getUUID()).isPresent()) {
                helper.fail("fixture: the kept seat was not freed");
                return;
            }
            if (!entity.heldDecks().containsKey(seat) || decksIn(owner) != 0) {
                helper.fail("a seat freed while its player was away handed the deck back before the game was over");
                return;
            }
            ServerPlayer next = sit(helper, north);
            next.getInventory().clearContent();
            TableJoining.answer(next, new JoinTableAnswerPayload(table, true));
            if (!TableSessions.seatIdOf(helper.getLevel(), table, next.getUUID()).equals(java.util.Optional.of(seat))) {
                helper.fail("fixture: the next player did not take the freed seat");
                return;
            }
            var session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
            session.submit(new GameEvent.Conceded(seat));
            dev.gathering.server.TableMatch.settleIfFinished(helper.getLevel(), table, session.state());
            if (TableSessions.hasSession(helper.getLevel(), table)) {
                helper.fail("fixture: conceding the only board in play did not end the game");
                return;
            }
            if (decksIn(owner) != 1 || decksIn(next) != 0 || entity.heldDecks().containsKey(seat)) {
                helper.fail("after the game the owner has " + decksIn(owner) + " deck(s), the player who took the seat "
                        + decksIn(next) + ", and the table still holds it: " + entity.heldDecks().containsKey(seat));
                return;
            }
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    private static int decksIn(ServerPlayer player) {
        int decks = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (dev.gathering.item.DeckItem.deckOf(player.getInventory().getItem(slot)).isPresent()) {
                decks++;
            }
        }
        return decks;
    }

    /**
     * At a game of four, every other player voting frees a kept seat - and not before the last of them votes. At a
     * game of two, a vote frees nothing.
     */
    @GameTest(template = "tables")
    public static void theOthersAtAGameOfFourVoteASeatFree(GameTestHelper helper) {
        BlockPos first = TestTables.place(helper, 1, 2, 2);
        TestTables.place(helper, 4, 2, 2);
        BlockPos table = TableSessions.anchorOf(helper.getLevel(), first).orElse(first);
        List<BlockPos> chairs = List.of(
                chairAt(helper, first.offset(1, 0, -1), Direction.SOUTH),
                chairAt(helper, first.offset(4, 0, -1), Direction.SOUTH),
                chairAt(helper, first.offset(1, 0, 3), Direction.NORTH),
                chairAt(helper, first.offset(4, 0, 3), Direction.NORTH));
        List<ServerPlayer> players = chairs.stream().map(chair -> sit(helper, chair)).toList();
        for (ServerPlayer player : players) {
            if (TableSeats.seatOf(helper.getLevel(), table, player.getUUID()).isEmpty()) {
                helper.fail("fixture: four chairs along a line of two tables did not seat four players");
                return;
            }
        }
        start(helper, table);
        ServerPlayer away = players.get(0);
        SeatId seat = cardsFor(helper, table, away);
        try {
            away.stopRiding();
            if (!AwayFromBoard.isAway(helper.getLevel(), table, away.getUUID())) {
                helper.fail("fixture: the player who got up is not away");
                return;
            }
            for (int voter = 1; voter < players.size(); voter++) {
                AwayFromBoard.vote(players.get(voter), new AwayVotePayload(table, seat.index()));
                boolean last = voter == players.size() - 1;
                boolean held = TableSeats.seatOf(helper.getLevel(), table, away.getUUID()).isPresent();
                if (!last && !held) {
                    helper.fail("a kept seat was freed after " + voter + " of 3 votes");
                    return;
                }
                if (last && held) {
                    helper.fail("every other player voted and the kept seat is still held");
                    return;
                }
            }
            helper.succeed();
        } finally {
            AwayFromBoard.clock = net.minecraft.server.MinecraftServer::getTickCount;
        }
    }

    @GameTest(template = "tables")
    public static void aVoteAtAGameOfTwoFreesNothing(GameTestHelper helper) {
        BlockPos table = TestTables.place(helper, 1, 2, 2);
        ServerPlayer away = sit(helper, chairAt(helper, table.offset(1, 0, -1), Direction.SOUTH));
        ServerPlayer other = sit(helper, chairAt(helper, table.offset(1, 0, 3), Direction.NORTH));
        start(helper, table);
        SeatId seat = cardsFor(helper, table, away);
        away.stopRiding();
        AwayFromBoard.vote(other, new AwayVotePayload(table, seat.index()));
        if (TableSeats.seatOf(helper.getLevel(), table, away.getUUID()).isEmpty()) {
            helper.fail("one vote at a game of two freed a kept seat");
            return;
        }
        helper.succeed();
    }

    private static void start(GameTestHelper helper, BlockPos table) {
        if (TableSessions.start(helper.getLevel(), table, TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            throw new GameTestAssertException("fixture: the game did not start");
        }
    }

    /** Puts a card on this player's board, which is what makes getting up leave something behind. */
    private static SeatId cardsFor(GameTestHelper helper, BlockPos table, ServerPlayer player) {
        var session = TableSessions.sessionAt(helper.getLevel(), table).orElseThrow();
        SeatId seat = TableSessions.seatIdOf(helper.getLevel(), table, player.getUUID()).orElseThrow();
        session.submit(new GameEvent.DeckLoaded(seat,
                List.of(CardIdentity.ofPrinting(new UUID(7L, seat.index()), false)), List.of()));
        return seat;
    }

    private static ServerPlayer sit(GameTestHelper helper, BlockPos chair) {
        ServerPlayer player = player(helper);
        Chairs.sit(player, chair, helper.getLevel().getBlockState(chair));
        return player;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static BlockPos chairAt(GameTestHelper helper, BlockPos where, Direction facing) {
        BlockState chair = GatheringContent.CHAIR.get().defaultBlockState().setValue(ChairBlock.FACING, facing);
        helper.getLevel().setBlock(where, chair, 3);
        return where;
    }
}
