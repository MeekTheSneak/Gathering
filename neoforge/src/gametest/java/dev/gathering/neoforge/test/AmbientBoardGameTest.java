package dev.gathering.neoforge.test;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.Gathering;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableBroadcast;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A table nobody is playing stops talking, and starts again the moment it has something to say.
 * <p>The public board went out every two seconds for as long as a session existed, changed or
 * not - per table, to everyone in range. Building one means walking every zone of every seat
 * through the visibility rules and serializing the result, once per person: measured at 0.397
 * ms and 874 KB for two spectators on a sixteen-hundred-card board, repeated for ever on a
 * game nobody was touching.
 * <p>Silence is the easy half. The half worth testing is that it is not silent when it matters:
 * a board that changes, and a player who has just walked up to one that has not.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmbientBoardGameTest {

    /** Long enough that the two-second timer fires several times over. */
    private static final int TICKS = 200;

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static TableBlockEntity entity(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    /** A seated game with something on the table to look at. */
    private static SeatId aGame(GameTestHelper helper, ServerPlayer player, BlockPos origin) {
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var seat = TableClusters.at(helper.getLevel(), origin).seats().getFirst();
        if (TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player.getUUID())
                != TableSeats.Claim.TAKEN) {
            throw new AssertionError("could not seat the player");
        }
        if (TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1)) != TableSessions.Outcome.STARTED) {
            throw new AssertionError("could not start the game");
        }
        SeatId me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID())
                .orElseThrow();
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        session.submit(new GameEvent.PaperCardCreated(me, me, PaperStock.BLANK, "something"));
        return me;
    }

    private static void tickTheTable(GameTestHelper helper, BlockPos origin, int howMany) {
        TableBlockEntity table = entity(helper, origin);
        for (int tick = 0; tick < howMany; tick++) {
            TableBlockEntity.serverTick(helper.getLevel(), origin,
                    helper.getLevel().getBlockState(origin), table);
        }
    }

    /**
     * A quiet table sends its board once and then stops.
     * <p>Once, not never: the first tick after a change still has something to say, and a table
     * that never spoke at all would be a table nobody standing near it could see.
     */
    @GameTest(template = "empty")
    public static void aquiettablestopstalking(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        aGame(helper, player, origin);

        // Whatever the first push costs, it happens once.
        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        int firstSpell = TableBroadcast.boardsSent();

        // And then nothing, because nothing has happened.
        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        int secondSpell = TableBroadcast.boardsSent();

        if (secondSpell != 0) {
            helper.fail("a table nobody touched sent " + secondSpell + " board(s) over "
                    + TICKS + " ticks; it should have said nothing after the first push"
                    + " (the first spell cost " + firstSpell + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * A board that changes is sent again.
     * <p>The half that makes the other half safe. Suppressing an unchanged board is only
     * correct if a changed one still goes out, and a test that only proved silence would pass
     * just as happily against a table that had stopped broadcasting altogether.
     */
    @GameTest(template = "empty")
    public static void achangedboardisstillsent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = aGame(helper, player, origin);

        tickTheTable(helper, origin, TICKS);
        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        if (TableBroadcast.boardsSent() != 0) {
            helper.fail("the fixture never went quiet, so this proves nothing");
            return;
        }

        // Somebody plays a card.
        TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow()
                .submit(new GameEvent.PaperCardCreated(me, me, PaperStock.BLANK, "and another"));

        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        if (TableBroadcast.boardsSent() == 0) {
            helper.fail("a board that changed was never sent to the room");
            return;
        }
        helper.succeed();
    }

    /**
     * Somebody who walks up to a quiet table is shown it.
     * <p>The case a revision check on its own gets wrong, and the commonest one there is: the
     * likeliest table to walk up to is a quiet one. Without the audience half, a new spectator
     * would stand in front of a game and be sent nothing until somebody moved a card.
     */
    @GameTest(template = "empty")
    public static void somebodywhowalksupisshownaquietboard(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        aGame(helper, player, origin);

        tickTheTable(helper, origin, TICKS);
        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        if (TableBroadcast.boardsSent() != 0) {
            helper.fail("the fixture never went quiet, so this proves nothing");
            return;
        }

        // A second player arrives and stands at the table without sitting down.
        ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
        watcher.setPos(origin.getX() + 1.5, origin.getY(), origin.getZ() + 1.5);

        TableBroadcast.forgetTheCount();
        tickTheTable(helper, origin, TICKS);
        if (TableBroadcast.boardsSent() == 0) {
            helper.fail("a player who walked up to a quiet table was sent nothing at all");
            return;
        }
        helper.succeed();
    }
}
