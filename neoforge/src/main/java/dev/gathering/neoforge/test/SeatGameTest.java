package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Getting out of a chair.
 *
 * <p>Standing up lives on the board's own menu, because clicking the seat you are in used to
 * give it up and that swallowed the one click a seated player most wants to make during a
 * game. But the board only exists while a game does - so before a game, and after one, a
 * player could take a seat and have no way at all to leave it. Sitting down is the first
 * thing anybody does here, which made a dead end out of the very first flow.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeatGameTest {

    /** With no game running, a seat can be given up. */
    @GameTest(template = "empty")
    public static void aSeatCanBeLeftWhenThereIsNoGame(GameTestHelper helper) {
        BlockPos origin = table(helper);
        UUID player = new UUID(31L, 1L);
        SeatAnchor seat = TableClusters.at(helper.getLevel(), origin).seats().get(0);
        TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player);

        if (TableSeats.seatOf(helper.getLevel(), origin, player).isEmpty()) {
            helper.fail("taking a seat did not seat anybody");
            return;
        }
        if (!TableSeats.leave(helper.getLevel(), origin, player)) {
            helper.fail("a seat with no game running could not be given up");
            return;
        }
        if (TableSeats.seatOf(helper.getLevel(), origin, player).isPresent()) {
            helper.fail("leaving a seat left the player in it");
            return;
        }
        helper.succeed();
    }

    /**
     * A game ended leaves nobody stuck in their chair.
     *
     * <p>The reported deadlock: leave your seat mid-game and the game cannot be ended without
     * sitting back down, and ending it from the seat leaves you in the seat. Ending is a
     * command that only needs you to be looking at the table, and standing up now works
     * whenever there is no board to open - so neither half traps the other.
     */
    @GameTest(template = "empty")
    public static void endingAGameLeavesNobodyStuckInTheirChair(GameTestHelper helper) {
        BlockPos origin = table(helper);
        UUID player = new UUID(31L, 2L);
        SeatAnchor seat = TableClusters.at(helper.getLevel(), origin).seats().get(0);
        TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player);

        if (TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a solo game would not start");
            return;
        }
        // Ended the way the command ends it: by naming the table, not by holding a seat.
        TableSessions.end(helper.getLevel(), origin, new SeatId(0), "ended by a test");
        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("the game did not end");
            return;
        }
        // And now the chair lets go.
        if (!TableSeats.leave(helper.getLevel(), origin, player)) {
            helper.fail("after the game ended the seat could still not be given up");
            return;
        }
        helper.succeed();
    }

    /** Leaving a seat nobody is in is not an error, it is nothing. */
    @GameTest(template = "empty")
    public static void leavingASeatYouAreNotInDoesNothing(GameTestHelper helper) {
        BlockPos origin = table(helper);
        if (TableSeats.leave(helper.getLevel(), origin, new UUID(31L, 3L))) {
            helper.fail("somebody who was not sitting there gave up a seat");
            return;
        }
        helper.succeed();
    }

    /**
     * Somebody who sits down after the game has started is in the game, not beside it.
     *
     * <p>The reported bug, and the shape of it: a seat is claimed on the table block and a
     * seat is taken in the session, and only the first used to happen once a game was under
     * way. Everybody present at the start was seated into the session; anybody who walked up
     * afterwards got a chair the session never heard about, so their column read "(away)"
     * with them sitting in it and they could not act, because acting needs a seat to act as.
     */
    @GameTest(template = "empty")
    public static void sittingDownMidGameJoinsTheGame(GameTestHelper helper) {
        BlockPos origin = table(helper);
        ServerLevel level = helper.getLevel();
        UUID starter = new UUID(31L, 4L);
        List<SeatAnchor> seats = TableClusters.at(level, origin).seats();
        TableSeats.take(level, origin, seats.get(0).cell(), seats.get(0).side(), starter);

        if (TableSessions.start(level, origin, MatchRules.single(FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("the game would not start");
            return;
        }

        // A second player arrives after the game is already running. A real one, because the
        // reconcile reads the level for who is behind the claimed chair.
        ServerPlayer latecomer = helper.makeMockServerPlayerInLevel();
        SeatAnchor theirs = seats.get(1);
        if (TableSeats.take(level, origin, theirs.cell(), theirs.side(), latecomer.getUUID())
                != TableSeats.Claim.TAKEN) {
            helper.fail("the second player could not claim a chair");
            return;
        }
        TableSessions.seatingChanged(level, origin);

        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        if (session == null) {
            helper.fail("the game vanished");
            return;
        }
        PlayerRef sitting = session.state().seatState(new SeatId(1)).occupant();
        if (sitting == null) {
            helper.fail("a player who sat down mid-game is still (away) in the session");
            return;
        }
        if (!sitting.id().equals(latecomer.getUUID())) {
            helper.fail("seat 1 is held by somebody other than the player who took it");
            return;
        }

        // And standing back up empties it again, rather than leaving a ghost in the chair.
        TableSeats.leave(level, origin, latecomer.getUUID());
        TableSessions.seatingChanged(level, origin);
        if (session.state().seatState(new SeatId(1)).occupant() != null) {
            helper.fail("standing up left the player in the session's seat");
            return;
        }
        helper.succeed();
    }

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
