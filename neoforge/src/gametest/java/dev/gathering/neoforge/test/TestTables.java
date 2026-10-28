package dev.gathering.neoforge.test;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TablePart;
import dev.gathering.item.GatheringContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tables put down for the in-world tests, the one way they are put down.
 * <p>Eleven test classes each wrote this out for themselves, in six small variations of the same
 * four lines. One helper, as Create keeps its test helpers in one class, so a change to how a table
 * is built is made once.
 */
public final class TestTables {

    private TestTables() {
    }

    /** A wooden table with its north-west corner at a position relative to the test's structure. */
    public static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        return place(helper, GatheringContent.TABLE.get(), x, y, z);
    }

    /**
     * Which turn the draft pod at this table is on, as a drafter's screen would say it, or -1 for no pod.
     * <p>What a test sends with a pick, standing in for a client looking at the pack in front of it now.
     */
    public static long draftTurnAt(GameTestHelper helper, BlockPos origin) {
        return dev.gathering.block.DraftPods.podAt(helper.getLevel(), origin)
                .map(pod -> dev.gathering.core.draft.PickClock.turnOf(pod.state()))
                .orElse(-1L);
    }

    /** A table of any material, all four quarters, returning its corner in the world. */
    public static BlockPos place(GameTestHelper helper, Block material, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        BlockState table = material.defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
