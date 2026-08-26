package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableReach;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Who is close enough to a table to be playing at it.
 *
 * <p>Every payload the table accepts names a position, and a position is any position - a
 * client puts whatever coordinates it likes in one. The check that the player is really there
 * is therefore the only thing between a table and somebody reaching it from across the world,
 * and until this existed it was written out eight times, in two different ways, and tested
 * nowhere at all.
 *
 * <p>What is checked here is the boundary and the two answers either side of it, because a
 * reach rule that is off by a block is a rule nobody notices is wrong.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableReachGameTest {

    @GameTest(template = "empty")
    public static void aPlayerStandingAtATableIsAtIt(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(table.getX() + 0.5, table.getY(), table.getZ() + 1.5);

        if (!TableReach.within(player, table)) {
            helper.fail("a player standing next to a table was not at it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPlayerAcrossTheWorldIsNot(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(table.getX() + 4000.0, table.getY(), table.getZ());

        // The failure this rules out is the whole reason the check exists: a client that
        // names a table it has never been near and gets an answer about somebody's library.
        if (TableReach.within(player, table)) {
            helper.fail("a player four thousand blocks away was counted as at the table");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theEdgeIsWhereItSaysItIs(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Just inside, measured from where the player is to the middle of the block - which
        // is the question, and not the block-to-block distance one caller used to use.
        player.setPos(table.getX() + 0.5, table.getY(), table.getZ() + 0.5 + TableReach.REACH - 0.1);
        if (!TableReach.within(player, table)) {
            helper.fail("a player just inside the reach was shut out");
        }

        player.setPos(table.getX() + 0.5, table.getY(), table.getZ() + 0.5 + TableReach.REACH + 0.1);
        if (TableReach.within(player, table)) {
            helper.fail("a player just outside the reach was let in");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anythingThatIsNotATableIsNotOne(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(at);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 1.5);

        // Empty air, at arm's length. Reaching a position is not the same as reaching a table,
        // and a handler that only checked the distance would run against nothing at all.
        if (TableReach.originFor(player, absolute).isPresent()) {
            helper.fail("a handful of air answered as a table");
        }

        BlockState table = GatheringContent.TABLE.get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(absolute, table);
        if (TableReach.originFor(player, absolute).isEmpty()) {
            helper.fail("a real table within reach did not answer as one");
        }
        helper.succeed();
    }
}
